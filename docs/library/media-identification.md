# Media identification rules

Rules the library service uses to decide what to do with each file found in an authorized folder during indexing. They implement requirements LIB-02 and PLY-01 and follow [ADR-0015](../adr/0015-mp4-h264-aac-video-format.md). The code is `MediaFileClassifier` in `services/library-service`.

## Supported format

HomeFlix plays one format: an **MP4** container with **H.264** video and **AAC** audio, with the index at the start of the file (`faststart`) so seeking is fast.

Prepare a compatible file with:

```
ffmpeg -i input.mov -c:v libx264 -c:a aac -movflags +faststart out.mp4
```

## Rules

The decision is made from the **file name only**. Nothing is opened or read, so a `.mp4` file with another codec inside is still indexed and may fail at playback (see the playback error handling, issue #66).

Rules are applied in this order; the first match wins.

| Order | Rule | Outcome |
|---|---|---|
| 1 | The name is empty, or starts with `.` (hidden files, macOS `._name.mp4` companions) | Ignored |
| 2 | The extension is `mp4` (any letter case) | **Supported video**: indexed |
| 3 | The extension is one of the unsupported video extensions below | **Unsupported video**: reported, not indexed |
| 4 | Anything else (no extension, subtitles, images, `.nfo`, partial downloads such as `.part` or `.!qB`) | Ignored |

The extension is the text after the last `.`, compared without regard to case. So `movie.mp4.part` is a partial download (ignored), not a video.

### Unsupported video extensions

`mkv`, `avi`, `mov`, `wmv`, `flv`, `webm`, `m4v`, `mpg`, `mpeg`, `ts`, `m2ts`, `3gp`, `ogv`, `hevc`, `h265`

## What the administrator sees

| Outcome | Indexed | Reported |
|---|---|---|
| Supported video | Yes | No |
| Unsupported video | No | Yes, with the outcome code `format_not_supported` and a plain-language message such as "This video format is not supported. Convert it to MP4 (H.264 video, AAC audio)." |
| Ignored | No | No |

The report is produced by the indexer (`POST /scans`, issue #54). This document fixes the rule and the outcome code.

## How the indexer applies the rules

- **Where it looks.** Every authorized folder, and its subfolders. Access to each folder is checked again at each scan, and a folder that cannot be read is reported (`inaccessibleRoots`) and the other folders are still scanned. The scan never fails because a folder or a network share is unavailable.
- **Links are not followed.** A symbolic link to a file or to a folder is skipped, so a link can neither lead out of the folder nor show the same file twice.
- **One title per file.** A file is identified by its folder and its path inside it. A new supported file gets a catalogue entry whose title is the file name without its extension, `Films/The Great Escape.mp4` giving `The Great Escape`. Nothing else is filled in.
- **Repeating a scan changes nothing.** A file already known only gets its size and the time it was last seen updated. The title, description, genres and poster are never touched by a scan, so an administrator's edits survive.
- **Files that disappear.** A known file that is not found in a folder that was read completely makes its title `UNAVAILABLE`, with its information, favourites and progress kept. When the file is back, the title is `AVAILABLE` again. If part of a folder could not be read, nothing is marked unavailable for it in that scan, because a file that cannot be seen is not a file that is gone.
- **A folder that cannot be read at all** (it was removed, is now a file, or the share is not mounted) makes all its available titles `UNAVAILABLE` in the same way, and they come back at the first scan that can read it again. The administrator sees the folder in `inaccessibleRoots`, and the member sees that the title is not available (AS-09). When the folder is readable but its scan fails for another reason (typically a database error), it is reported in `inaccessibleRoots` with the code `scan_failed`, not as unreadable, and its titles are not marked unavailable.
- **A removed entry stays removed.** The file record is kept when an administrator removes the catalogue entry, so a later scan does not create it again (LIB-12, issue #57).
- **One scan at a time.** A second request while a scan runs answers 409 `scan_in_progress`.

## Out of scope

- Transcoding or conversion.
- Checking codecs or `faststart` inside the file.
- Subtitles and audio-only files.
