-- First verified entry: Sintel (Blender Foundation, CC BY 3.0), the peer-to-peer test file.
-- Rights evidence, file identity and the torrent built for the demo are recorded with issue J1 (#74).
-- The licence allows reproduction and redistribution, which seeding requires (ADR-0014, R-14, R-16).
-- Remuxed to MP4 faststart without re-encoding: 129,184,608 bytes, H.264 and AAC (ADR-0015).

INSERT INTO acquisition_entry (
    title, source_name, source_url, licence, licence_url, attribution,
    download_source, expected_size_bytes, expected_format)
VALUES (
    'Sintel',
    'Blender Foundation',
    'https://durian.blender.org/',
    'Creative Commons Attribution 3.0 Unported (CC BY 3.0)',
    'https://creativecommons.org/licenses/by/3.0/',
    'Sintel © copyright Blender Foundation | durian.blender.org. Licensed under CC BY 3.0. Remuxed to MP4 faststart for HomeFlix; video and audio streams unchanged, full credits scroll kept.',
    'magnet:?xt=urn:btih:4e626263b96d72a36b1b963f44188bf937004d5f&dn=sintel.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    129184608,
    'MP4');
