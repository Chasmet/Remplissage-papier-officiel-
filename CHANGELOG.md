# Historique des versions

## 1.12.1

- Migration automatique des brouillons créés avant l’ancrage exact.
- Suppression du lien MCP ancien et des overlays ChatGPT déjà mal positionnés lors de la première ouverture après mise à jour.
- Resynchronisation immédiate du PDF avec les nouveaux repères, sans demander à l’utilisateur d’effacer manuellement l’ancien résultat.

## 1.12.0

- Détection automatique des lignes, cases et zones de saisie lors de la synchronisation ChatGPT.
- Image-guide séparée, numérotée par `field_id`, sans modification de la page PDF originale.
- Ancrage MCP exact sur les mesures Android lorsque ChatGPT choisit un `field_id`.
- Centrage automatique des coches et adaptation de la taille du texte à la zone choisie.
- Refus préventif des placements libres qui recouvrent du texte déjà imprimé.
- Ouverture de ChatGPT avec l’identifiant exact du document afin d’éviter toute confusion de tâche.
- Fenêtre de présence du document actif portée à quinze minutes.

## 1.11.0

- Boucle de prévisualisation et de correction compatible avec l’ancien connecteur ChatGPT.
- Publication automatique de l’APK signé dans les mises à jour intégrées.
