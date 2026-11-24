# Real peer-to-peer transfer: recorded run (J10)

This page records the real transfer of the authorized test file (*Sintel*, see [the J1 evidence](../media/p2p-test-file.md)) from a verified catalogue entry to a playable title. It is the evidence for issue J10 (#83): ACQ-14, AS-06, PRS-03, risk R-21. A simulation does not count, so this run used the real torrent client, the real swarm and the real library.

## Summary

| | |
|---|---|
| Date | 2026-11-24 (Europe/Paris) |
| Start | 20:21:58 |
| Completed | 20:25:40, so **3 min 42 s** for 123.2 MiB |
| Network | Home broadband connection (residential, Wi-Fi), behind a router doing NAT. The host had a private address (192.168.1.0/24). The BitTorrent peer port (6881) is published on the host. Whether the router forwards it from the Internet was not checked. Not the presentation network (rehearsal: L10, #106). |
| Torrent | Infohash `4e626263b96d72a36b1b963f44188bf937004d5f`, one file, tracker `udp://tracker.opentrackr.org:1337/announce` plus DHT |
| Peer seen | One BitTorrent peer, **qBittorrent 5.2.3**, connected over µTP, public address in France (`89.156.52.xxx`, last digits left out), flags `D H P`, 100 % of the file (a seeder), about 600 KB/s |
| Result | **Success.** The file passed the checks, was handed to the library, indexed, and played in the player. |
| SHA-256 of the received file | `0abf1acde9c84a7a7225250fdc65dc744918ad114b1742f6e0dca81be6cbe6a4`, **identical** to the value recorded in J1 |

The demo torrent has no web seed, so no byte came over HTTP: this is a peer-to-peer transfer.

## What was run, step by step

Signed in as an administrator, starting from an empty library. To get a clean run, the library entry, the torrent and the file from an earlier run (earlier the same day, same file) were removed first. That earlier file had the same SHA-256.

| # | Step | How it was observed | Evidence |
|---|---|---|---|
| 1 | Library without the title | Home screen: "The catalogue is empty". `GET /api/library/media` returned `[]` after a rescan. | [1](evidence/2026-11-24-j10-1-library-before.png) |
| 2 | Verified catalogue entry | Administration, Downloads: *Sintel*, MP4, 129.2 MB, source Blender Foundation, licence CC BY 3.0, attribution text. | [2](evidence/2026-11-24-j10-2-catalogue-entry.png) |
| 3 | Rights reminder before the start | "Before you download" dialog (ACQ-11), naming the entry and its licence. | [3](evidence/2026-11-24-j10-3-rights-reminder.png) |
| 4 | Start | `POST /api/acquisition/downloads` with `rightsAcknowledged: true` answered `201`, state `QUEUED`. | |
| 5 | Status while downloading | `QUEUED`, then `DOWNLOADING` at 3 %, 8 %, 17 %…; the screen shows 31 % with a **Cancel** button. In the torrent client: 1 seed, 0 other peers, 600 KB/s. | [4](evidence/2026-11-24-j10-4-downloading.png) |
| 6 | Completion | State `COMPLETED`, 100 %, finished 20:25:40. The file was in `/downloads` with 129,184,608 bytes and the expected SHA-256. | [5](evidence/2026-11-24-j10-5-completed.png) |
| 7 | Indexing | The library log shows "Download of entry … is now in the library". `GET /api/library/media` returned *Sintel*, `AVAILABLE`. The handoff took about 15 ms after the checks passed. | [6](evidence/2026-11-24-j10-6-library-after.png) |
| 8 | Playback | Player at 2:51 of 14:48 with the controls. `Range: bytes=0-1023` and `bytes=64000000-64001023` through the gateway answered `206` with the right `Content-Range`; without a session `401`. | [7](evidence/2026-11-24-j10-7-playback.png) |

The Peers tab of the torrent client could not be captured as a screenshot during the transfer (the client's web interface is not published, by design, see [torrent-client.md](torrent-client.md)). The peer details above come from the client's API (`sync/torrentPeers`) at 20:22:47, during the transfer. **A screenshot of the Peers tab is still to be added to this page.**

## Access checks

Run the same day with the administrator account, a throwaway ordinary account and no session at all:

| Request | Administrator | Ordinary user | Not signed in |
|---|---|---|---|
| `GET /api/acquisition/entries` | 200 | **403** | 401 |
| `GET /api/acquisition/downloads` | 200 | **403** | 401 |
| `GET /api/acquisition/downloads/{id}` | 200 | **403** | |
| `POST /api/acquisition/downloads` (start) | 201 | **403** | **401** |
| `POST /api/acquisition/downloads/{id}/cancel` | | **403** | |
| `GET /api/library/media` | 200 | 200 | |

The torrent client has no route through the gateway: `/api/qbittorrent/` answers `404`, and `/qbittorrent/` returns the HomeFlix frontend page, not the client. The client's web port is not published on the host (`docker compose port qbittorrent 8080` prints nothing); only the BitTorrent peer port is. The throwaway account was disabled afterwards.

## What this run does not show

- **Cancel during a real transfer.** The administrator screen offers *Cancel* while a download runs (screenshot 4), and eight earlier downloads of the same entry ended as *Failed, This download was cancelled* earlier the same day (visible on screens 2 and 5). Those were started before the fix for #176, while the client could not write its files, so they stayed at 0 %. A cancel of a transfer that is actually moving data has **not** been recorded. It needs a second run, because a title already in the library cannot be downloaded again (`409 already_in_library`).
- **The presentation network.** This was a home connection. The rehearsal on the presentation network is L10 (#106).
- **A second seeder in the swarm.** Only one peer was seen. The backup seeder of the J1 evidence was not seen in this run.

## Issues found

1. **The downloads folder must be authorized by hand before the first handoff (setup gap).** On the first real run of the day, the library refused the completed file with `409 downloads_folder_not_authorized` ("/media/downloads is not an authorized folder. Add it in the administration area."), and the acquisition service retried every 10 seconds until an administrator added the folder in the *Library* tab. A clean install should behave the same way (not tested on one), so the first download after an installation looks stuck. This second run did not hit it because the folder was already authorized. To do: say it in the installation and configuration documentation (L1, L4 #100), and decide whether the folder should be authorized automatically.
2. **Playback logs an `ERROR` when the browser disconnects.** When the client stops reading, `StreamController` logs a `SocketTimeoutException` through `ApiExceptionHandler` as `ERROR` ("Unexpected error"). It is harmless for the user but it is noise in the logs.
3. **The Peers screenshot is missing** (see above).

## Risk R-21

See [risks.md](../risks.md). A real transfer of the authorized file now works end to end on a home connection. What remains for R-21 is the rehearsal on the presentation network and the backup plan (L10).
