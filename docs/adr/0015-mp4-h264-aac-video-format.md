# ADR-0015: MP4 with H.264 video and AAC audio as the supported format

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, brief section "Predictable playback and feedback"

## Context

Browsers cannot play every video file. The MVP must document one supported format, and universal transcoding is not required. Playback must support seeking without downloading the whole file, through HTTP Range requests.

## Decision

The supported format is an **MP4** container with **H.264** video and **AAC** audio. MP4 files must have their index at the start (`faststart`), otherwise seeking is slow because the player must read to the end of the file first. Other formats (for example MKV, AVI or H.265) are not supported, and HomeFlix shows a clear "format not supported" message for them.

A compatible test file can be prepared with:

```
ffmpeg -i input.mov -c:v libx264 -c:a aac -movflags +faststart out.mp4
```

## Alternatives considered

- **Transcoding to a browser-friendly format:** more flexible, but heavy and explicitly out of scope.
- **Supporting several containers and codecs:** playback would depend on each user's browser, which makes the behavior unpredictable.

## Consequences

- All major browsers play the format natively in a `<video>` element.
- The indexer must identify supported files by extension and report unsupported ones clearly.
- Demonstration files and the downloaded test file must be in this format, or be converted before use.
