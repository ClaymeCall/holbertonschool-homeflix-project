# Vidéo de présentation HomeFlix

Vidéo de 2 à 6 minutes (livrable : lien YouTube public ou non listé), en deux parties :
1. **Produit et démo** (~2 min) : le problème, les utilisateurs, l'application en action.
2. **Technique** (~3 min 40) : conception, stack, architecture, base de données, streaming et P2P, agent IA.

| Fichier | Contenu |
|---|---|
| [script.md](script.md) | Script minuté, voix off, image associée, captures à enregistrer |
| [tasks.md](tasks.md) | Répartition des tâches, avec « Done when » prêts à copier dans des issues |
| [animations/](animations/) | Source des schémas et titres animés (HTML) et script de rendu en MP4 |

## Chaîne de production

```
captures d'écran (OBS)  ┐
voix off (audio)        ├─► montage ─► export 1080p ─► YouTube (non listé) ─► lien
animations (animations/)┘
```

## Régénérer les animations

Prérequis : Python 3, `pip install playwright`, Google Chrome ou Chromium, ffmpeg, police Inter.

```bash
cd docs/video/animations
python3 render.py all out/            # 10 clips dans out/ (≈ 2 min)
python3 render.py arch prev 6,40      # aperçus JPEG dans out/, sans encoder la vidéo
```

Les textes et durées de chaque scène sont dans `scenes.html` (objet `S`, une entrée par scène).
Les couleurs reprennent les tokens du frontend (`frontend/src/styles/tokens.css`).
Les clips MP4 ne sont pas versionnés (dossier `out/` ignoré) : ils se régénèrent.

## Règles d'exactitude

Tout ce qui est dit doit correspondre à ce qui fonctionne : distinguer clairement le réalisé de l'envisagé
(le transcodage, les séries, les applications mobiles ne sont **pas** faits). Chaque auteur de segment
vérifie son texte contre le dépôt (README, ADR, `docs/architecture/`).
