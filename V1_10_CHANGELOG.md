# v1.10.0 — Precision Correction Loop

- Coordonnées normalisées strictes et autoritaires.
- Baseline texte explicite et centre exact des cases.
- IDs stables pour chaque overlay.
- Corrections locales `update_overlay` avec coordonnées absolues ou deltas.
- Suppression locale `delete_overlay`.
- Mesure Android des métriques de texte.
- Géométrie page/image explicite.
- Crops ciblés autour des overlays modifiés.
- Révisions de preview pour empêcher la validation d'une image périmée.
- Validation pré-export et avertissements qualité.
- États known/unknown/requires_user/requires_signature.
- Type signature protégé contre la génération automatique.
- Types date et checkbox avec sémantiques explicites.
- Contrat de boucle incrémentale sans reconstruction du plan complet.
- Batterie de tests unitaires dédiée au moteur de précision.
