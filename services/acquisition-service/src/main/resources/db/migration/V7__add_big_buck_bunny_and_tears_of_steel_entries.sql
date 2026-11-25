-- Two more verified entries: Big Buck Bunny and Tears of Steel (Blender Foundation, CC BY 3.0), issue #184.
-- Rights evidence, file identity and the torrents built for HomeFlix are recorded in docs/media/acquisition-entries.md.
-- The licence allows reproduction and redistribution, which seeding requires (ADR-0014, R-14, R-16).
-- One single-file torrent per video, because a torrent of several files is refused (J7). Both videos are
-- already MP4 faststart with H.264 and AAC, so they were not remuxed (ADR-0015).

INSERT INTO acquisition_entry (
    title, source_name, source_url, licence, licence_url, attribution,
    download_source, expected_size_bytes, expected_format)
VALUES (
    'Big Buck Bunny',
    'Blender Foundation',
    'https://peach.blender.org/about/',
    'Creative Commons Attribution 3.0 Unported (CC BY 3.0)',
    'https://creativecommons.org/licenses/by/3.0/',
    'Big Buck Bunny (c) copyright 2008, Blender Foundation / www.bigbuckbunny.org. Licensed under CC BY 3.0. File unchanged, full credits roll kept.',
    'magnet:?xt=urn:btih:7b28c6b6cfc3eaf6b546fb2256c6d9c883f316c3&dn=big-buck-bunny.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    259392822,
    'MP4'),
(
    'Tears of Steel',
    'Blender Foundation',
    'https://mango.blender.org/sharing/',
    'Creative Commons Attribution 3.0 Unported (CC BY 3.0)',
    'https://creativecommons.org/licenses/by/3.0/',
    'Tears of Steel (CC) Blender Foundation | mango.blender.org. Licensed under CC BY 3.0. File unchanged, full credits scroll kept.',
    'magnet:?xt=urn:btih:e6d8867d826b6406746e067e8b08d582cbb9dee8&dn=tears-of-steel.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    505533124,
    'MP4');
