# Verified acquisition entries

Rights evidence and file identity for the entries added to the verified acquisition catalogue after Sintel (ACQ-01, ACQ-02, ACQ-12, ACQ-13):

- Big Buck Bunny and Tears of Steel (issue #184, migration `V7__add_big_buck_bunny_and_tears_of_steel_entries.sql`), in the sections below.
- Nine more Blender open movies (issue #198, migrations `V8__add_spring_and_sprite_fright_entries.sql` and `V9__add_seven_more_blender_open_movie_entries.sql`), in [More Blender open movies](#more-blender-open-movies-198).

Sintel, the peer-to-peer test file, is documented in [p2p-test-file.md](p2p-test-file.md). The same rules apply here.

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
| Screenshot (2026-11-25, cropped to the page) | [`2026-11-25-peach-about.png`](evidence/2026-11-25-peach-about.png) | [`2026-11-25-mango-sharing.png`](evidence/2026-11-25-mango-sharing.png) |

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
| Audio | Two AAC (`mp4a`) LC stereo tracks (see below) | AAC (`mp4a`), LC profile |
| Duration | 634.2 s (10 min 34 s) | 734.1 s (12 min 14 s) |

The two audio tracks of Big Buck Bunny (streams 1 and 2) carry the same metadata: AAC LC, stereo, about 130 kb/s, language `eng`, both marked as default. Browsers play the first one, stream 1. The file is kept unchanged, so the second track stays in it, unused.

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

A download from HomeFlix only works while a peer has the whole file. Thomas's computer seeds both torrents in qBittorrent 5.2.3, from a copy of each video under the file name of the torrent. On 2026-11-25 at 00:34, both were at 100 % and seeding, created by `HomeFlix team`, with the infohashes above. Each one had sent the whole file once (247.3 MiB and 482.2 MiB, ratio 1.00), which is the test run below ([Big Buck Bunny](evidence/2026-11-25-qbittorrent-seeding-big-buck-bunny.png), [Tears of Steel](evidence/2026-11-25-qbittorrent-seeding-tears-of-steel.png), save path hidden). As for Sintel, a seeder on the same machine as the HomeFlix stack is not found through the tracker, because most home routers do not loop back to their own public address (see [p2p-run.md](../acquisition/p2p-run.md)).

**No backup seeder.** Thomas's computer is the only seeder of these two torrents. If it is off, a download of Big Buck Bunny or Tears of Steel stays at 0 %. Sintel, the film of the peer-to-peer demo, has a backup seeder ([p2p-test-file.md](p2p-test-file.md#seeding-plan-risk-r-21)); these two do not. This is noted in risk R-21.

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

This run does not show a transfer from another network. That is covered for Sintel by J10 ([p2p-run.md](../acquisition/p2p-run.md)). For these two torrents, a download from another network is part of the L10 rehearsal (#106).

## Evidence collected

- [x] Screenshots of <https://peach.blender.org/about/> and <https://mango.blender.org/sharing/> in [`evidence/`](evidence/)
- [x] Screenshot of both torrents seeding on a team machine (see Seeding)
- [x] A download of each entry from the catalogue, through the torrent, to a played title (test run above)

## More Blender open movies (#198)

Nine more open movies, added to give the demo a fuller catalogue (migrations `V8` and `V9`). They follow the same rules as the two films above: one single-file torrent per video, built for HomeFlix, and the file checked against its expected size and format.

### Rights

All nine are Blender open movies under a Creative Commons Attribution licence. Every version used (2.5, 3.0, 4.0) allows reproduction, adaptation and redistribution, also commercially, with attribution. That covers seeding, which redistributes the file (R-16), and the remux of five of them, which the attribution states (CC BY asks to indicate changes).

| Film | Year | Licence | Source page stored in the entry | Attribution line used |
|---|---|---|---|---|
| Spring | 2019 | CC BY 4.0 | <https://studio.blender.org/films/spring/> | (CC) Blender Foundation \| cloud.blender.org/spring |
| Sprite Fright | 2021 | CC BY 4.0 | <https://studio.blender.org/films/sprite-fright/> | (CC) Blender Foundation \| studio.blender.org |
| Caminandes 3: Llamigos | 2016 | CC BY 3.0 | [Commons file page](https://commons.wikimedia.org/wiki/File:Caminandes_3_-_Llamigos_-_Blender_Animated_Short.webm) | (CC) Blender Foundation \| studio.blender.org |
| Charge | 2022 | CC BY 4.0 | [Commons file page](https://commons.wikimedia.org/wiki/File:Charge_-_Blender_Open_Movie-full_movie.webm) | (CC) Blender Foundation \| studio.blender.org |
| Coffee Run | 2020 | CC BY 4.0 | [Commons file page](https://commons.wikimedia.org/wiki/File:Coffee_Run_-_Blender_Open_Movie-full_movie.webm) | (CC) Blender Foundation \| studio.blender.org |
| Cosmos Laundromat: First Cycle | 2015 | CC BY 4.0 | [Internet Archive item](https://archive.org/details/CosmosLaundromatFirstCycle) | (CC) Blender Foundation \| gooseberry.blender.org |
| Elephants Dream | 2006 | CC BY 2.5 | [Commons file page](https://commons.wikimedia.org/wiki/File:Elephants_Dream_(2006).webm) | (c) copyright 2006, Blender Foundation / Netherlands Media Art Institute / www.elephantsdream.org |
| Hero | 2018 | CC BY 4.0 | [Commons file page](https://commons.wikimedia.org/wiki/File:HERO_-_Blender_Open_Movie-full_movie.webm) | (CC) Blender Foundation \| studio.blender.org |
| Wing It! | 2023 | CC BY 4.0 | [Commons file page](https://commons.wikimedia.org/wiki/File:WING_IT!_-_Blender_Open_Movie-full_movie.webm) | (CC) Blender Foundation \| studio.blender.org |

Pages read on 2026-11-26. The Wikimedia Commons file pages show the licence in their *Licensing* section, with the author and the source in *Summary*. Blender Studio's own pages refuse automated reading, so they are to be checked and captured in a browser. Blender Studio's terms state that its content is under CC BY 4.0 unless noted otherwise.

Points to know:

- **Do not take the licence field of an Internet Archive item as evidence.** It is set by whoever uploaded the item. The Coffee Run item used here (`coffee-run`) and another Charge item (`charge_202601`) are marked CC BY-ND, and the description of the Spring item says CC-BY-ND while its licence field says CC BY 4.0. None of this matches the licence given by Blender, which is the one recorded.
- **Cosmos Laundromat:** the evidence is the Archive item, uploaded under the name Blender Foundation and marked CC BY 4.0. The copy on Wikimedia Commons, taken from YouTube, is marked CC BY-SA 3.0. Both allow the use; the Blender Studio page of the film is to be captured to settle the version.
- **Elephants Dream:** CC BY 2.5, as on Commons and on the project's site (orange.blender.org). The Archive item says CC BY 3.0 US. The file is only 640 × 360, although it is named `ed_hd.mp4`.
- **Not added:** Caminandes 1 and 2. No official page could be read to confirm their licence: caminandes.com no longer exists, Archive says CC BY 3.0 and Commons says CC BY-SA 3.0 for the second one. Glass Half is on Archive only as `.ogv`, and Agent 327 only under BY-ND uploads, so neither was added.

### Source files

Each video comes from an Internet Archive item. Six were downloaded through the item's torrent, with only the MP4 selected. Spring, Cosmos Laundromat and Elephants Dream did not complete through the torrent, so they were downloaded over HTTPS from the same item, and their size and SHA-1 were checked against the item's metadata.

| Film | Archive item | File in the item | Size in the item (bytes) |
|---|---|---|---|
| Spring | `springopenmovie` | `springopenmovie.mp4` | 90,190,341 |
| Sprite Fright | `sprite-fright` | `Sprite Fright - Open Movie by Blender Studio-804p.mp4` | 110,581,245 |
| Caminandes 3: Llamigos | `CaminandesLlamigos` | `Caminandes_ Llamigos-1080p.mp4` | 138,945,811 |
| Charge | `charge-blender-open-movie-1608p` | `Charge - Blender Open Movie 1608p.mp4` | 191,560,304 |
| Coffee Run | `coffee-run` | `Coffee Run.mp4` | 17,475,695 |
| Cosmos Laundromat | `CosmosLaundromatFirstCycle` | `Cosmos Laundromat - First Cycle (1080p).mp4` | 220,087,570 |
| Elephants Dream | `ElephantsDream` | `ed_hd.mp4` | 67,801,890 |
| Hero | `hero_20260106` | `hero.mp4` | 53,008,532 |
| Wing It! | `wing_it` | `wing_it.mp4` | 65,577,790 |

The item torrents are not used as they are: each one holds every file of the item (other formats, thumbnails, metadata), and the completed-file checks refuse a torrent of several files (J7).

### Files

All nine are H.264 and AAC LC. Five had their index at the end and were remuxed, with no re-encoding: `ffmpeg -i <source>.mp4 -map 0 -c copy -movflags +faststart <file>.mp4`. The four others already had the index first and are unchanged. After this, every file starts with `ftyp, moov`.

| File name in the torrent | Change | Size (bytes) | SHA-256 | Video | Duration |
|---|---|---|---|---|---|
| `spring.mp4` | remuxed | 90,190,400 | `eaf2b4a3317494f997a7bbade1729e535719c06e861534b5eb0d5f19ef56a4ab` | High, 1920 × 804 | 7 min 44 s |
| `sprite-fright.mp4` | none | 110,581,245 | `85af52d5f82976256d5f091aea4cf276a3d8599d14d36bae5d51e43a9acfd076` | High, 1920 × 804 | 10 min 30 s |
| `caminandes-3-llamigos.mp4` | none | 138,945,811 | `826513349ecf02d83b22f414514704e3bba10ff28fb45408a6cdf6cc57bacc06` | High, 1920 × 1080 | 2 min 30 s |
| `charge.mp4` | none | 191,560,304 | `4288be5a6737379b1c16b95b56708bd288c72097665797b2beff1c40ec5ba3e2` | High, 3840 × 1608 | 4 min 23 s |
| `coffee-run.mp4` | remuxed | 17,475,696 | `eed746e7f12a49543c1b6332d28a9a8c998032af84e3bc73a3dbb38f37ffeb48` | Main, 1280 × 536 | 3 min 5 s |
| `cosmos-laundromat.mp4` | remuxed | 220,087,074 | `8f8cd84cb42821345cf444cb506dde782c0fde5889aea5d63976bda0161b1794` | High, 1920 × 804 | 12 min 11 s |
| `elephants-dream.mp4` | none | 67,801,890 | `9766c83b135ef10df2d7f5b232058e93cef9199556b2cc44ca234b75e39b1e30` | Constrained Baseline, 640 × 360 | 10 min 54 s |
| `hero-2018.mp4` | remuxed | 53,008,533 | `9a3db0cf7461b85faabd3efa79b35960909427f77ef2b80a21db58c414a7d45a` | High, 2048 × 858 | 3 min 57 s |
| `wing-it.mp4` | remuxed | 65,577,791 | `f8aa5dc9ba3e0ada8a969f6a63edb85d51b6710e61ce9aaf9b96d89b5b1dd7f1` | High, 1920 × 1080 | 3 min 58 s |

As for Sintel, a remuxed file exists only in the team's torrent: remuxing the source again gives a file that plays the same but may not have the same bytes. Get these files through the team's torrents.

### Torrents built for HomeFlix

Each torrent holds one file, with the same settings as the two above: tracker `udp://tracker.opentrackr.org:1337/announce` plus DHT, not private, no web seed, `created by` `HomeFlix team`, the attribution of the film as `comment`, created on 2026-11-26. They were built with a short script that hashes the file in pieces (SHA-1) and writes the torrent. Run on `big-buck-bunny.mp4` with 512 KiB pieces, the same script gives the infohash of #184 (`7b28c6b6…f316c3`).

| File | Infohash (v1) | Piece length |
|---|---|---|
| `spring.mp4` | `df418f62342353102d8c74e3f66f65643bd60a47` | 256 KiB (345 pieces) |
| `sprite-fright.mp4` | `d219709e3db88021f22b26ba21ee5aeffcab3b54` | 256 KiB (422 pieces) |
| `caminandes-3-llamigos.mp4` | `13179b286c170a89d226f83724ced32bd40919d4` | 256 KiB (531 pieces) |
| `charge.mp4` | `5175c58aa5334f23bad19f3c79d4c493f255e597` | 512 KiB (366 pieces) |
| `coffee-run.mp4` | `909a2a7aa6560cc4de8bb8285a8b480ff12514da` | 256 KiB (67 pieces) |
| `cosmos-laundromat.mp4` | `e8b8a9f4b82bc070aafb9c50348c85719c35e5e7` | 512 KiB (420 pieces) |
| `elephants-dream.mp4` | `247ad6ae086248ee40e8bf537cbb5ae1b8c9bebf` | 256 KiB (259 pieces) |
| `hero-2018.mp4` | `11eb33acc6693933c7c7bc946a3b6c5e73e51a34` | 256 KiB (203 pieces) |
| `wing-it.mp4` | `3f6cc49e3fc6fc2fc0e7734590973a76ed3103fe` | 256 KiB (251 pieces) |

Each entry stores the magnet link `magnet:?xt=urn:btih:<infohash>&dn=<file>&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce` (see `V8` and `V9`). Hero's file is named `hero-2018.mp4` so that it does not clash with the Archive file `hero.mp4` in the seeder's folder.

### Seeding

Thomas's computer seeds the nine torrents in qBittorrent 5.2.3, from a copy of each video under the file name of the torrent. As for the other entries, the seeder is on the same machine as the stack and is added by hand as a peer of the stack's client (Docker Desktop host address), because the router does not loop back to its own public address.

**No backup seeder.** Thomas's computer is the only seeder of these nine torrents, as for Big Buck Bunny and Tears of Steel (risk R-21).

### Test run (2026-11-26)

All nine entries were downloaded from the catalogue on the Compose stack, branch of #198 (migrations `V8` and `V9` applied at start: "now at version v9"), on macOS with Docker Desktop. Before the run, the copies of these films that had been scanned into the library were removed, so that no film is listed twice.

| Film | Started | Completed and handed to the library |
|---|---|---|
| Spring | 10:25:35 | 10:38:50 |
| Sprite Fright | 10:25:40 | 10:38:50 |
| Charge | 10:49:19 | 10:50:44 |
| Elephants Dream | 10:49:27 | 10:50:44 |
| Cosmos Laundromat | 10:49:23 | 10:50:54 |
| Coffee Run | 10:49:30 | 10:50:54 |
| Hero | 10:49:36 | 10:50:54 |
| Wing It! | 10:49:59 | 10:50:54 |
| Caminandes 3: Llamigos | 10:49:15 | 10:51:44 |

- **Result:** each download is `COMPLETED` at 100 %, its file passed the checks, and its title is `AVAILABLE` in the catalogue, linked to its acquisition entry. The SHA-256 of each received file in `/downloads` is the value in the table above.
- **Playback:** the films were played from the catalogue (checked by Thomas).
- **First attempt:** Spring and Sprite Fright first stayed at 0 %. The seeder had their torrents saved in another folder than the videos, so it had nothing to send. Once the save folder was set and the torrents rechecked, the seeder was at 100 % and both downloads completed.

This run does not show a transfer from another network. That is covered for Sintel by J10 ([p2p-run.md](../acquisition/p2p-run.md)).

### Evidence collected

- [ ] Screenshots of the licence of each film, cropped to the page, in [`evidence/`](evidence/): the *Licensing* section of the Commons file page, the Archive item for Cosmos Laundromat, and the Blender Studio pages of Spring and Sprite Fright
- [ ] Screenshot of the nine torrents seeding on a team machine, save path hidden
- [x] A download of each entry from the catalogue, through the torrent, to a played title (test run above)
