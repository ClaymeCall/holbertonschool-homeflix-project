# Verified acquisition entries: Big Buck Bunny and Tears of Steel

Rights evidence and file identity for the two entries added to the verified acquisition catalogue after Sintel (issue #184, migration `V7__add_big_buck_bunny_and_tears_of_steel_entries.sql`; ACQ-01, ACQ-02, ACQ-12, ACQ-13). Sintel, the peer-to-peer test file, is documented in [p2p-test-file.md](p2p-test-file.md). The same rules apply here.

No media file is committed. The `.torrent` files are shared within the team.

## Rights

Both films are open movies by the Blender Foundation, released under the Creative Commons Attribution 3.0 licence (<https://creativecommons.org/licenses/by/3.0/>). The licence allows reproduction and redistribution, also commercially, with attribution. That covers seeding, which redistributes the file (R-16).

| | Big Buck Bunny | Tears of Steel |
|---|---|---|
| Year | 2008 | 2012 |
| Licence page | <https://peach.blender.org/about/> | <https://mango.blender.org/sharing/> (also <https://mango.blender.org/about/>) |
| Licence as written | "licensed under the Creative Commons Attribution 3.0 license" | "licensed under the Creative Commons Attribution 3.0 license" |
| Condition | Proper attribution. For screening the whole film: "Include the entire credits roll in ending." | Proper attribution. The about page: free to share and show "for as long you keep the credit scroll included." |
| Attribution line given by the page | "(c) copyright 2008, Blender Foundation / www.bigbuckbunny.org" | "(CC) Blender Foundation \| mango.blender.org" |
| Not covered | Site logos and trademarks, DVD cover and disc print | Logos and trademarks (Blender, Mango, Creative Commons, sponsors), material not from the project |
| Pages read | 2026-11-24 | 2026-11-24 |

Attribution stored in the entries:

> Big Buck Bunny (c) copyright 2008, Blender Foundation / www.bigbuckbunny.org. Licensed under CC BY 3.0. File unchanged, full credits roll kept.

> Tears of Steel (CC) Blender Foundation | mango.blender.org. Licensed under CC BY 3.0. File unchanged, full credits scroll kept.

## Source torrents

The videos were obtained through the torrents published by uTorrent's "licensed samples" page (<https://www.utorrent.com/intl/fr/licensed-samples/>), which states CC BY 3.0 and names the Blender Foundation. The same films are also listed on <https://webtorrent.io/free-torrents>, with other infohashes.

| | Big Buck Bunny | Tears of Steel |
|---|---|---|
| `.torrent` | <https://d2rkvwn9o31ezi.cloudfront.net/Big%20Buck%20Bunny.torrent> | <https://d2rkvwn9o31ezi.cloudfront.net/Tears%20of%20Steel.torrent> |
| Infohash (v1) | `6bd15b7938f3d16e7511eabe54eb81fb19e491f4` | `8784b99e852522ebca424f961d99f3664dc5e62c` |
| `created by` | `WebTorrent/0008` | `WebTorrent/0008` |
| Files | `Big Buck Bunny.mp4`, `poster.jpg` | `Tears of Steel.mp4`, 8 subtitle files (`.srt`), `poster.jpg` |

These torrents are not used as they are: they hold several files, and the completed-file checks refuse a torrent of several files (J7). The video of each one was kept unchanged and published in a single-file torrent of its own (below).

## Files

Neither video was remuxed: both are already in the supported format (ADR-0015), with the index first.

| | Big Buck Bunny | Tears of Steel |
|---|---|---|
| File name in the torrent | `big-buck-bunny.mp4` | `tears-of-steel.mp4` |
| Size | 259,392,822 bytes (247.4 MiB) | 505,533,124 bytes (482.1 MiB) |
| SHA-256 | `0bb49419d00d9405beea040f29586b6fdf650b375cb74713e3de3be1538dce77` | `0d38c5a60e42d83e7587f495273fc7771fbf9b534322f1aa9bfe5cc89455c6fd` |
| Container | MP4, boxes `ftyp, moov, wide, mdat` (`faststart`) | MP4, boxes `ftyp, moov, mdat` (`faststart`) |
| Video | H.264 (`avc1`), High profile | H.264 (`avc1`), Main profile |
| Audio | Two AAC (`mp4a`) LC tracks | AAC (`mp4a`), LC profile |
| Duration | 634.2 s (10 min 34 s) | 734.1 s (12 min 14 s) |

Checked with:

```bash
shasum -a 256 big-buck-bunny.mp4 tears-of-steel.mp4
ffprobe -v error -show_entries stream=codec_type,codec_tag_string,profile:format=duration -of compact big-buck-bunny.mp4
```

## Torrents built for HomeFlix

| | Big Buck Bunny | Tears of Steel |
|---|---|---|
| Infohash (v1) | `7b28c6b6cfc3eaf6b546fb2256c6d9c883f316c3` | `e6d8867d826b6406746e067e8b08d582cbb9dee8` |
| Files | 1 (`big-buck-bunny.mp4`) | 1 (`tears-of-steel.mp4`) |
| Piece length | 512 KiB (495 pieces) | 1 MiB (483 pieces) |
| Tracker | `udp://tracker.opentrackr.org:1337/announce`, plus DHT | same |
| Private | No (DHT and peer exchange allowed) | No |
| Web seed | None | None |
| `created by` | `HomeFlix team` | `HomeFlix team` |
| `comment` | Attribution of the film (CC BY 3.0) | Attribution of the film (CC BY 3.0) |
| Created on | 2026-11-24 | 2026-11-24 |

Magnet links (stored in the entries):

```text
magnet:?xt=urn:btih:7b28c6b6cfc3eaf6b546fb2256c6d9c883f316c3&dn=big-buck-bunny.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce
magnet:?xt=urn:btih:e6d8867d826b6406746e067e8b08d582cbb9dee8&dn=tears-of-steel.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce
```

## Seeding

A download from HomeFlix only works while a peer has the whole file. Thomas's computer seeds both torrents in qBittorrent, from a copy of each video under the file name of the torrent. As for Sintel, a seeder on the same machine as the HomeFlix stack is not found through the tracker, because most home routers do not loop back to their own public address (see [p2p-run.md](../acquisition/p2p-run.md)).

## Test run (2026-11-25)

Both entries were downloaded from the catalogue on the Compose stack, branch of #184 at `d4add9c`, on macOS with Docker Desktop.

| | Big Buck Bunny | Tears of Steel |
|---|---|---|
| Started (Administration > Downloads) | 00:13:36 | 00:13:49 |
| Completed and handed to the library | 00:15:58 | 00:15:28 |
| Received file | `/downloads/big-buck-bunny.mp4` | `/downloads/tears-of-steel.mp4` |
| SHA-256 of the received file | `0bb49419…8dce77`, the value above | `0d38c5a6…55c6fd`, the value above |
| Download state | `COMPLETED`, 100 % | `COMPLETED`, 100 % |
| Library entry | `AVAILABLE`, linked to its acquisition entry | `AVAILABLE`, linked to its acquisition entry |
| Playback | Played from the catalogue (checked by Thomas) | Played from the catalogue (checked by Thomas) |

- **Peer:** the seeder was Thomas's desktop qBittorrent 5.2.3, on the same computer as the stack. It was added by hand as a peer of the stack's client (`torrents/addPeers`, Docker Desktop host address), because the router does not loop back to its own public address. The client's peer list showed it as `qBittorrent/5.2.3`. The data came through the BitTorrent protocol, and the torrents have no web seed.
- **Migration:** at start, Flyway applied `V7` to the acquisition database ("now at version v7"). The catalogue then listed Big Buck Bunny, Sintel and Tears of Steel.
- **First attempt:** the seeder first had the torrents at 0 %, because its copies of the videos had been removed from its save folder. Once they were back and rechecked, the seeder was at 100 % and both downloads completed.

This run does not show a transfer from another network. That is covered for Sintel by J10 ([p2p-run.md](../acquisition/p2p-run.md)).

## Evidence still to collect

- [ ] Screenshots of <https://peach.blender.org/about/> and <https://mango.blender.org/sharing/> in [`evidence/`](evidence/)
- [ ] Screenshot of both torrents seeding on a team machine
- [x] A download of each entry from the catalogue, through the torrent, to a played title (test run above)
