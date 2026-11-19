"""Writes the HomeFlix settings into qBittorrent's configuration file before the client starts.

Run at every start of the container (see homeflix-config.sh), so the settings that protect the client
cannot drift: an edit made through the web interface is replaced at the next start.

- The web interface login comes from QBITTORRENT_USERNAME and QBITTORRENT_PASSWORD (R-18). The password
  is stored as qBittorrent stores it, a PBKDF2-SHA512 hash, never in clear.
- The web interface only answers requests addressed to "qbittorrent" (the Compose service name used by the
  acquisition service) or "localhost" (the health check), and never skips the login.
- Seeding stops after QBITTORRENT_MAX_RATIO or QBITTORRENT_MAX_SEEDING_MINUTES, whichever comes first (R-16).
- Incomplete files stay in /incomplete, which only this container mounts. /downloads only receives
  completed files, so the library never sees a partial download (ACQ-06, R-19).

When a setting is missing or invalid, the web interface is switched off in the configuration file, so the
client is unreachable while the container stops, and the script exits with status 1 and a message.
The password is never printed.
"""

import base64
import configparser
import hashlib
import hmac
import os
import secrets
import sys

CONFIG_FILE = "/config/qBittorrent/qBittorrent.conf"

# qBittorrent's own password format (Utils::Password::PBKDF2).
PBKDF2_ITERATIONS = 100_000
PBKDF2_SALT_BYTES = 16
PBKDF2_KEY_BYTES = 64

MIN_PASSWORD_LENGTH = 12

# Keys of the linuxserver.io default file that older qBittorrent versions used. qBittorrent migrates them
# at its first start and would then overwrite the values written below, so they are removed.
LEGACY_KEYS = {
    "Preferences": [
        "Downloads\\SavePath",
        "Downloads\\TempPath",
        "Connection\\UPnP",
        "Connection\\PortRangeMin",
        "WebUI\\Address",
        "WebUI\\ServerDomains",
    ],
}


def read_config():
    # Qt's format: case-sensitive keys with backslashes, "key=value" without spaces, no interpolation.
    config = configparser.ConfigParser(interpolation=None, delimiters=("=",), strict=False)
    config.optionxform = str
    config.read(CONFIG_FILE, encoding="utf-8")
    return config


def write_config(config):
    os.makedirs(os.path.dirname(CONFIG_FILE), exist_ok=True)
    with open(CONFIG_FILE, "w", encoding="utf-8") as file:
        config.write(file, space_around_delimiters=False)


def put(config, section, values):
    if not config.has_section(section):
        config.add_section(section)
    for key, value in values.items():
        config.set(section, key, value)


def fail(message):
    """Switches the web interface off, so nothing answers on it, then stops with the message."""
    print(f"[homeflix] {message}", file=sys.stderr)
    config = read_config()
    put(config, "WebUI", {"Enabled": "false"})
    write_config(config)
    sys.exit(1)


def env_text(name):
    value = os.environ.get(name, "").strip()
    if not value:
        fail(f"{name} is not set. Set it in .env (see .env.example).")
    return value


def env_number(name, default, cast, minimum):
    raw = os.environ.get(name, "").strip() or default
    try:
        value = cast(raw)
    except ValueError:
        fail(f"{name} must be a number, not {raw!r}.")
    if value < minimum:
        fail(f"{name} must be at least {minimum}.")
    return value


def pbkdf2(password, salt):
    return hashlib.pbkdf2_hmac("sha512", password.encode("utf-8"), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BYTES)


def hash_password(password):
    salt = secrets.token_bytes(PBKDF2_SALT_BYTES)
    encoded = f"{base64.b64encode(salt).decode()}:{base64.b64encode(pbkdf2(password, salt)).decode()}"
    return f'"@ByteArray({encoded})"'


def matches(stored, password):
    """True when the stored hash is already the hash of this password, so it is kept as it is."""
    try:
        inner = stored.strip('"').removeprefix("@ByteArray(").removesuffix(")")
        salt, expected = (base64.b64decode(part) for part in inner.split(":"))
    except ValueError:
        return False
    return hmac.compare_digest(pbkdf2(password, salt), expected)


def main():
    username = env_text("QBITTORRENT_USERNAME")
    password = env_text("QBITTORRENT_PASSWORD")
    if len(password) < MIN_PASSWORD_LENGTH:
        fail(f"QBITTORRENT_PASSWORD must be at least {MIN_PASSWORD_LENGTH} characters long.")
    peer_port = env_number("TORRENTING_PORT", "6881", int, 1024)
    if peer_port > 65535:
        fail("TORRENTING_PORT must be a port number, at most 65535.")
    max_ratio = env_number("QBITTORRENT_MAX_RATIO", "1.0", float, 0.0)
    max_minutes = env_number("QBITTORRENT_MAX_SEEDING_MINUTES", "60", int, 0)

    config = read_config()

    for section, keys in LEGACY_KEYS.items():
        for key in keys:
            if config.has_section(section):
                config.remove_option(section, key)

    stored_hash = config.get("WebUI", "Password_PBKDF2", fallback="")
    put(config, "WebUI", {
        "Enabled": "true",
        "Username": username,
        "Password_PBKDF2": stored_hash if matches(stored_hash, password) else hash_password(password),
        "Address": "*",
        "Port": os.environ.get("WEBUI_PORT", "8080"),
        "LocalHostAuth": "true",
        "AuthSubnetWhitelistEnabled": "false",
        "HostHeaderValidation": "true",
        # Quoted: in Qt's format an unquoted ";" starts a comment and the rest would be lost.
        "ServerDomains": '"qbittorrent;localhost"',
        "CSRFProtection": "true",
        "ClickjackingProtection": "true",
        "UseUPnP": "false",
    })
    put(config, "BitTorrent", {
        "Session\\Port": str(peer_port),
        "Session\\DefaultSavePath": "/downloads/",
        "Session\\TempPathEnabled": "true",
        "Session\\TempPath": "/incomplete/",
        "Session\\AddExtensionToIncompleteFiles": "true",
        "Session\\GlobalMaxRatio": f"{max_ratio:g}",
        "Session\\GlobalMaxSeedingMinutes": str(max_minutes),
    })
    # Inside a container, UPnP cannot open the router's port: the peer port is published by Compose instead.
    put(config, "Network", {"PortForwardingEnabled": "false"})
    put(config, "LegalNotice", {"Accepted": "true"})

    write_config(config)

    print(f"[homeflix] qBittorrent configured: web login for {username!r}, peer port {peer_port}, "
          f"seeding stops at ratio {max_ratio:g} or after {max_minutes} minutes.")


if __name__ == "__main__":
    main()
