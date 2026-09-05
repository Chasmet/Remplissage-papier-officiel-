# Historique des versions

## 1.13.0

- Synchronisation complète des pages, guides et du profil à la réouverture et via le bouton Synchroniser.
- Reprise automatique après interruption ; état « prêt » réservé à un envoi complet confirmé.
- Ouverture de ChatGPT différée jusqu’à la préparation du document ; distinction entre profil vide et transmis.
- Publication d’un guide même sur une page sans repère détecté.
- Les messages de présence du pont ne remplacent plus les modifications locales ni la progression affichée.
- Conservation des identifiants et états des éléments dans les brouillons.
- Copie du PDF via un fichier temporaire pour préserver la source en cas d’erreur ou de copie sur elle-même.
- Actualisation des prévisualisations après effacement du dernier élément d’une page.

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
