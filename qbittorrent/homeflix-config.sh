#!/usr/bin/with-contenv bash
# shellcheck shell=bash
# Run by the linuxserver.io image at every start, as root, after its default configuration is in place and
# before qBittorrent starts (/custom-cont-init.d). Applies the HomeFlix settings (configure_qbittorrent.py).
# If they cannot be applied, the web interface is switched off and the container stops.

if ! python3 /usr/local/share/homeflix/configure_qbittorrent.py; then
    lsiown abc:abc /config/qBittorrent/qBittorrent.conf
    echo "[homeflix] The web interface is switched off and the container stops: fix the setting above in .env, then start it again." >&2
    /run/s6/basedir/bin/halt
    exit 1
fi

# The file was written as root: give it back to the user qBittorrent runs as.
lsiown abc:abc /config/qBittorrent/qBittorrent.conf

# Files in progress are written to the incomplete downloads volume, which Docker creates owned by root.
lsiown abc:abc /incomplete
