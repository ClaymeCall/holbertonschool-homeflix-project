# Acceptance criteria for the 10 MVP scenarios

Measurable, testable criteria for each scenario of the brief. Covers DOC-01 and prepares the acceptance plan (TST-06). The scenario list and the requirements each one exercises are in [requirements.md](requirements.md#mvp-acceptance-scenarios).

## How to read this page

- Each criterion is written as **Given** (the starting state), **When** (the action) and **Then** (the observable result). A criterion passes only if the Then can be checked by someone else without interpretation.
- A criterion is checked **through the interface** unless it says otherwise (GEN-03). Where the server must refuse something, it is also checked directly against the API, with the interface bypassed (SEC-02).
- The notation `AS-04.2` means the second criterion of scenario AS-04. The acceptance plan (issue #91) refers to these IDs.
- Every scenario starts from a freshly started environment unless its Given says otherwise. The accounts used are the ones created in AS-01 and AS-02, following the demonstration procedure (OPS-12): no account is shipped, the administrator is created on first start.
- Test data: the demonstration videos listed with their source and licence (DOC-06). "A supported video" means MP4, H.264, AAC (ADR-0015).

## AS-01: Install from the repository

Requirements: OPS-01, OPS-07, OPS-03, ACC-01, ACC-02.

| ID | Given | When | Then |
|---|---|---|---|
| AS-01.1 | A machine with only the documented prerequisites, and a fresh clone of the repository. | The tester follows the README, copies `.env.example` to `.env`, fills in the values and runs `docker compose up --build`. | Every container reaches the healthy state, with no undocumented step and no manual SQL. |
| AS-01.2 | HomeFlix is running for the first time. | The tester opens the application address. | The first-run setup screen is shown. No account exists and no default password is accepted. |
| AS-01.3 | The first-run setup screen. | The tester creates the first administrator with a password they choose. | The account is created and the tester can sign in. The setup screen cannot be used a second time. |
| AS-01.4 | The repository and `.env.example`. | The tester searches them for secrets. | No usable secret, password or private key is found. |
| AS-01.5 | The README. | A person who did not write it follows it. | They reach a playable demonstration state without help (OPS-07). |

## AS-02: Configure a media library

Requirements: LIB-01 to LIB-07, LIB-11.

| ID | Given | When | Then |
|---|---|---|---|
| AS-02.1 | An administrator is signed in, and a mounted folder contains 3 supported videos. | The administrator configures the folder and starts indexing in the interface. | The 3 titles appear in the catalogue, each once. |
| AS-02.2 | The folder was already indexed (AS-02.1). | The administrator starts indexing again. | The catalogue still has exactly 3 titles. No duplicate is created. |
| AS-02.3 | The folder was indexed. | A video is added to the folder and indexing is started again. | The new title appears and the existing ones are unchanged. |
| AS-02.4 | The folder was indexed. | A video file is removed from the folder and indexing is started again. | The title stays in the catalogue, marked unavailable, with its metadata kept. |
| AS-02.5 | The folder contains a file in an unsupported format. | Indexing runs. | The file is not added to the catalogue, and the indexing result reports it. |
| AS-02.6 | An administrator enters a path that does not exist or is outside the authorized root. | The administrator saves it. | The interface shows an understandable message with no technical detail, and nothing is indexed. |
| AS-02.7 | A signed-in user who is not an administrator. | The user calls the configuration or indexing API directly. | The server answers 403 and nothing changes. |
| AS-02.8 | The running library container. | The tester tries to create a file in the media folder from inside the container. | The write is refused, because the mount is read-only. |

## AS-03: Search and play a movie

Requirements: CAT-04, PLY-02, PLY-03, PLY-04, CAT-03.

| ID | Given | When | Then |
|---|---|---|---|
| AS-03.1 | A signed-in user with a profile selected, and an indexed title called "Sample Film". | The user types part of the title in the search. | "Sample Film" is in the results, and a title that does not match is not. |
| AS-03.2 | The catalogue has titles in at least two genres. | The user filters by one genre. | Only titles of that genre are shown. |
| AS-03.3 | The user opens "Sample Film". | The page loads. | Title, description, poster (or the placeholder), genre, duration and availability are shown. |
| AS-03.4 | The movie page. | The user starts playback. | The video plays in the browser, with working play, pause, volume and fullscreen controls. |
| AS-03.5 | A video is playing. | The user seeks to the middle of the video. | Playback continues from that point without downloading the whole file first. |
| AS-03.6 | A stream URL. | A request with a `Range` header is sent. | The server answers `206 Partial Content` with a correct `Content-Range`. A request without a valid session is refused. |
| AS-03.7 | The movie page of a title with no poster. | The page loads. | The placeholder poster is shown. |

## AS-04: Pause and resume

Requirements: PLY-07, PLY-08, PLY-09, CAT-07.

| ID | Given | When | Then |
|---|---|---|---|
| AS-04.1 | A user plays a title for at least 30 seconds, then pauses. | The user leaves the player. | The position is saved for the active profile. |
| AS-04.2 | A position was saved (AS-04.1). | The user signs out, signs in again and selects the same profile. | The title appears in "continue watching", and playing it resumes within 10 seconds of the saved position. |
| AS-04.3 | A started title. | The user plays it to the end. | The title is shown as completed and leaves "continue watching". |
| AS-04.4 | A started title. | The user resets its progress. | The title is no longer started and playback begins at 0. |

## AS-05: Switch profiles

Requirements: PRF-01 to PRF-04, CAT-08.

| ID | Given | When | Then |
|---|---|---|---|
| AS-05.1 | An account with two profiles, A and B. | The user signs in. | The user must select a profile before reaching the catalogue. |
| AS-05.2 | Profile A is active. | The user adds "Sample Film" to My List and watches part of "Other Film". | Both appear for profile A. |
| AS-05.3 | Profile A has favourites and progress. | The user switches to profile B. | Profile B's My List and continue watching are empty, and A's data is not shown. |
| AS-05.4 | Profile B is active. | A request is sent for profile A's favourites or progress, by changing the profile identifier. | The server refuses it (403 or 404) and returns no data. |
| AS-05.5 | A second account exists. | Its user requests a profile identifier that belongs to the first account. | The server refuses it and returns no data. |

## AS-06: Acquire missing content

Requirements: ACQ-01 to ACQ-14.

| ID | Given | When | Then |
|---|---|---|---|
| AS-06.1 | An administrator is signed in. | The administrator opens the acquisition screen. | A separate catalogue lists only entries that each show a source and a licence. |
| AS-06.2 | The administrator has never started a download. | The administrator starts one. | A rights reminder is shown first, and the download starts only after it is acknowledged. |
| AS-06.3 | A download was started with a small authorized file. | The transfer runs on a real peer-to-peer connection. | The states queued, downloading and completed are visible in turn. This is a real transfer, not a simulation (ACQ-14). |
| AS-06.4 | A download is in progress. | The administrator cancels it. | The job is shown as cancelled and the partial file is not offered in the catalogue. |
| AS-06.5 | A download has completed. | Indexing runs, or is triggered by the handoff. | The title appears in the catalogue as available and plays. While the download runs it is shown as downloading. |
| AS-06.6 | A signed-in user who is not an administrator. | The user calls the acquisition API directly. | The server answers 403. The acquisition screens are not shown in the interface. |
| AS-06.7 | The running stack. | A user tries to reach qBittorrent's web interface through the gateway or from outside the Compose network. | It is unreachable. |
| AS-06.8 | The administrator submits an arbitrary URL or a source that is not an approved entry. | The request is sent. | The server refuses it. |

## AS-07: Attempt an unauthorized action

Requirements: ACC-06, SEC-02, PLY-04, ACQ-05, ACC-10.

| ID | Given | When | Then |
|---|---|---|---|
| AS-07.1 | No session. | A protected endpoint of each service is called directly. | Each answers 401, with the common error body and no technical detail. |
| AS-07.2 | A signed-in user who is not an administrator. | Every administrator endpoint is called with that user's session: account management, media folders, indexing, metadata editing, acquisition. | Each answers 403 and no data changes. |
| AS-07.3 | The same user, using the interface. | The user types an administrator address in the browser. | The interface does not show the administration area. |
| AS-07.4 | No session, or a user without permission. | A video stream URL is requested. | The server refuses it, and the video bytes are not returned. |
| AS-07.5 | A request for `../` or an absolute path outside the authorized roots. | The request reaches the library service. | It is refused and no file outside the roots is read. |
| AS-07.6 | A disabled account. | The user tries to sign in, or reuses a session issued before it was disabled. | Sign-in is refused, and protected resources are refused. |
| AS-07.7 | An expired or tampered token. | A protected endpoint is called. | The server answers 401. |

## AS-08: Restart the containers

Requirements: OPS-08, PLY-10, OPS-04.

| ID | Given | When | Then |
|---|---|---|---|
| AS-08.1 | A running stack with accounts, an indexed catalogue, favourites and saved progress. | The tester runs `docker compose restart`, then `docker compose down` followed by `docker compose up` (without `-v`). | Every container becomes healthy again. |
| AS-08.2 | The restart finished. | The user signs in. | The accounts, profiles, catalogue, favourites and progress are all still there. |
| AS-08.3 | The restart finished. | A title with saved progress is opened. | It resumes from its saved position. |
| AS-08.4 | The restart finished. | The tester inspects the media and downloads folders. | The source files are untouched. |

## AS-09: Make a file or service unavailable

Requirements: PLY-05, ARC-08, LIB-07, SEC-04.

| ID | Given | When | Then |
|---|---|---|---|
| AS-09.1 | An indexed title whose file is removed from the folder. | A user opens it and starts playback. | The interface says the video is unavailable, in plain language. No path, stack trace or class name is shown. |
| AS-09.2 | The same title after re-indexing. | The catalogue is shown. | The title is marked unavailable and its information is kept. |
| AS-09.3 | The acquisition service is stopped. | A user browses and plays an already indexed title. | Browsing and playback keep working. |
| AS-09.4 | The acquisition service is stopped. | An administrator opens the acquisition screen. | An understandable "service unavailable" message is shown, with no hanging screen. |
| AS-09.5 | The library service is stopped. | A user opens the catalogue. | An understandable error is shown, with a way to retry. |
| AS-09.6 | The identity service is stopped. | A user tries to sign in. | An understandable error is shown. |
| AS-09.7 | Any error shown in AS-09.1 to AS-09.6. | The body is inspected. | It follows the common error format and exposes no host, port, path, SQL or stack trace ([error model](../architecture/error-model.md)). |
| AS-09.8 | A video in an unsupported format is opened. | Playback is attempted. | The interface explains that the format is not supported. |

## AS-10: Review the AI agent

Requirements: AGT-01 to AGT-06.

| ID | Given | When | Then |
|---|---|---|---|
| AS-10.1 | The repository. | The reviewer looks for the agent's assignment. | The agent's role and limits are documented ([persona](../team/ai-agent-persona.md)). |
| AS-10.2 | The agent has been run on the project. | The reviewer opens its report. | A findings report exists in `docs/team/ai-agent-reports/`. |
| AS-10.3 | The report. | The reviewer reads each finding. | Each one records the learner's verification, a decision (accepted, rejected or deferred) and the reviewer's name. |
| AS-10.4 | An accepted finding. | The reviewer follows it. | The corresponding fix or a documented residual risk can be found in the repository. |

## Traceability

| Scenario | Criteria | Requirements covered |
|---|---|---|
| AS-01 | AS-01.1 to AS-01.5 | OPS-01, OPS-03, OPS-07, ACC-01, ACC-02 |
| AS-02 | AS-02.1 to AS-02.8 | LIB-01 to LIB-07, LIB-11 |
| AS-03 | AS-03.1 to AS-03.7 | CAT-03, CAT-04, CAT-05, PLY-02 to PLY-04 |
| AS-04 | AS-04.1 to AS-04.4 | PLY-07 to PLY-09, CAT-07 |
| AS-05 | AS-05.1 to AS-05.5 | PRF-01 to PRF-04, CAT-08 |
| AS-06 | AS-06.1 to AS-06.8 | ACQ-01 to ACQ-14 |
| AS-07 | AS-07.1 to AS-07.7 | ACC-06, ACC-10, SEC-02, PLY-04, ACQ-05 |
| AS-08 | AS-08.1 to AS-08.4 | OPS-04, OPS-08, PLY-10 |
| AS-09 | AS-09.1 to AS-09.8 | ARC-08, LIB-07, PLY-05, SEC-04 |
| AS-10 | AS-10.1 to AS-10.4 | AGT-01 to AGT-06 |
