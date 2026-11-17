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

The report is produced by the indexer (issue #54); this document only fixes the rule and the outcome code.

## Out of scope

- Transcoding or conversion.
- Checking codecs or `faststart` inside the file.
- Subtitles and audio-only files.
