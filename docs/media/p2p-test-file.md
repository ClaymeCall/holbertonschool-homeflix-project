# Peer-to-peer test file: rights evidence and identity

This page records the file chosen for the real peer-to-peer demonstration (ACQ-12, ACQ-14, DOC-06), why we may download **and** redistribute it, and how to recognize it. It is the evidence for issue J1 (#74). The video itself is not committed: it travels by peer-to-peer.

## Summary

| | |
|---|---|
| Work | *Sintel* (2010), open movie by the Blender Foundation (Durian Open Movie project) |
| Rights holder | Blender Foundation |
| Licence | Creative Commons Attribution 3.0 Unported (CC BY 3.0), <https://creativecommons.org/licenses/by/3.0/> |
| Reproduction allowed | Yes ("copy and redistribute the material in any medium or format") |
| Redistribution allowed | Yes, same clause. Needed because the client shares pieces while and after downloading |
| Conditions | Attribution, link to the licence, indicate changes for a derivative, no endorsement implied, full credits scroll when redistributing the film itself |
| Excluded from the licence | Logos and trademarks (Blender, Durian, Creative Commons logos). HomeFlix does not use them |
| File chosen | `sintel.mp4`, remuxed from the source file (see below), 129,184,608 bytes |
| Torrent for the demo | Single file, infohash `4e626263b96d72a36b1b963f44188bf937004d5f`, created by the team |
| Licence pages read on | 2026-11-17, screenshots taken on 2026-11-18 |
| Demo file and torrent inspected on | 2026-11-18 |

## Licence evidence

Official pages read on 2026-11-17:

- **<https://durian.blender.org/sharing/>** (official site of the Durian project):
  > "The results of the Durian Open Movie project are being licensed under the Creative Commons Attribution 3.0 license."
  >
  > "This includes all the data we've published online and on the DVDs, and all of the contents on this website."
  >
  > "In short, this means you can freely reuse and distribute this content, also commercially, for as long you provide a proper attribution."
  >
  > "If you redistribute or screen or broadcast the movie itself: include the entire credits scroll."
  >
  > "In all other cases, attribute our work as: © copyright Blender Foundation | durian.blender.org"
  >
  > "Excluded from the Creative Commons is: all logos on this website (including Blender logo, Durian logo, Creative Commons logo) and associated trademarks."
- **<https://durian.blender.org/about/>**:
  > "“Sintel” is licensed as Creative Commons Attribution 3.0. That means you can share and show the movie freely, for as long you include the credit scroll of the film itself."
- **<https://studio.blender.org/films/sintel/>**: official film page of Blender Studio. It does not state the licence itself; the licence comes from the two pages above.
- **<https://creativecommons.org/licenses/by/3.0/>** (licence deed): allows to *Share* (copy and redistribute in any medium or format, for any purpose) and *Adapt*, under *Attribution* (credit, link to the licence, indicate if changes were made) and *No additional restrictions*.

Screenshots of these pages, taken on 2026-11-18, are kept in [`evidence/`](evidence/):

- [`2026-11-18-durian-sharing.jpg`](evidence/2026-11-18-durian-sharing.jpg): <https://durian.blender.org/sharing/>
- [`2026-11-18-durian-about.jpg`](evidence/2026-11-18-durian-about.jpg): <https://durian.blender.org/about/>
- [`2026-11-18-cc-by-3.0-deed-top.jpg`](evidence/2026-11-18-cc-by-3.0-deed-top.jpg) and [`2026-11-18-cc-by-3.0-deed-terms.jpg`](evidence/2026-11-18-cc-by-3.0-deed-terms.jpg): <https://creativecommons.org/licenses/by/3.0/>

## Attribution

Required text, used by HomeFlix:

> *Sintel* © copyright Blender Foundation | durian.blender.org. Licensed under CC BY 3.0 (https://creativecommons.org/licenses/by/3.0/). Remuxed to MP4 `faststart` for HomeFlix; video and audio streams unchanged, full credits scroll kept.

Where it appears:

- **In the video**: the full credits scroll of *Sintel* is part of the video stream. The remux copies the streams unchanged, so the credits are kept.
- **In this page**, in the repository documentation.
- **In the HomeFlix catalogue entry** (J3, #76): source and licence of the entry, shown to administrators.
- **In the `.torrent` file's `comment` field**: this is metadata of the torrent file only. It is not written into the video and does not replace the two places above.

## Source file and source torrent

| | Value |
|---|---|
| Torrent | Multi-file torrent named `Sintel`, `created by` field `WebTorrent/0008`, creation date 2023-01-10 |
| Infohash (v1) | `5318660c4b1ae6cce06e11a5a630f51dd67c2bd1` |
| Where the `.torrent` was obtained | uTorrent "licensed samples" page, <https://www.utorrent.com/intl/fr/licensed-samples/> |
| Files | 11: `Sintel.mp4`, 9 subtitle files (`.srt`), `poster.jpg`, 129,302,391 bytes in total (123.3 MiB) |
| Video file | `Sintel.mp4`, 129,241,752 bytes |
| SHA-256 of the video | `1dc6f2ca9762dfcc7d1b1843129a3e4f351d1fe935dea2241c7b359c11ebc1d8` |
| Container | MP4, atoms in the order `ftyp, free, mdat, moov`: index at the end, **not** `faststart` |
| Web seed | `https://d2rkvwn9o31ezi.cloudfront.net/` (HTTP) |

The qBittorrent screenshot taken during this download shows **this source torrent** (123.3 MiB, all 11 files) completed and seeding, with 0 connected sources out of 3 known. It does not prove anything about the torrent built for the demo.

Why the source file is not used as is:

- its index is at the end of the file, but ADR-0015 requires `faststart`;
- it holds 11 files, while the acquisition flow expects one video per catalogue entry (expected size in J3, format check in J7);
- with few connected peers, data may have come mostly from the HTTP web seed, which would not demonstrate a real peer-to-peer transfer (ACQ-14).

## Change made: remux only

The video and audio streams are copied without re-encoding; only the container is rewritten with the index first. Subtitles and the poster are left out.

The first remux (2026-11-17, SHA-256 `4c079a8b…`) was lost before it was seeded. It was redone on 2026-11-18 with the same command, using FFmpeg 6.1.1. The streams and the size are the same, but the bytes written by FFmpeg differ, so the SHA-256 and the infohash below changed. The values below are those of the 2026-11-18 file and torrent.

```bash
ffmpeg -i Sintel.mp4 -map 0:v:0 -map 0:a:0 -c copy -movflags +faststart -map_metadata 0 sintel.mp4
```

## File chosen for the demo

| | Value |
|---|---|
| File name | `sintel.mp4` |
| Size | 129,184,608 bytes (123.2 MiB) |
| SHA-256 | `0abf1acde9c84a7a7225250fdc65dc744918ad114b1742f6e0dca81be6cbe6a4` |
| Container | MP4, atoms in the order `ftyp, moov, free, mdat`: index first (`faststart`) |
| Video | H.264, High profile |
| Audio | AAC, LC profile |
| Duration | 888.064 s (14 min 48 s) |

## Torrent built for the demo

| | Value |
|---|---|
| Infohash (v1) | `4e626263b96d72a36b1b963f44188bf937004d5f` |
| Files | 1 (`sintel.mp4`, 129,184,608 bytes) |
| Piece length | 256 KiB (493 pieces) |
| Tracker | `udp://tracker.opentrackr.org:1337/announce`, plus DHT |
| Private | No (DHT and peer exchange allowed) |
| `created by` | `HomeFlix team` |
| `comment` | Attribution of *Sintel* (CC BY 3.0) |
| Created on | 2026-11-18 |

Magnet link (for the verified catalogue, J3):

```text
magnet:?xt=urn:btih:4e626263b96d72a36b1b963f44188bf937004d5f&dn=sintel.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce
```

The `.torrent` file is shared within the team. It is not committed.

## How these values were obtained

All values in the tables above come from commands run on the files themselves, not from the screenshots: on 2026-11-17 for the source file and the source torrent, on 2026-11-18 for the demo file and the demo torrent.

| Value | How |
|---|---|
| Codecs, profiles, duration | `ffprobe -v error -show_entries stream=codec_name,profile:format=duration -of default=nw=1 <file>` |
| Order of MP4 atoms | Reading the top-level box headers of the file (size and type of each box) |
| SHA-256 | `shasum -a 256 <file>` |
| Sizes | `stat` on the files; sum of the file lengths listed in the source torrent |
| Infohashes, file lists, trackers, web seed | Parsing the bencoded `.torrent` files and hashing their `info` dictionary with SHA-1 |

To check a copy of the demo file:

```bash
shasum -a 256 sintel.mp4
ffprobe -v error -show_entries stream=codec_name,profile -of default=nw=1 sintel.mp4
```

## Seeding plan (risk R-21)

The main seeder is in place. The backup seeder and the network test are **not done yet**:

- **Main seeder**: Thomas's computer, seeding the demo torrent in qBittorrent 5.2.3 since 2026-11-18: `sintel.mp4`, 123.2 MiB, checked at 100 %, status *Seeding* ([screenshot](evidence/2026-11-18-qbittorrent-main-seeder.png)). The *General* tab shows info hash v1 `4e626263b96d72a36b1b963f44188bf937004d5f`, created by `HomeFlix team` ([screenshot](evidence/2026-11-18-qbittorrent-infohash.png)).
- **Backup seeder**: a second team computer seeding the same torrent. Not set up yet.
- **Network test**: download the demo torrent from another machine or another network (for example a mobile hotspot) and record the result. Started on 2026-11-18 at 18:33 by a teammate on another machine, over Wi-Fi on another network than the main seeder: the *General* tab shows info hash v1 `4e626263b96d72a36b1b963f44188bf937004d5f`, created by `HomeFlix team`, 1 seed connected and 30.7 KiB/s download speed ([screenshot](evidence/2026-11-18-qbittorrent-backup-downloading.png), save path blurred). The end of the download is not recorded yet. Rehearsal on the presentation network is in L10 (#106).

## Evidence still to collect

- [x] URL where the source `Sintel.torrent` was obtained
- [x] Saved copy of <https://durian.blender.org/sharing/> and <https://durian.blender.org/about/>: screenshots of 2026-11-18 in [`evidence/`](evidence/)
- [x] Demo torrent added to qBittorrent and seeding on the main seeder: [screenshot of 2026-11-18](evidence/2026-11-18-qbittorrent-main-seeder.png)
- [ ] Download of the demo torrent from another machine and another network (for example a mobile hotspot), through the torrent and not by copying the file, with date, network and result:
  - [x] during the download, at least one connected peer and a download speed above 0: [`2026-11-18-qbittorrent-backup-downloading.png`](evidence/2026-11-18-qbittorrent-backup-downloading.png) (*General* tab, 1 seed, 30.7 KiB/s)
  - `…-backup-infohash.png`: *General* tab after the download, info hash v1 `4e626263b96d72a36b1b963f44188bf937004d5f` and *Downloaded* 123.2 MiB (a copied file would show 0)
  - optional: `shasum -a 256 sintel.mp4` on that machine, giving `0abf1acde9c84a7a7225250fdc65dc744918ad114b1742f6e0dca81be6cbe6a4`
- [ ] Backup seeder: the same machine keeps the torrent seeding afterwards, `…-backup-seeder.png` showing `sintel.mp4` at 100 % with status *Seeding*

## Checklist (issue J1)

- [x] Source, licence and authorization evidence stored in the repository: quotes, links and the source URL are on this page, screenshots of the licence pages in [`evidence/`](evidence/)
- [x] Reproduction and redistribution rights explicitly verified (CC BY 3.0, quotes above)
- [ ] File is small, well seeded and in the supported format: small and supported format are checked (see above); **well seeded is not tested yet**
- [x] No arbitrary or unauthorized source integrated and no DRM circumvention (ACQ-13)
