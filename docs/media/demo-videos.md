# Demonstration videos

The videos used to show browsing and playback in the demo, with where their rights evidence is kept (issue I2 #64; DOC-06, ACQ-12, PRS-06; risk R-14). Only content whose licence allows the use is shown. No media file is committed (CONTRIBUTING): each video is obtained again and checked against the SHA-256 below.

## The videos

All three are open movies by the Blender Foundation, under the Creative Commons Attribution 3.0 licence (<https://creativecommons.org/licenses/by/3.0/>). The licence allows copying, showing and sharing, also commercially, as long as the attribution is given. Each film keeps its full credits, which is what the licence pages ask for when the whole film is shown.

| Film | File | Size | SHA-256 | Format | Rights evidence |
|---|---|---|---|---|---|
| *Sintel* (2010) | `sintel.mp4` | 129,184,608 bytes (123.2 MiB) | `0abf1acd…cbe6a4` | MP4 `faststart`, H.264 High, AAC LC, 14 min 48 s | [p2p-test-file.md](p2p-test-file.md) |
| *Big Buck Bunny* (2008) | `big-buck-bunny.mp4` | 259,392,822 bytes (247.4 MiB) | `0bb49419…8dce77` | MP4 `faststart`, H.264 High, two AAC LC tracks, 10 min 34 s | [acquisition-entries.md](acquisition-entries.md) |
| *Tears of Steel* (2012) | `tears-of-steel.mp4` | 505,533,124 bytes (482.1 MiB) | `0d38c5a6…55c6fd` | MP4 `faststart`, H.264 Main, AAC LC, 12 min 14 s | [acquisition-entries.md](acquisition-entries.md) |

Each evidence page gives the licence quotes, a screenshot of the licence page, the source of the file, the full SHA-256 and the commands used to check the format. All three files are in the supported format (ADR-0015). *Sintel* was remuxed to put the index first. The two others already had it and are unchanged.

Where to get each file again:

- *Big Buck Bunny* and *Tears of Steel* are unchanged, so the file from the source torrent named on their evidence page has the same SHA-256 as the file in HomeFlix's torrent.
- *Sintel* was remuxed, so the file above exists only in the team's torrent (infohash `4e626263…004d5f`). The official file has another SHA-256 (`1dc6f2ca…11ebc1d8`). Remuxing it again with the command in [p2p-test-file.md](p2p-test-file.md#change-made-remux-only) gives a file of the same size that plays the same, but not with the same bytes, so not with the SHA-256 above. Get `sintel.mp4` through the team's torrent.

*Sintel* is the smallest and is the file of the peer-to-peer demo (J1, J10). *Big Buck Bunny* and *Tears of Steel* are larger, so they are used to fill the catalogue rather than for a live transfer.

## How they get into HomeFlix

All three are entries of the verified acquisition catalogue. An administrator downloads them from **Administration > Downloads**, and each completed file is handed to the library and appears in the catalogue (J8). This is the way used in the demo.

A download only works while a peer seeds the file. *Sintel* has a main and a backup seeder ([p2p-test-file.md](p2p-test-file.md#seeding-plan-risk-r-21)). *Big Buck Bunny* and *Tears of Steel* have a single seeder, Thomas's computer, and no backup seeder ([acquisition-entries.md](acquisition-entries.md#seeding), risk R-21): they can only be downloaded while it is on.

For a first install with no peer available, the same files can also be copied into the folder mounted as `/media/library` (`MEDIA_LIBRARY_PATH`), and that folder added and scanned in **Administration > Library**. Check the SHA-256 first.

Use one way or the other for a given film, not both. The library does not link a copied file to its catalogue entry, so a film copied into `/media/library` and later downloaded from the catalogue lands in `/media/downloads` too and is listed twice.

## Posters

**No poster is used.** The films show HomeFlix's placeholder poster (CAT-05). No third-party image is shown, so there is no poster licence to check.

The poster files that come with the published torrents are not used: HomeFlix takes a poster only as an `https` link on an allowed host (ADR-0018), not as an uploaded file. The Big Buck Bunny licence page also excludes the DVD cover from the Creative Commons licence.

To add posters later, use an image whose licence allows the use, hosted on an allowed host (`POSTER_ALLOWED_HOSTS`, `upload.wikimedia.org` by default), and record its page and licence here.
