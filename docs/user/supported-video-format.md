# Supported video format

HomeFlix plays **one** video format. Videos in any other format are not played, so convert them first (see below).

## The format

| | Required |
|---|---|
| Container (file extension) | **MP4** (`.mp4`) |
| Video codec | **H.264** |
| Audio codec | **AAC** |
| Index position | At the **start** of the file (`faststart`) |

MP4 with H.264 and AAC plays natively in all major browsers, with no plugin.

### Why `faststart` matters

An MP4 file contains an index that tells the player where each moment of the video is. Many tools write it at the **end** of the file. The player then has to read the whole file before it can start or jump to another position, so seeking is slow. With `faststart`, the index is at the start and seeking is fast. HomeFlix relies on this to let you skip around a film without downloading all of it.

## Converting a video

If your file is in another format (for example MKV, AVI, MOV, or H.265 video), convert it with [ffmpeg](https://ffmpeg.org/):

```
ffmpeg -i input.mov -c:v libx264 -c:a aac -movflags +faststart out.mp4
```

- `-c:v libx264` encodes the video as H.264.
- `-c:a aac` encodes the audio as AAC.
- `-movflags +faststart` moves the index to the start of the file.

Replace `input.mov` with your file and `out.mp4` with the name you want. If your file is already MP4 but plays slowly when seeking, the same command fixes it.

Put the converted file in your media folder and run the indexing again.

## What you see for other formats

When indexing finds a video in an unsupported format, it is **not added to the catalogue** and is reported with a "format not supported" message:

> This video format is not supported. Convert it to MP4 (H.264 video, AAC audio).

Files that are not videos (subtitles, images, partial downloads) are skipped without a message.

HomeFlix identifies videos by their file extension only. It does not open the file, so an `.mp4` file with a different codec inside is added to the catalogue but may fail when played, and the player then shows an error message.

## Transcoding is not supported

HomeFlix does **not** convert videos. Transcoding is out of scope for this project: no format is converted automatically, on upload, at indexing time or during playback. Convert your files yourself before adding them.

## Related

- [ADR-0015: MP4 with H.264 video and AAC audio](../adr/0015-mp4-h264-aac-video-format.md), the reasons for this choice.
