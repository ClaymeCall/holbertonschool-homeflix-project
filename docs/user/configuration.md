# Configuration

This page describes everything you can configure in HomeFlix, where the data lives, how the databases are prepared and how to create the first administrator safely. For the step-by-step installation, see the [README](../../README.md#install-and-run).

HomeFlix is configured in one file, `.env`, at the root of the project. Copy [`.env.example`](../../.env.example) to `.env` and fill in the empty values. `.env` holds secrets and is ignored by Git: never commit it, and never paste its values into an issue or a pull request. This page never shows a real value.

After you change `.env`, apply it with `docker compose up -d`. Compose recreates only the containers whose settings changed.

## Variables

**Required** means HomeFlix does not start while the variable is empty: `docker compose` stops with `Set ... in .env`.

### Database

| Variable | Required | Default | What it is |
|---|---|---|---|
| `POSTGRES_USER` | No | `homeflix_admin` | The administrator of the PostgreSQL server. Only used to create the three service databases the first time. No HomeFlix service connects with it. |
| `POSTGRES_PASSWORD` | Yes | none | Password of that administrator. |
| `IDENTITY_DB_PASSWORD` | Yes | none | Password of the identity service's own database user. |
| `LIBRARY_DB_PASSWORD` | Yes | none | Password of the library service's own database user. |
| `ACQUISITION_DB_PASSWORD` | Yes | none | Password of the acquisition service's own database user. |

Use a different random password for each, with letters and digits only. Each service has its own database and user, and cannot connect to another service's database. **The databases and their users are created only the first time the database volume is created**, so changing these passwords in `.env` later does not change them in PostgreSQL. See [Database setup and migrations](#database-setup-and-migrations).

### Session key

| Variable | Required | Default | What it is |
|---|---|---|---|
| `JWT_PRIVATE_KEY_PATH` | Yes | `./secrets/jwt-private.pem` in `.env.example` | The file of the RSA private key that signs session tokens. Only the identity service receives it. |
| `JWT_PUBLIC_KEY_PATH` | Yes | `./secrets/jwt-public.pem` in `.env.example` | The matching public key. The library and acquisition services use it to check tokens, and cannot sign any. It is not secret, but it must match the private key. |

Create the pair once, with the commands in `.env.example` and the [README](../../README.md#2-create-the-signing-key). Keep the private key private, because anyone who has it can sign in as any account. A new key signs everyone out, and changes nothing else.

### Calls between services

| Variable | Required | Default | What it is |
|---|---|---|---|
| `IDENTITY_TO_LIBRARY_TOKEN` | Yes | none | The secret the identity service sends to the library, for example to delete a profile's favourites and progress. Use the same recipe as below: 32 characters. |
| `ACQUISITION_TO_LIBRARY_TOKEN` | Yes | none | The secret the acquisition service sends to the library to hand over a completed download. At least 32 characters. |

Each service has its own secret and can only use its own calls. The two values must differ (ADR-0022). Compose gives the same value to both sides, so you only set each once. Generate each with `tr -dc a-f0-9 < /dev/urandom | head -c 32; echo`. These calls are never reachable from a browser.

### Media and downloads

| Variable | Required | Default | What it is |
|---|---|---|---|
| `MEDIA_LIBRARY_PATH` | No | `./data/library` | The folder on your computer that holds your videos. It appears as `/media/library` inside HomeFlix, **read-only**. |
| `DOWNLOADS_PATH` | No | `./data/downloads` | The folder on your computer where completed downloads are written. It appears as `/media/downloads` in the library (read-only) and as `/downloads` in the torrent client (the only place that writes). |
| `ACQUISITION_HANDOFF_TIMEOUT` | No | `5m` | How long a downloaded and checked file may wait for the library to accept it. After that the download fails with `handoff_failed`. The file is kept. Written like `30s`, `5m`, `1h`. |
| `POSTER_ALLOWED_HOSTS` | No | `upload.wikimedia.org` | The host names a poster image may come from, comma separated, exact names, https only. The library refuses any other poster address, and the gateway lets the browser load images only from these hosts. An invalid entry stops the gateway and the library from starting. |

The two folders must exist before you start and should be separate folders. Create them yourself so that they belong to your user.

### Access

| Variable | Required | Default | What it is |
|---|---|---|---|
| `GATEWAY_PORT` | No | `8080` | The port of the web address, `http://localhost:8080` by default. It is the only HomeFlix port other than the peer port that is published on your computer. |

### Torrent client

| Variable | Required | Default | What it is |
|---|---|---|---|
| `QBITTORRENT_USERNAME` | Yes | none | Login of the torrent client's web interface and API. Only the acquisition service uses it: nobody types it, because the interface is never published. |
| `QBITTORRENT_PASSWORD` | Yes | none | Its password, at least 12 characters, letters and digits. Applied again at every start, so change it here, then run `docker compose up -d qbittorrent`. |
| `QBITTORRENT_PEER_PORT` | No | `6881` | The BitTorrent port, published on your computer (TCP and UDP) so other peers can connect. To receive incoming connections from the Internet, forward this port on your router to this computer. |
| `QBITTORRENT_MAX_RATIO` | No | `1.0` | Seeding stops when the share ratio (uploaded divided by downloaded) reaches this number. |
| `QBITTORRENT_MAX_SEEDING_MINUTES` | No | `60` | Or when it has seeded for this many minutes, whichever comes first. The torrent is stopped, not deleted. |
| `PUID`, `PGID` | No | `1000` | The user and group that own downloaded files. Use the output of `id -u` and `id -g`. |
| `TZ` | No | `Europe/Paris` | The time zone of the torrent client. |

The torrent client's settings come from `.env` at every start: a change made in its own web interface is replaced at the next start. See [torrent-client.md](../acquisition/torrent-client.md) for the isolation rules.

## Data and volumes

HomeFlix keeps its data in three named Docker volumes and in the two folders you choose. Docker names the volumes with the project name, so on your computer they appear as `homeflix_postgres-data` and so on (`docker volume ls`).

| Where | What it holds | Deleted by `docker compose down`? | Deleted by `docker compose down -v`? |
|---|---|---|---|
| Volume `postgres-data` | The three databases: accounts, profiles, catalogue, favourites, viewing progress and downloads | No | **Yes** |
| Volume `qbittorrent-config` | The torrent client's settings | No | **Yes** |
| Volume `qbittorrent-incomplete` | Downloads in progress (partial files). Only the torrent client mounts it, so a partial file is never indexed | No | **Yes** |
| `MEDIA_LIBRARY_PATH` folder | Your videos | No | No |
| `DOWNLOADS_PATH` folder | Completed downloads | No | No |

`docker compose down -v` **deletes all the data of HomeFlix** (the first three rows). Your video files are never deleted by HomeFlix. Use plain `docker compose down` to stop.

### Which container sees which folder

| Folder | Library service | Acquisition service | Torrent client |
|---|---|---|---|
| `MEDIA_LIBRARY_PATH` | `/media/library`, **read-only** | no | no |
| `DOWNLOADS_PATH` | `/media/downloads`, **read-only** | `/downloads`, **read-only** | `/downloads`, read and write |

- **The library is read-only.** HomeFlix reads your videos and never writes to them. Removing a media folder in the administration area never deletes a file.
- **Only the torrent client writes downloads.** The other containers cannot change them.
- **The downloads folder must be authorized in the library.** A completed download is only accepted if `/media/downloads` has been added as a media folder (**Administration**, **Library**, **Folder to add**). Until it is, the download reaches 100 %, the library refuses the file, HomeFlix retries every few seconds, and the download fails with `handoff_failed` after `ACQUISITION_HANDOFF_TIMEOUT`. Add the folder once, before your first download.
- **Authorized folders must be inside a mounted one.** In the administration area you can only add `/media/library`, `/media/downloads` or a folder inside them.
- **Permissions.** The torrent client writes as `PUID` and `PGID`. If `DOWNLOADS_PATH` is not writable by that user, downloads fail with a permission error. The library container must be able to read your files: if a title reports a permission problem, check the permissions of the folder (see [file access](../library/file-access.md)).

## Database setup and migrations

Two different things prepare the databases.

**1. Creating the databases (once).** The first time the `postgres-data` volume is empty, PostgreSQL runs the script `db/init/01-create-databases.sh`. It creates one database and one user per service, with the passwords from `.env` at that moment, and removes everyone else's access to each database. **It never runs again** while the volume exists. So:

- Changing a database password in `.env` after the first start breaks the matching service, which keeps trying the old password in the database. Change the password in PostgreSQL first. The [README](../../README.md#change-a-database-password-after-the-first-start) shows how.
- Adding a service later does not create its database in an existing volume.

**2. Creating and updating the tables (at every start).** Each service runs its own [Flyway](https://flywaydb.org/) migrations when it starts, against its own database (ADR-0007). On a new installation, the tables are created. After you update HomeFlix (`git pull` and `docker compose up --build -d`), any new migration is applied automatically, in order, and the ones already applied are skipped. There is nothing to run by hand.

If a migration fails, the service does not become healthy. `docker compose ps` shows it as unhealthy and `docker compose logs <service>` shows the reason. Back up your data before an update.

## First administrator

HomeFlix has **no default account and no default password**. The first administrator is created from the welcome page, the first time you open HomeFlix.

**Do it immediately after the first start.** The welcome page is open to **whoever reaches it first**: until an administrator exists, anyone who can open `http://localhost:8080` (or your computer's address) can create it and take control of your HomeFlix. The server checks this, and creates at most one administrator even if two people try at the same moment, but it cannot know who should be the owner.

1. Start HomeFlix (`docker compose up --build`) and wait until `docker compose ps` shows every service as `healthy`.
2. **Right away**, open `http://localhost:8080`. If you changed `GATEWAY_PORT`, use that port.
3. On **Welcome to HomeFlix**, enter your email address, a password of at least 12 characters, and confirm it. Use a password you do not use anywhere else, and keep it: this version has no password reset.
4. Sign in and name your first profile.
5. Check that it is closed: open the address again in a private window. You should be asked to sign in, and the welcome page no longer lets anyone create an account. The server answers `409 setup_already_completed` to any second attempt.

While you have not done this:

- Do not start HomeFlix on a computer that other people can reach, such as a network you do not trust.
- HomeFlix is meant to run on your home network. Do not publish its port to the Internet. Remote access from the Internet is not part of this version (see [out of scope](../product/out-of-scope.md)).

Then, in **Administration**, **Accounts**, create an account for each person in the household. You choose the password, then give it to that person. The last administrator can never be disabled, deleted or demoted, so there is always one.
