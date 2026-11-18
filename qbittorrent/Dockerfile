# qBittorrent for HomeFlix (ADR-0014, ADR-0023): the pinned linuxserver.io image (E11, R-34) plus a start
# script that applies the HomeFlix settings at every start (login from .env, seeding limit, peer port).
FROM lscr.io/linuxserver/qbittorrent:5.3.0_v2.1.2-ls487

COPY --chmod=644 configure_qbittorrent.py /usr/local/share/homeflix/configure_qbittorrent.py
COPY --chmod=755 homeflix-config.sh /custom-cont-init.d/50-homeflix-config
