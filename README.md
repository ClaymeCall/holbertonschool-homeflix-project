# HomeFlix

HomeFlix is a self-hosted video streaming platform for the home. It lets a household browse and watch videos stored on a computer, a personal server or a NAS, through a simple, welcoming web interface.

To install it and watch a first video, follow [Install and run](#install-and-run) below.

## The scenario: movie night at home

A household has a collection of videos on a NAS but no convenient way to watch them on the living-room laptop. HomeFlix connects to that library so that:

- an **administrator** creates an account, connects the media folder and starts indexing;
- **household members** sign in, choose their own profile, then find, play and resume a film;
- each profile keeps its **own favourites ("My List") and viewing progress**.

## What the MVP covers

- Account and role management, and viewing profiles
- Indexing of an authorized media folder, with no duplicates and with detection of missing files
- Catalogue navigation: recently added, categories, favourites, continue watching, search by title, filter by genre
- Movie pages with title, description, poster, genre, duration and availability
- Browser playback with seeking (HTTP Range requests) and saved progress per profile
- Authorized peer-to-peer acquisition of redistributable content, with a rights reminder

## What it does not cover (yet)

Universal transcoding, series, recommendations, parental controls, mobile or TV apps, remote Internet access, and support for every NAS or network protocol. See [docs/product/out-of-scope.md](docs/product/out-of-scope.md).

## Install and run

### What you need

- A computer or server that runs **Docker** with **Docker Compose v2** (the `docker compose` command, not the old `docker-compose`). Check with `docker compose version`.
- **Git**, and **OpenSSL** to create the signing key (it is already installed on most Linux and macOS systems).
- A folder of videos in the [supported format](docs/user/supported-video-format.md): MP4 with H.264 video and AAC audio. Other formats are not played, so convert them first.
- A modern web browser (Chrome, Firefox, Edge or Safari).

Nothing else is installed on your computer: Java, Node.js and PostgreSQL all run inside the containers.

### 1. Get the code

```bash
git clone https://github.com/ClaymeCall/holbertonschool-homeflix-project.git
cd holbertonschool-homeflix-project
```

### 2. Create the signing key

HomeFlix signs session tokens with an RSA key that you create once. Run:

```bash
mkdir -p secrets && chmod 700 secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
chmod 644 secrets/jwt-private.pem
```

The file is readable (`644`) because the service runs as a non-root user inside its container and must read it; the `700` folder keeps the other users of your computer out. Keep `secrets/jwt-private.pem` private: anyone who has it can sign in as any account. The `secrets/` folder is ignored by Git. If you create a new key later, everybody is signed out.

### 3. Create your folders

```bash
mkdir -p data/library data/downloads
```

- `data/library` is where your videos go. HomeFlix mounts it **read-only**, so it can never change or delete your files. To use a folder that already exists (a NAS share, for example), set `MEDIA_LIBRARY_PATH` to it in the next step.
- `data/downloads` is where completed peer-to-peer downloads are written. Create it yourself, as your own user, so that the torrent client can write in it.

### 4. Configure `.env`

```bash
cp .env.example .env
```

Open `.env` and fill in every empty value. Each variable is explained in the file. In short:

| Variable | What to put |
|---|---|
| `POSTGRES_PASSWORD`, `IDENTITY_DB_PASSWORD`, `LIBRARY_DB_PASSWORD`, `ACQUISITION_DB_PASSWORD` | A different random password for each |
| `IDENTITY_TO_LIBRARY_TOKEN`, `ACQUISITION_TO_LIBRARY_TOKEN` | A different random secret for each, at least 32 characters |
| `QBITTORRENT_USERNAME`, `QBITTORRENT_PASSWORD` | A login for the torrent client (password: at least 12 letters and digits). Nobody types it: only HomeFlix uses it |
| `PUID`, `PGID` | The output of `id -u` and `id -g`, so downloaded files belong to you |
| `MEDIA_LIBRARY_PATH`, `DOWNLOADS_PATH` | Leave the defaults if you created the folders of step 3 |
| `GATEWAY_PORT` | The port of the web address (8080 by default) |

Generate each random value with:

```bash
tr -dc a-f0-9 < /dev/urandom | head -c 32; echo
```

Use letters and digits only. Never commit `.env`: Git ignores it.

> **Choose the database passwords now.** The three service databases and their users are created **only the first time** the database volume is created, from the passwords in `.env` at that moment. If you change one of these passwords in `.env` later, the databases keep the old passwords and the services can no longer connect. See [Troubleshooting](#troubleshooting) to change a password afterwards.

### 5. Start HomeFlix

```bash
docker compose up --build
```

The first start builds every image (the web interface and the three Java services), which takes several minutes. Wait until the log stops scrolling, or run `docker compose ps` in a second terminal and check that every service says `healthy`. To run it in the background, add `-d`.

### 6. Open it and create the administrator

Open **http://localhost:8080** in your browser (replace `8080` with your `GATEWAY_PORT`; from another device, use the computer's address instead of `localhost`).

The first time, HomeFlix shows **Welcome to HomeFlix**. Enter an email address and a password of at least 12 characters, then confirm it. This creates the administrator account, and this page is only available once. HomeFlix then sends you to the sign-in page (you are not signed in automatically): sign in with this email address and password, then give your first profile a name.

### 7. Connect your videos and play one

1. Copy an MP4 video (H.264 and AAC) into `data/library`, or into the folder you set in `MEDIA_LIBRARY_PATH`.
2. In HomeFlix, open **Administration**, then **Library**.
3. Under **Media folders**, type `/media/library` in **Folder to add** (this is where your folder appears inside the container) and press **Add the folder**.
4. Press **Scan the folders**. Your videos appear in the list of titles. Scanning again never adds a video twice.
5. Go back to the home page, open your film and press play. You can seek anywhere, and HomeFlix resumes where you stopped the next time you open the title.

To let other people use HomeFlix, create their accounts in **Administration**, then **Accounts**. Each person chooses a profile with its own favourites and viewing progress.

### Getting videos by peer-to-peer

In **Administration**, then **Downloads**, you can download an authorized video from the verified catalogue. A rights reminder is shown first. Read the [copyright and peer-to-peer awareness note](docs/copyright-and-p2p.md) before your first download.

Before the first download, add `/media/downloads` as a media folder too (step 7.3). If you skip this, the download finishes but the library refuses the file, and the download looks stuck. To accept incoming connections from other peers, forward `QBITTORRENT_PEER_PORT` (6881 by default) on your router to this computer. Forward only this port, never `GATEWAY_PORT`: HomeFlix uses plain HTTP and is not meant to be reachable from the Internet. Downloads usually work without it, but slowly or not at all if there are few peers.

### Stop, restart and update

| What you want | Command | Your data |
|---|---|---|
| Stop, and start again later | `docker compose stop`, then `docker compose start` | Kept |
| Stop and remove the containers | `docker compose down`, then `docker compose up` | Kept |
| Restart one service | `docker compose restart library-service` | Kept |
| Rebuild after getting new code | `git pull`, then `docker compose up --build -d` | Kept |
| **Delete everything** | `docker compose down -v` | **Deleted** |

> **Warning: `docker compose down -v` deletes your data.** The `-v` option removes the volumes, which hold the accounts, profiles, catalogue, favourites, viewing progress and the torrent client's settings. Your video files in `MEDIA_LIBRARY_PATH` are never touched, but you would have to set HomeFlix up again from step 6. Use plain `docker compose down` to stop.

## Troubleshooting

Start with `docker compose ps` (is every service `healthy`?) and `docker compose logs <service>` for the one that is not. The services are `gateway`, `identity-service`, `library-service`, `acquisition-service`, `postgres` and `qbittorrent`.

| Problem | Likely cause and fix |
|---|---|
| `docker compose` stops with `Set ... in .env` | A variable of step 4 is empty or `.env` is missing. Fill in the variable it names. |
| `docker compose` complains about a secret file or key | The key of step 2 is missing, or `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` do not point to it. Check that `secrets/jwt-private.pem` and `secrets/jwt-public.pem` exist. |
| `port is already allocated` or `address already in use` | Another program uses port 8080 (or 6881). Change `GATEWAY_PORT` (or `QBITTORRENT_PEER_PORT`) in `.env` and start again. |
| The page does not load right after `up` | The services are still starting. Wait a minute and check `docker compose ps`. |
| The page says a part of HomeFlix is temporarily unavailable | One service or the database is down or slow. Run `docker compose ps`, then `docker compose logs` for the service that is not `healthy`, and `docker compose restart` it. |
| A service restarts again and again, with a database authentication error | The database password in `.env` no longer matches the one the database was created with (see below). |
| You were signed out after changing the key | A new key signs everyone out. Sign in again: accounts and data are not affected. |
| I forgot a password | This version has no password reset. An administrator can create a new account for that person in **Administration**, then **Accounts**. If the only administrator's password is lost, the only way back to the welcome page is to delete the data (`docker compose down -v`). |
| The library is empty after the scan | The folder you added must be inside `/media/library` (not the path on your computer). Check that the videos are in `MEDIA_LIBRARY_PATH`, then scan again. The scan report lists folders that could not be read. |
| A video is listed but does not play, or is not listed | Only MP4 with H.264 and AAC plays. See [how to convert](docs/user/supported-video-format.md). The scan report lists the videos that were not added. |
| A title is shown as unavailable | Its file was moved or deleted, or the folder is not mounted. Put the file back and scan again: the title returns with its favourites and progress. |
| A download stays stuck after it reached 100 % | `/media/downloads` is not added as a media folder. Add it in **Administration**, then **Library**. HomeFlix offers the file to the library again every few seconds, so the download finishes by itself, but it fails after 5 minutes (`ACQUISITION_HANDOFF_TIMEOUT`). If it failed, start it again. |
| A download does not start moving | The torrent has few or no peers, or your router blocks incoming connections. Wait, or forward `QBITTORRENT_PEER_PORT` on your router. |
| Downloads fail with a permission error | `DOWNLOADS_PATH` is not writable by `PUID` and `PGID`. Create the folder yourself as your user, and set `PUID` and `PGID` to `id -u` and `id -g`. |

### Change a database password after the first start

The databases are created once, so editing `.env` is not enough. To change a password, set it in the database first and in `.env` after. For the library, for example:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "ALTER ROLE homeflix_library PASSWORD '"'"'the-new-password'"'"'"'
```

Then put the same value in `LIBRARY_DB_PASSWORD` and run `docker compose up -d`. The roles are `homeflix_identity`, `homeflix_library` and `homeflix_acquisition`. If you have no data to keep, `docker compose down -v` followed by `docker compose up --build` starts again from a clean database with the new passwords.

## Architecture in one paragraph

HomeFlix is split into at least three Java/Spring Boot business microservices (identity and profiles, library and playback, acquisition), each running in its own container and owning its own data, behind a React frontend. Everything starts with Docker Compose. Details are in [docs/architecture/tech-stack.md](docs/architecture/tech-stack.md).

## Documentation

| Topic | File |
|---|---|
| Team and working agreement | [docs/team/working-agreement.md](docs/team/working-agreement.md) |
| Mandatory requirements checklist | [docs/product/requirements.md](docs/product/requirements.md) |
| Acceptance criteria for the 10 MVP scenarios | [docs/product/acceptance-criteria.md](docs/product/acceptance-criteria.md) |
| Out of scope and future extensions | [docs/product/out-of-scope.md](docs/product/out-of-scope.md) |
| Architecture overview (diagram, boundaries) | [docs/architecture/overview.md](docs/architecture/overview.md) |
| Technology stack | [docs/architecture/tech-stack.md](docs/architecture/tech-stack.md) |
| Supported video format and conversion | [docs/user/supported-video-format.md](docs/user/supported-video-format.md) |
| Torrent client: isolation and settings | [docs/acquisition/torrent-client.md](docs/acquisition/torrent-client.md) |
| Architecture decision records | [docs/adr/](docs/adr/README.md) |
| Risk register | [docs/risks.md](docs/risks.md) |
| API contracts (OpenAPI) | [docs/api/](docs/api/README.md) |
| Data model (entities per service) | [docs/architecture/data-model.md](docs/architecture/data-model.md) |
| Error model and outage behavior | [docs/architecture/error-model.md](docs/architecture/error-model.md) |
| Gateway checks (Range, blocked paths, 503) | [gateway/tests/README.md](gateway/tests/README.md) |
| User journeys | [docs/product/user-journeys.md](docs/product/user-journeys.md) |
| Prioritized backlog | [docs/product/backlog.md](docs/product/backlog.md) |
| Identity and permissions design | [docs/architecture/identity-and-permissions.md](docs/architecture/identity-and-permissions.md) |
| Cross-service consistency rules | [docs/architecture/consistency-rules.md](docs/architecture/consistency-rules.md) |
| Demo: one service calls another | [docs/architecture/service-to-service-demo.md](docs/architecture/service-to-service-demo.md) |
| Copyright and peer-to-peer awareness | [docs/copyright-and-p2p.md](docs/copyright-and-p2p.md) |
| Peer-to-peer test file: rights evidence | [docs/media/p2p-test-file.md](docs/media/p2p-test-file.md) |
| Verified acquisition entries: Big Buck Bunny and Tears of Steel | [docs/media/acquisition-entries.md](docs/media/acquisition-entries.md) |
| Real peer-to-peer transfer: recorded run (J10) | [docs/acquisition/p2p-run.md](docs/acquisition/p2p-run.md) |
| Contributing (branches, commits, pull requests) | [CONTRIBUTING.md](CONTRIBUTING.md) |

## Rights and peer-to-peer

Peer-to-peer is only a transfer method. It does not grant permission to download or share a work. HomeFlix is only meant to be used with personal videos, your own creations, or content whose licence allows the intended use. Read the [copyright and peer-to-peer awareness note](docs/copyright-and-p2p.md) before the first download.

## Team

Built by [ClaymeCall](https://github.com/ClaymeCall), [Benji3106](https://github.com/Benji3106) and [tomsonne](https://github.com/tomsonne) as a Holberton School project.
