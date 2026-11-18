# File access rules

How the library service keeps file access inside the authorized folders. Covers LIB-08 and PLY-04, and risks R-01 (path traversal) and R-04 (symbolic links pointing outside the roots). The code is `MediaPathGuard` in `services/library-service`.

## Rules

1. **Clients never send a path.** API endpoints take media IDs only. The path to read comes from the library's own database. No endpoint may accept a file path, folder name or URL-encoded path from a client.
2. **Resolve first, then check.** Before any file is opened, its *real* path is resolved: `..` sequences are removed and symbolic links are followed. Only then is it compared with the real path of each authorized root.
3. **Component-wise comparison.** A path is inside a root only if the root is a parent by path component, not by text prefix. `/media/library-evil/movie.mp4` is **not** inside `/media/library`.
4. **Symbolic links.** A link inside a root that points outside it is refused. A link that stays inside the root is allowed. A root that is itself a symbolic link works, because roots are also resolved to their real path.
5. **Generic message to the user, details in the log.** A refused access raises `MediaAccessDeniedException`, with a fixed message that contains no path (SEC-04). The requested and real paths are written to the server log.

## Outcomes

| Situation | Reason | Message to the user | Log |
|---|---|---|---|
| File does not exist or cannot be resolved | `NOT_FOUND` | "This file cannot be accessed." | Warning with the requested path |
| Real path is outside every authorized root | `OUTSIDE_ROOTS` | "This file cannot be accessed." | Warning with the requested and real paths |

Callers map the reason to their own feedback (for example a 404 for `NOT_FOUND` and a 403 for `OUTSIDE_ROOTS`); the playback error handling is issue #66.

## Defence in depth

The container only sees what Docker mounts (`/media/library` and `/media/downloads`, both read-only in `compose.yaml`), so even a guard bug cannot read other host files or write to the library.

## Authorized folders (LIB-01, LIB-03, R-02)

An administrator authorizes a folder with `POST /roots`. The service accepts it only when all of this holds, in this order, and each failure has its own code (see the [error model](../architecture/error-model.md)):

| Check | Code | Status |
|---|---|---|
| The path is absolute and valid | `validation_failed` | 400 |
| The path, with `..` and a trailing slash removed, is inside `/media/library` or `/media/downloads` | `directory_outside_library` | 400 |
| It exists | `directory_not_found` | 400 |
| Its real path (links followed) is still inside the mounted paths | `directory_outside_library` | 400 |
| It is a directory | `not_a_directory` | 400 |
| It can be listed | `directory_not_readable` | 400 |
| It is not already authorized, and does not contain or lie inside an authorized folder | `root_already_added`, `root_overlaps` | 409 |

The "inside the mounted paths" check comes before the existence check and uses only the text of the path, so the answer for a folder outside them does not reveal whether it exists. Messages never contain a path. The path and the cause are written to the log.

Removing a folder marks the titles that were in it as unavailable and deletes only database rows. Nothing on disk is touched.

### Evidence that the library is read-only (R-03)

The mounts are declared read-only in `compose.yaml`, so the service cannot change a source file even if it had a bug. A write test on a read-only mount made the same way, run on 2026-11-18:

```console
$ docker run --rm -v "$PWD/data/library:/media/library:ro" alpine:3.20 sh -c 'touch /media/library/should-fail; echo "exit=$?"'
touch: /media/library/should-fail: Read-only file system
exit=1
```

This shows how the mount behaves. It was run in a plain container, not in the library service container, and is to be repeated in the service container for the acceptance run (K6).
