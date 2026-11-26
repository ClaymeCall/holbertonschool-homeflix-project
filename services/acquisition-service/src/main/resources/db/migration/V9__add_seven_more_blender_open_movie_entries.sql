-- Seven more verified entries: Blender open movies under Creative Commons Attribution, issue #198.
-- Rights evidence, file identity and the torrents built for HomeFlix are recorded in docs/media/acquisition-entries.md.
-- Each licence allows reproduction, adaptation and redistribution, which seeding requires (ADR-0014, R-14, R-16).
-- One single-file torrent per video, because a torrent of several files is refused (J7). All videos are H.264 and
-- AAC (ADR-0015). Coffee Run, Cosmos Laundromat, Hero and Wing It! were remuxed to put the index first, and the
-- attribution says so, as CC BY asks; the three others are unchanged.

INSERT INTO acquisition_entry (
    title, source_name, source_url, licence, licence_url, attribution,
    download_source, expected_size_bytes, expected_format)
VALUES (
    'Caminandes 3: Llamigos',
    'Blender Foundation',
    'https://commons.wikimedia.org/wiki/File:Caminandes_3_-_Llamigos_-_Blender_Animated_Short.webm',
    'Creative Commons Attribution 3.0 Unported (CC BY 3.0)',
    'https://creativecommons.org/licenses/by/3.0/',
    'Caminandes 3: Llamigos (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 3.0. File unchanged, full credits kept.',
    'magnet:?xt=urn:btih:13179b286c170a89d226f83724ced32bd40919d4&dn=caminandes-3-llamigos.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    138945811,
    'MP4'),
(
    'Charge',
    'Blender Studio',
    'https://commons.wikimedia.org/wiki/File:Charge_-_Blender_Open_Movie-full_movie.webm',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Charge (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 4.0. File unchanged, full credits kept.',
    'magnet:?xt=urn:btih:5175c58aa5334f23bad19f3c79d4c493f255e597&dn=charge.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    191560304,
    'MP4'),
(
    'Coffee Run',
    'Blender Studio',
    'https://commons.wikimedia.org/wiki/File:Coffee_Run_-_Blender_Open_Movie-full_movie.webm',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Coffee Run (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 4.0. Remuxed to put the index first, video and audio unchanged, full credits kept.',
    'magnet:?xt=urn:btih:909a2a7aa6560cc4de8bb8285a8b480ff12514da&dn=coffee-run.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    17475696,
    'MP4'),
(
    'Cosmos Laundromat',
    'Blender Foundation',
    'https://archive.org/details/CosmosLaundromatFirstCycle',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Cosmos Laundromat: First Cycle (CC) Blender Foundation | gooseberry.blender.org. Licensed under CC BY 4.0. Remuxed to put the index first, video and audio unchanged, full credits kept.',
    'magnet:?xt=urn:btih:e8b8a9f4b82bc070aafb9c50348c85719c35e5e7&dn=cosmos-laundromat.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    220087074,
    'MP4'),
(
    'Elephants Dream',
    'Blender Foundation',
    'https://commons.wikimedia.org/wiki/File:Elephants_Dream_(2006).webm',
    'Creative Commons Attribution 2.5 Generic (CC BY 2.5)',
    'https://creativecommons.org/licenses/by/2.5/',
    'Elephants Dream (c) copyright 2006, Blender Foundation / Netherlands Media Art Institute / www.elephantsdream.org. Licensed under CC BY 2.5. File unchanged, full credits kept.',
    'magnet:?xt=urn:btih:247ad6ae086248ee40e8bf537cbb5ae1b8c9bebf&dn=elephants-dream.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    67801890,
    'MP4'),
(
    'Hero',
    'Blender Foundation',
    'https://commons.wikimedia.org/wiki/File:HERO_-_Blender_Open_Movie-full_movie.webm',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Hero (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 4.0. Remuxed to put the index first, video and audio unchanged, full credits kept.',
    'magnet:?xt=urn:btih:11eb33acc6693933c7c7bc946a3b6c5e73e51a34&dn=hero-2018.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    53008533,
    'MP4'),
(
    'Wing It!',
    'Blender Studio',
    'https://commons.wikimedia.org/wiki/File:WING_IT!_-_Blender_Open_Movie-full_movie.webm',
    'Creative Commons Attribution 4.0 International (CC BY 4.0)',
    'https://creativecommons.org/licenses/by/4.0/',
    'Wing It! (CC) Blender Foundation | studio.blender.org. Licensed under CC BY 4.0. Remuxed to put the index first, video and audio unchanged, full credits kept.',
    'magnet:?xt=urn:btih:3f6cc49e3fc6fc2fc0e7734590973a76ed3103fe&dn=wing-it.mp4&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce',
    65577791,
    'MP4');
