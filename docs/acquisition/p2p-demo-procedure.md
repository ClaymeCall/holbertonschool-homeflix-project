# Peer-to-peer demo: procedure and rehearsal

How to run the real peer-to-peer download in the presentation, so that any member of the team can do it and get the same result (issue L10 #106; ACQ-14, PRS-03, PRS-06; risk R-21). The brief does not accept a simulation: the file must come from another peer through BitTorrent, from a verified catalogue entry to a played title.

The file is *Sintel* (CC BY 3.0, [rights evidence](../media/p2p-test-file.md)). The first real transfer is recorded in [p2p-run.md](p2p-run.md) (J10).

## Set-up

| Role | Who | What it needs |
|---|---|---|
| **Seeder** | Thomas's computer, at home | qBittorrent running with the three torrents at 100 % and *Seeding*. Its peer port (60236) is reachable from the Internet. The computer stays awake for the whole presentation. |
| **Demo machine** | Benji's computer, at school | A HomeFlix install that has **never downloaded Sintel**. |
| **Backup network** | A phone's mobile hotspot | Only used if the school network blocks the transfer. |
| **Backup file** | *Big Buck Bunny* or *Tears of Steel* | Verified entries seeded by the same seeder ([evidence](../media/acquisition-entries.md)). Only used if Sintel cannot be downloaded. |

Why this set-up:

- **The demo machine only makes outgoing connections.** On the school network, or on a mobile hotspot, nobody can connect to it from outside. A transfer still works as long as the other side, the seeder, accepts incoming connections. That is why the seeder's port must be open.
- **The seeder is not on the demo machine.** A seeder on the same computer, or behind the same router, is not found through the tracker, because most routers do not loop back to their own public address (see [p2p-run.md](p2p-run.md)).
- **Sintel is never downloaded on the demo machine before the day.** Once a title is in the library, a new start is refused (`already_in_library`). And if `sintel.mp4` is still in the downloads folder, the torrent client finds the file and completes at once with no transfer, which would not prove anything. Rehearsals therefore use the backup films, and Sintel is kept for the presentation.

| Torrent | Info hash (v1) | Size |
|---|---|---|
| `sintel.mp4` | `4e626263b96d72a36b1b963f44188bf937004d5f` | 123.2 MiB |
| `big-buck-bunny.mp4` | `7b28c6b6cfc3eaf6b546fb2256c6d9c883f316c3` | 247.4 MiB |
| `tears-of-steel.mp4` | `e6d8867d826b6406746e067e8b08d582cbb9dee8` | 482.1 MiB |

## Before the day

### Seeder (Thomas, at home)

- [ ] qBittorrent is open, and the three torrents above are at 100 % and *Seeding*. A torrent at 0 % means the file is missing from its save folder: put it back, then **Force recheck**.
- [ ] In **Tools > Options > Connection**, the listening port is 60236, and "Use UPnP / NAT-PMP port forwarding" is ticked. If the router has no UPnP, forward port 60236 (TCP and UDP) to this computer in the router's settings.
- [ ] While qBittorrent runs, a port check (for example <https://canyouseeme.org>) says port 60236 is **open**.
- [ ] No share ratio or seeding time limit stops the torrents (**Tools > Options > BitTorrent**).
- [ ] The computer does not go to sleep: plugged in, and sleep turned off (or `caffeinate -dimsu` in a terminal left open).

### Demo machine (Benji)

- [ ] HomeFlix is installed from the README and started with `docker compose up -d`. The administrator account exists.
- [ ] In **Administration > Library**, `/media/downloads` is an authorized folder. Without it, the download reaches 100 % but the library refuses the file, and the download looks stuck ([p2p-run.md](p2p-run.md), setup gap).
- [ ] **Administration > Downloads** lists Sintel, Big Buck Bunny and Tears of Steel, and the catalogue does **not** contain Sintel.
- [ ] The rehearsals below are done, with a backup film.

## During the presentation

Signed in as the administrator. The times assume about 600 KB/s, the speed seen in J10: about 3 to 4 minutes for Sintel. The real speed depends on the seeder's upload bandwidth.

| # | Do | Expect |
|---|---|---|
| 1 | Open the home screen | Sintel is not in the catalogue |
| 2 | Open **Administration > Downloads** | The Sintel entry, with source (Blender Foundation), licence (CC BY 3.0) and size |
| 3 | Click **Download** on Sintel | The rights reminder ("Before you download"). Accept it |
| 4 | Watch the download | *Queued*, then *Downloading* with a percentage that goes up within the first minute |
| 5 | Wait until the end | *Completed*, 100 % |
| 6 | Open the home screen | Sintel is in the catalogue |
| 7 | Play Sintel | The film plays. Seek forward once to show that seeking works |

What to say while it downloads: the file comes from another peer (the seeder) through BitTorrent. Only verified entries can be downloaded, there is no free URL or magnet field, and the torrent client is not reachable from the browser.

## If it does not start

A download that finds no peer stays at *Downloading* 0 %: HomeFlix shows a torrent that waits for peers as downloading.

1. **After 1 minute at 0 %, check the transfer** on the demo machine, in a terminal in the HomeFlix folder (the command signs in with the credentials of the container, which it does not print):

   ```bash
   docker compose exec -T qbittorrent sh -c 'C=/tmp/c; B=http://localhost:$WEBUI_PORT/api/v2; curl -s -c $C --data-urlencode "username=$QBITTORRENT_USERNAME" --data-urlencode "password=$QBITTORRENT_PASSWORD" $B/auth/login >/dev/null; curl -s -b $C "$B/torrents/info" | python3 -c "
   import json,sys
   for t in json.load(sys.stdin): print(t[\"name\"], t[\"state\"], round(t[\"progress\"]*100), \"%\", \"seeds\", t[\"num_seeds\"], \"of\", t[\"num_complete\"])"'
   ```

   - `metaDL` or `stalledDL` with 0 seeds of 0: no peer was found. The network may block the tracker and DHT, or the seeder is off.
   - `stalledDL` with seeds of 1 or more, but no progress: a peer is known but cannot be reached. The seeder's port is probably closed.

2. **Ask the seeder** (by phone) whether qBittorrent is open and the torrent *Seeding*.
3. **Switch the demo machine to the phone's hotspot.** The download should carry on by itself. Check again with the command above after a minute. (To be confirmed by rehearsal 4.)
4. **If Sintel still does not start, use a backup film.** Cancel the Sintel download and start Big Buck Bunny, which is the smaller one (about twice as long as Sintel).
5. **Last resort: add the seeder by hand.** This works when the seeder is reachable, for example if the tracker is blocked. Replace `<seeder-ip>` with the seeder's public address:

   ```bash
   docker compose exec -T qbittorrent sh -c 'C=/tmp/c; B=http://localhost:$WEBUI_PORT/api/v2; curl -s -c $C --data-urlencode "username=$QBITTORRENT_USERNAME" --data-urlencode "password=$QBITTORRENT_PASSWORD" $B/auth/login >/dev/null; curl -s -b $C --data "hashes=4e626263b96d72a36b1b963f44188bf937004d5f&peers=<seeder-ip>:60236" $B/torrents/addPeers'
   ```

   Say so in the presentation: it is a step outside the application.

## Rehearsals

Record each run here: date, networks, film, result, duration, and whether the seeder was reached by an incoming connection (the peer flag `I` on the seeder's side, or the transfer working with no peer added by hand).

| # | Test | Demo machine network | Seeder network | Film | Result |
|---|---|---|---|---|---|
| 1 | Seeder port open | | Thomas's home | | To do |
| 2 | Download from home, no peer added by hand | Benji's home | Thomas's home | Big Buck Bunny or Tears of Steel | To do |
| 3 | Rehearsal on the presentation network | School | Thomas's home | The other backup film | To do |
| 4 | Backup network | Phone hotspot | Thomas's home | A backup film | To do |

## Afterwards

Update [R-21](../risks.md) with the results: the rehearsal, incoming connections and the backup plan are the three items still open after J10.
