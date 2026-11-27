# Vidéo : répartition des tâches

Propositions de propriétaires d'après les rôles du [working agreement](../team/working-agreement.md) ;
à ajuster. Une tâche = une issue (modèle « task »), labels `documentation` + priorité.

| # | Tâche | Propriétaire proposé |
|---|---|---|
| V1 | Préparer l'environnement de démo | Clement |
| V2 | Enregistrer les captures C1 à C4 (installation, profil, accueil, lecture) | Clement |
| V3 | Enregistrer la capture P2P C5 et C6 (téléchargement complet, puis lecture) | Thomas |
| V4 | Voix off partie 1 (produit et démo) | à choisir |
| V5 | Voix off partie 2 : conception, stack, architecture, BDD, streaming, agent IA | un segment par membre |
| V6 | Montage, sous-titres, export 1080p | à choisir |
| V7 | Relecture d'exactitude de chaque segment contre le dépôt | chaque auteur |
| V8 | Mise en ligne YouTube (non listé) et vérification du lien | à choisir |

## Done when

**V1** Environnement de démo
- [ ] Stack démarrée, `docker compose ps` : tous les services `healthy`
- [ ] Bibliothèque vide, `/media/downloads` ajouté comme dossier média
- [ ] Un compte admin et un compte membre créés
- [ ] Seeder en ligne pour *Sintel* ; *Big Buck Bunny* et *Tears of Steel* copiés pour remplir le catalogue

**V2** Captures C1 à C4 (1080p, sans son, curseur visible, zoom navigateur ≥ 125 %)
- [ ] C1 installation et scan (~15 s) · C2 choix du profil (~8 s)
- [ ] C3 accueil, recherche, Ma liste (~25 s) · C4 lecture, saut, fermeture, reprise (~25 s)

**V3** Captures P2P
- [ ] C5 : rappel des droits, lancement, progression jusqu'à la fin (enregistrer toute la durée, ≈ 4 min)
- [ ] C6 : le film passe en `AVAILABLE`, puis lecture (~10 s)

**V4 / V5** Voix off
- [ ] Une prise audio par segment du [script](script.md), WAV ou M4A, lieu calme, sans bruit de clavier
- [ ] Texte relu contre le dépôt (V7) avant enregistrement

**V6** Montage
- [ ] Durée totale entre 2 et 6 minutes, les deux parties identifiables
- [ ] Texte et schémas lisibles, son audible et homogène, sous-titres

**V8** Mise en ligne
- [ ] Vidéo YouTube publique ou non listée, lien ouvert dans une fenêtre de navigation privée
- [ ] Lien ajouté au README ou au livrable demandé
