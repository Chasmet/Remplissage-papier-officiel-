# Remplissage Papier Officiel — Precision Loop v1.10.0

## Contrat de rendu

- Coordonnées x/y strictes, normalisées 0..1.
- Origine en haut à gauche pour les images et le protocole.
- Pour `text` et `date`, y est la baseline exacte.
- Pour `checkbox`, x/y est le centre exact.
- Aucun snap, auto-fit ou repositionnement des coordonnées ChatGPT.
- Chaque overlay possède un `overlay_id` stable.
- Les états de donnée supportés sont `known`, `unknown`, `requires_user`, `requires_signature`.
- `signature` est un type explicite et ne doit jamais être inventé par ChatGPT.

## Boucle attendue

PDF original -> image réelle -> placements ChatGPT -> rendu Android -> preview -> correction locale -> nouvelle preview -> validation -> PDF final.

## Corrections locales

Une correction cible uniquement `overlay_id` et peut utiliser x/y absolus ou des deltas normalisés. Les autres overlays restent inchangés.

## Qualité

Le moteur Android mesure les métriques de texte et valide au minimum les sorties de page, les chevauchements entre overlays et les dimensions déclarées. Les previews doivent être produites depuis la même géométrie que le PDF final.

## Priorité MCP

Le serveur doit exposer les primitives équivalentes à : `measure_text`, `update_overlay`, `delete_overlay`, `render_preview`, `get_preview_crop`, `validate_layout` et `export_final_pdf`, tout en conservant les outils MCP historiques pour compatibilité.
