# Script de la vidéo de présentation (≈ 5 min 40)

Format : capture d'écran + voix off + schémas animés. Rythme visé : ~150 mots/min.
Les clips animés sont chronométrés sur ce script ; à recaler sur la voix réellement enregistrée.

## Partie 1 : produit et démo (0:00 – 1:55)

| Temps | Voix off | Image |
|---|---|---|
| 0:00–0:22 | Vos films sont quelque part sur un disque dur, dans des dossiers sans fin. Vous voudriez les regarder en famille, depuis le canapé, sans abonnement. Voici HomeFlix : votre propre plateforme de streaming, hébergée chez vous, pour toute la famille. | `intro.mp4`, puis capture C3 (accueil) |
| 0:22–1:35 | Première ouverture : l'administrateur crée son compte, puis ajoute son dossier de vidéos. Un scan, et tout apparaît, sans jamais de doublon. Chaque membre du foyer choisit son profil, avec ses favoris et sa progression. Sur l'accueil : ajouts récents, catégories, recherche, « Continuer à regarder ». J'ajoute un film à Ma liste. Je lance la lecture, je saute au milieu, je ferme… et en rouvrant, je reprends exactement où je m'étais arrêté. Et s'il manque un film ? L'administrateur le télécharge en pair-à-pair depuis un catalogue vérifié de films libres de droits, après un rappel sur les droits. Le téléchargement avance… et le film rejoint la bibliothèque, prêt à être lu. | C1 → C2 → C3 → C4 → C5 (accéléré) → C6 |
| 1:35–1:55 | Vos fichiers restent chez vous, en lecture seule : HomeFlix n'y touche jamais. Un profil par personne. Et toute la plateforme se lance avec Docker Compose. Le cinéma maison, simplement. | `value.mp4` |

## Partie 2 : comment c'est construit (1:55 – 5:40)

| Temps | Voix off | Image |
|---|---|---|
| 1:55–2:00 | Voyons maintenant comment HomeFlix est construit. | `divider.mp4` |
| 2:00–2:40 | Nous sommes trois, et tout part des besoins : deux profils, l'administrateur qui installe et gère, le membre du foyer qui veut regarder ce soir, et quatre parcours utilisateurs. Chaque décision d'architecture est consignée dans un ADR : vingt-cinq au total, avec les alternatives écartées. Les contrats d'API sont écrits à la main en OpenAPI avant le code, pour que le front et les services avancent en parallèle. Chaque tâche est une issue, chaque changement une pull request relue. | `design.mp4` |
| 2:40–3:20 | Côté interface : React avec Vite et TypeScript, le plus grand écosystème pour une équipe qui apprend vite. Côté serveur : Java 21 et Spring Boot avec Maven, imposés par le sujet, ce qui nous donne un socle commun aux trois services. PostgreSQL pour les données, avec Flyway pour versionner le schéma. Et Docker Compose : un seul fichier démarre toute la plateforme, même sur un petit serveur. | `stack.mp4` |
| 3:20–4:05 | Trois microservices : identité pour les comptes et profils, bibliothèque pour le catalogue et la lecture, acquisition pour les téléchargements. Le navigateur ne parle qu'à une passerelle Nginx, le seul port exposé. Les services s'appellent en REST synchrone, avec un jeton par appelant. Pour l'authentification, identité signe un jeton JWT RS256 dans un cookie HttpOnly ; les autres services n'ont que la clé publique et vérifient eux-mêmes. Pas de broker de messages : trop lourd pour ce projet. | `arch.mp4` |
| 4:05–4:30 | Chaque service a sa propre base PostgreSQL et son propre utilisateur : un service ne peut physiquement pas lire les tables d'un autre. Donc pas de clés étrangères entre services, seulement des identifiants. La bibliothèque stocke par exemple l'identifiant d'un profil pour ses favoris, sans jamais interroger la base d'identité. | `db.mp4` |
| 4:30–5:05 | Pour la lecture, le navigateur lit directement un MP4 H.264 et AAC, avec des requêtes HTTP Range : on saute dans le film sans tout télécharger. Pour le P2P, nous n'avons pas écrit de moteur BitTorrent : acquisition pilote qBittorrent, isolé sur un réseau privé. Le fichier est vérifié, puis la bibliothèque est prévenue et l'indexe. Notre essai : Sintel, 123 Mo, en 3 minutes 42. | `stream.mp4` |
| 5:05–5:25 | Dernier point : l'agent IA. Un relecteur en lecture seule, qui compare ce que la documentation dit à ce que le code fait. Son premier rapport a relevé dix écarts, avec fichier, ligne et gravité. Nous vérifions chacun avant d'agir : il ne code pas. | `agent.mp4` |
| 5:25–5:40 | HomeFlix n'est pas fini : pas encore de transcodage, de séries ni d'applications mobiles. Mais le cœur fonctionne : vos films, vos profils, chez vous. Merci ! | `outro.mp4` |

Total ≈ 800 mots. Si la lecture dépasse 6:00, coupe d'abord la partie « conception ».

## Captures à enregistrer (écran 1080p, sans son)

| Code | Contenu | Durée utile |
|---|---|---|
| C1 | Écran d'accueil de première installation (compte admin), ajout du dossier, scan | ~15 s |
| C2 | Connexion, choix du profil | ~8 s |
| C3 | Accueil : ajouts récents, catégories, recherche, ajout à Ma liste | ~25 s |
| C4 | Lecture, saut au milieu, fermeture, réouverture avec reprise | ~25 s |
| C5 | Administration > Downloads : rappel des droits, lancement, progression (enregistrer toute la durée, à raccourcir au montage) | brut ≈ 4 min |
| C6 | Le film passe en AVAILABLE, puis lecture | ~10 s |
| Option | Terminal `docker compose ps` (tout healthy) ; dossier `docs/adr` ; extrait de `docs/team/ai-agent-reports/001.md` | ~8 s chacun |

Avant d'enregistrer : bibliothèque vide pour la démo P2P, `/media/downloads` ajouté à la main comme dossier média, un seeder en ligne pour *Sintel*, un compte admin et un compte membre. L'interface est en anglais seulement (ADR-0021) : c'est normal, la voix la commente.
