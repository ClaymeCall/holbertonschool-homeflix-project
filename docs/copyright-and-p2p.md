# Copyright and peer-to-peer awareness note

This note is for everyone who uses HomeFlix: the **administrator** who adds and downloads content, and the **household members** who watch it. Read it before the first download.

## Peer-to-peer is a transfer method, not a permission

HomeFlix can fetch videos with peer-to-peer (BitTorrent). Peer-to-peer only describes **how** a file travels between computers. It does **not** give anyone the right to download, copy or share a work. Whether you may use a video depends only on who owns it and on its licence.

## What you may use

- **Personal videos**: films you shot yourself, of people who agreed to be filmed.
- **Your own creations**: videos you made, without third-party music, images or footage you have no right to use.
- **Content whose licence allows it**: for example works under a Creative Commons licence that permits copying and sharing. Check **both** rights:
  - **reproduction**: you may copy the file onto your computer;
  - **redistribution**: you may share it with others. This matters because peer-to-peer shares the file automatically (see below).

If a licence asks for attribution, keep the credit with the file.

## What is not allowed

- Downloading or sharing films, series or any protected work without the rights holder's permission.
- Using a website, tracker or index that distributes works without authorization.
- Bypassing any copy protection (DRM).

## Sharing happens automatically

While a file downloads, the peer-to-peer client also **sends pieces of it to other people**. After the download completes, it keeps sharing (seeding) for a **limited** time (ADR-0014; the exact limit is set in issue J4, #77). This is why the redistribution right must be checked, not only the right to download.

## What HomeFlix does to help

- Only entries from a **verified acquisition catalogue** can be downloaded. Each entry records its source and its licence or other authorization.
- There is **no field to enter a URL or a magnet link**, so HomeFlix cannot be pointed at an arbitrary source.
- Only an **administrator** can start or cancel a download. The server enforces it, not only the interface.
- A **rights reminder** is shown before every download, and the server refuses a start that does not confirm it (see below).

## What HomeFlix cannot do for you

**The in-app warning does not replace checking the rights.** HomeFlix cannot know who owns a video or what its licence allows. The administrator who adds an entry to the catalogue is responsible for checking it.

## Before adding an entry to the catalogue

- [ ] The source is the rights holder or a source they authorized.
- [ ] The licence allows **reproduction and redistribution** for this use.
- [ ] Evidence is kept: the licence page (URL and a saved copy) and the date it was checked.
- [ ] The required attribution is recorded.
- [ ] The file is in the supported format (MP4, H.264, AAC; ADR-0015).

## In-app reminder (reference text)

The interface shows this text, in English, before every download (issue J9, #82): the download starts only after **I understand**, and the request records it (`rightsAcknowledged`). Change it here first, then in `frontend/src/pages/DownloadsPage.tsx`.

> **Before you download**
>
> HomeFlix uses peer-to-peer to transfer files. While a file downloads, and for a limited time afterwards, pieces of it are automatically shared with other people.
>
> Peer-to-peer gives you no rights over a work. Only download content whose licence allows both copying **and** sharing.
>
> This reminder does not replace checking the rights.
>
> **[I understand]**
