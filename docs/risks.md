# Risk Register

This register lists what could go wrong with HomeFlix, how likely and how serious it is, and what we do about it. It covers the six topics required by the brief (**file access, identity, copyright, downloads, outages and backups**) and adds delivery and security risks that we met while building.

It is a living document. It is reviewed at the **day 5 integrated review** and the **day 9 acceptance run**, and the remaining items become the "residual risks" and "known limitations" of the final delivery.

## How to read it

- **Impact** and **Likelihood** are rated **High**, **Medium** or **Low**.
- **Status** says where the mitigation stands:

| Status | Meaning |
|---|---|
| **Done** | In place and checked |
| **In review** | Implemented in an open pull request |
| **Planned** | Decided or required, not built yet |
| **Open** | Not mitigated yet, needs a decision or work |

- **Owner** is the person responsible for making sure the risk is handled. Owners were set from each learner's area and confirmed by Clement on 2026-11-17. Benjamin and Thomas still need to acknowledge them at the next team sync.
- Mitigation references use the IDs of the requirements checklist (`docs/product/requirements.md`, for example `LIB-08`) and of the decision records (`docs/adr/`, for example `ADR-0014`).

## Required topics at a glance

| Topic required by the brief | Risks |
|---|---|
| File access | R-01 to R-05 |
| Identity | R-06 to R-13 |
| Copyright | R-14 to R-16 |
| Downloads | R-17 to R-22 |
| Outages | R-23 to R-26 |
| Backups | R-27 to R-30 |
| Delivery and other | R-31 to R-35 |

## Top risks to watch

These combine a high impact with a real chance of happening, or are directly graded:

1. **R-21** The real peer-to-peer download fails during the presentation (no seeders, restricted network).
2. **R-01** Path traversal exposes files outside the media folders.
3. **R-06** One user reads another's profiles or progress by changing an identifier.
4. **R-18** The torrent client's admin interface is reachable or has a default login.
5. **R-14** A demo file or poster is used without the right to reproduce or share it.
6. **R-31** HomeFlix cannot be installed on another machine by following the README.

---

## File access

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-01 | **Path traversal.** A crafted request makes the library service read a file outside the configured media folders. | High | Medium | Clients send media IDs, never file paths. The service resolves the real path and checks it is inside a configured root before opening it (LIB-08, PLY-04). Tests for forbidden paths (TST-03). | Planned | Clement |
| R-02 | **Root configured too broadly.** An administrator configures a folder that exposes more than intended. | Medium | Medium | The library service only sees what Docker mounts (`/media/library`, `/media/downloads`), and configured roots must be inside those. Other paths are rejected with a clear message (LIB-03). | Planned | Clement |
| R-03 | **HomeFlix modifies or deletes the source media.** | High | Low | The library is mounted **read-only** (checked with a failed write test). Removing a catalogue entry deletes only the database row (LIB-12). Deleting an account never touches media (SEC-07). | Done (mount), Planned (rest) | Clement |
| R-04 | **Symbolic links** inside a media folder point outside the allowed roots. | Medium | Low | Resolve links to their real path before the root check (same check as R-01). Add a test with a link pointing outside. | Planned | Clement |
| R-05 | **The network share becomes unavailable** while running, so titles show as available but fail, or the scan crashes. | Medium | Medium | Availability is recomputed on each scan and each file is marked available or unavailable (LIB-07). Playback shows an understandable message (PLY-05). | Planned | Clement |

## Identity

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-06 | **Broken access control.** A user reads or changes another user's profiles, favourites or progress by changing an ID in a URL or request. | High | Medium | The account and profile come from the authenticated token, not from request parameters. Ownership is checked on the server for every request (PRF-04, SEC-02). Tests for profile isolation (TST-02, TST-04). | In review | Benjamin |
| R-07 | **First-run takeover.** Someone else reaches the setup screen first and becomes the administrator. | High | Low | No default account exists (ACC-02). The setup screen is available only while no administrator exists. HomeFlix is not exposed to the Internet (ADR-0004). The README says to complete setup immediately. | Planned | Benjamin |
| R-08 | **Weak password storage.** Password hashes can be cracked if the database leaks. | High | Low | Use **BCrypt** (Spring Security `BCryptPasswordEncoder`, strength 10 to 12), never plain text (ACC-04). Decided 2026-11-17. | Planned | Benjamin |
| R-09 | **Token theft or request forgery.** XSS steals the session, or another site makes a request on the user's behalf. | High | Low | Token in an HttpOnly cookie with `SameSite=Strict`, same origin through the gateway (ADR-0009). Safe rendering of all text (R-33). Security review of XSS and CSRF risks (SEC-08). | Planned | Benjamin |
| R-10 | **A signed token stays valid** after the account is disabled or deleted. | Medium | Medium | Short-lived token (about 15 minutes) renewed through the identity service. Renewal fails for a disabled or deleted account, so access ends within that time. No revocation list, so services stay stateless (ADR-0009, ACC-10). Decided 2026-11-17. Tests for disabled accounts (TST-02). | In review | Benjamin |
| R-11 | **Secrets committed or leaked:** database passwords, the token signing key, the torrent client login. | High | Medium | Real `.env` files are git-ignored and `.env.example` contains no values (checked). Keys and passwords come from the environment. Reviewers check every PR for secrets (CONTRIBUTING). GitHub secret scanning and push protection are enabled in the repository settings (decided and checked 2026-11-17). | Done | Clement |
| R-12 | **A service can be called directly,** bypassing the gateway, and trusts the request. | High | Low | Every service verifies identity and permissions itself, not the gateway (ADR-0009, ADR-0013). Services publish no port to the host (checked). | Done (no ports), Planned (verification) | Benjamin |
| R-13 | **Personal data is over-collected or kept after deletion.** Deleting an account leaves favourites and progress in other services. | Medium | Medium | Collect only what is needed (SEC-06). Account deletion triggers deletion in every service (SEC-07), designed in the [cross-service rules](architecture/consistency-rules.md). | Planned | Benjamin |

## Copyright

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-14 | **A demo file or poster is used without the right to reproduce or share it.** | High | Low | Only personal videos, own creations or content with a licence that allows the use (ACQ-12). Source and licence evidence is kept for every demo file (DOC-06). Posters are supplied as links on an allowed host list (ADR-0018) and must be licensed for the use. The evidence for the peer-to-peer test file is in [docs/media/p2p-test-file.md](media/p2p-test-file.md) (J1). | Done (P2P test file), Planned (demo videos I2, posters) | Thomas |
| R-15 | **The rights warning is treated as permission,** or people use HomeFlix to fetch unauthorized works. | High | Low | Only entries of the team-verified catalogue can be downloaded. There is no free URL or magnet link field (ACQ-02, ACQ-09). A rights reminder is shown before the first download (ACQ-11). A copyright and peer-to-peer awareness note is provided (DOC-07). | Planned | Thomas |
| R-16 | **Seeding redistributes content** that we are not allowed to share. | High | Low | Only redistributable content is downloaded in the first place (ACQ-12). Seeding after completion is limited (ADR-0014): it stops at a share ratio of 1.0 or after 60 minutes, whichever comes first, set in `.env` and enforced at every start ([torrent client](acquisition/torrent-client.md), ADR-0023). | Done (limit) | Thomas |

## Downloads

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-17 | **Server-side request forgery.** A user-supplied URL makes the server reach internal services. | High | Low | No arbitrary URLs are accepted for downloads (ACQ-09, ACQ-10). The server never fetches poster URLs (ADR-0018). Any future remote fetch needs validation first. | Planned | Thomas |
| R-18 | **The torrent client's admin interface is reachable,** or still has its default login. | High | Medium | The container publishes no port and sits on a private network, reachable only by the acquisition service (checked, ADR-0014). The gateway has no route to it. The login comes from `.env` (variables listed in `.env.example` with no value, also read by the acquisition service), is written at every start, and has no localhost or subnet bypass; without it Compose refuses to start (ADR-0023). `qbittorrent/check-isolation.sh` checks it in CI: the old `admin` / `adminadmin` login and a wrong password are refused ([torrent client](acquisition/torrent-client.md)). | Done | Thomas |
| R-19 | **A malicious, corrupt or oversized file is downloaded.** | Medium | Low | The catalogue is small and verified. Each catalogue entry stores the expected size, which is compared after download, and the file must be a supported video format before it is indexed (decided 2026-11-17). BitTorrent already checks the content with the torrent's piece hashes. The downloads folder is a separate volume. HomeFlix never executes downloaded files and only serves supported video formats. | Planned | Thomas |
| R-20 | **Download folder permissions are too broad.** | Medium | Low | Only the torrent client can write to the downloads folder. The library service mounts it read-only (checked) (ACQ-06). | Done | Clement |
| R-21 | **The real peer-to-peer demo fails.** No seeders, a restricted school network, or the client cannot accept incoming peers. A simulation is not accepted by the brief. | High | Medium | Pick a small, well-seeded, authorized file. Test on the presentation network beforehand. Keep a backup plan, such as a seeder run by the team and a mobile hotspot. Record a successful run (ACQ-14). Publish only the BitTorrent peer port, never the web UI: done (ADR-0023, checked in CI). The test file, its main seeder and the evidence come with J1 (#74). A real transfer from a verified catalogue entry to a played title was run and recorded on 2026-11-24 on a home connection (J10, [p2p-run.md](acquisition/p2p-run.md)); the rehearsal on the presentation network (L10) is still to do. | Done (peer port, real transfer on a home connection), Open (rehearsal on the presentation network, cancel of a real transfer) | Thomas |
| R-22 | **Handoff between acquisition and library breaks.** A completed download is not indexed, or the entry stays "downloading". | Medium | Medium | The handoff is designed in the [cross-service rules](architecture/consistency-rules.md) with a timeout and a visible failed state (ACQ-04). A test covers the full path (TST-05). | Planned | Thomas |

## Outages

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-23 | **A service or the database is down,** and the user sees a blank page or a technical error. | High | Medium | Health checks and `restart: unless-stopped` are in place (checked). The gateway answers with a plain 503 message (open PR #30). Every screen has loading, error and empty states (UX-05, ARC-08). | In review | Clement |
| R-24 | **One service slows down or fails,** and the failure spreads to the services that call it. | Medium | Medium | Timeouts on every inter-service call, a defined retry policy and a common error format (issue #16, ADR-0008). Tests for a failing dependency (TST-05). | Planned | Clement |
| R-25 | **The gateway fails to start** because a service is not ready. | Low | Low | The gateway resolves service names at request time, so it starts and serves the app even if a service is down (checked, open PR #30). | In review | Clement |
| R-26 | **A disk fills up** (downloads folder or database volume). | Medium | Low | Downloads are limited to small, catalogued files. The documentation states the disk space needed (OPS-11). | Planned | Clement |

## Backups

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-27 | **Data loss.** The database volume is deleted (for example `docker compose down -v`) or the disk fails, losing accounts, catalogue and progress. | High | Medium | Named volumes keep data across normal restarts (checked). A backup and restore guide covers every database (OPS-09). The README warns that `down -v` deletes data. | Planned | Clement |
| R-28 | **The restore procedure does not work** when it is needed. | High | Low | Run a full restore once before delivery and include it in the acceptance run (TST-06). | Planned | Clement |
| R-29 | **Backups expose personal data or secrets** (password hashes, the torrent client's configuration). | Medium | Low | The guide says to store backups privately. The torrent client's config volume is documented as sensitive. | Planned | Clement |
| R-30 | **The database users are created only once.** Changing a database password in `.env` later breaks the services. | Medium | Medium | Document that the init script runs only on a fresh volume, and give a reset procedure (OPS-12). | Planned | Clement |

## Delivery and other

| ID | Risk | Impact | Likelihood | Mitigation | Status | Owner |
|---|---|---|---|---|---|---|
| R-31 | **HomeFlix cannot be installed on another machine** by following the README, because of an undocumented step. | High | Medium | A clean-clone install test on another machine (OPS-07). CI already builds all images on every PR. The README is written and tested only from a fresh clone. | Planned | Clement |
| R-32 | **Not enough time.** Scope grows, one learner is absent, or the integration is late. | High | Medium | Build the vertical slice first (PRC-05), review on day 5, keep optional features out (GEN-04), use the weekend buffer. | Planned | Team |
| R-33 | **User-controlled text is rendered unsafely** (cross-site scripting). Titles and descriptions come from file names, torrent names and administrators. | High | Low | React escapes text by default. Never insert raw HTML. Validate poster URLs and restrict image hosts with a Content-Security-Policy (SEC-03, SEC-12). Review before delivery (SEC-08). | Planned | Thomas |
| R-34 | **Images and dependencies change unexpectedly.** The torrent client image used `latest`, so a new release could break the setup. | Medium | Medium | Pin every image to an exact version tag, never `latest` (done 2026-11-17: every image in `compose.yaml` and the Dockerfiles uses an exact tag), and re-check the tags on delivery day. CI catches build breakage on every PR. | In review | Clement |
| R-35 | **The AI agent reads secrets, changes code or goes beyond its role.** | Medium | Low | The persona limits it to reading files, forbids secrets and edits, and it runs on a clean clone without secrets or push credentials. Every finding is reviewed by a learner. | Done (persona) | Benjamin |

---

## Review log

| Date | Reviewed by | Changes |
|---|---|---|
| 2026-11-17 | Clement Callejon | First version. |
| 2026-11-17 | Clement Callejon | Decisions: BCrypt (R-08), short-lived token without revocation (R-10), secret scanning (R-11), torrent login from `.env` (R-18), size and format check (R-19), peer port published (R-21), exact image tags (R-34), owners confirmed. |
| 2026-11-18 | Thomas Rousseau | Torrent client (J4, ADR-0023): login from `.env` (R-18 done), seeding limit (R-16 done for the limit), peer port published (R-21 partly done). |
| 2026-11-24 | Clement Callejon | Real transfer run and recorded (J10, #83): R-21 updated; setup gap (downloads folder to authorize before the first handoff) logged in [p2p-run.md](acquisition/p2p-run.md). |
