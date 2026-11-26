-- Two more verified entries: Spring and Sprite Fright (Blender Studio, CC BY 4.0), issue #198.
-- Rights evidence, file identity and the torrents built for HomeFlix are recorded in docs/media/acquisition-entries.md.
-- The licence allows reproduction, adaptation and redistribution, which seeding requires (ADR-0014, R-14, R-16).
-- One single-file torrent per video, because a torrent of several files is refused (J7). Both videos are H.264 and
-- AAC (ADR-0015). Spring was remuxed to put the index first; Sprite Fright already had it and is unchanged.

INSERT INTO acquisition_entry (
    title, source_name, source_url, licence, licence_url, attribution,
    download_source, expected_size_bytes, expected_format)
VALUES (
    'Spring',
    'Blender Studio',
    'https://studio.blender.org/films/spring/',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Spring (CC) Blender Foundation | cloud.blender.org/spring. Licensed under CC BY 4.0. Remuxed to put the index first, video and audio unchanged, full credits kept.',
    'magnet:?xt=urn:btih:df418f62342353102d8c74e3f66f65643bd60a47&dn=spring.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    90190400,
    'MP4'),
(
    'Sprite Fright',
    'Blender Studio',
    'https://studio.blender.org/films/sprite-fright/',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Sprite Fright (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 4.0. File unchanged, full credits kept.',
    'magnet:?xt=urn:btih:d219709e3db88021f22b26ba21ee5aeffcab3b54&dn=sprite-fright.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    110581245,
    'MP4');
