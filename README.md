# Remplissage PDF

Application Android de remplissage de PDF. La synchronisation ChatGPT passe par un serveur MCP Supabase et nécessite un jeton de session individuel. Le bouton ChatGPT demande une confirmation avant le premier envoi d'un document et du profil.

Depuis la version 1.12, l’application détecte les zones de saisie, génère une image-guide séparée et transmet des `field_id` stables. ChatGPT choisit le champ selon son libellé; le serveur place ensuite le contenu sur l’ancre exacte mesurée par Android. Les coordonnées libres restent disponibles uniquement pour les zones sans repère fiable.

Deux variantes Android utilisent le même code et le même identifiant d'application :

- `direct` : APK signé publié dans les Releases GitHub, avec mise à jour manuelle intégrée ;
- `play` : Android App Bundle pour les tests Google Play, sans permission d'installation ni mise à jour d'APK intégrée.

`./gradlew assembleDirectDebug assemblePlayDebug` compile les variantes de test. `./gradlew assembleDirectRelease bundlePlayRelease` exige les variables `ANDROID_KEYSTORE_FILE` et `ANDROID_KEYSTORE_PASSWORD` pour signer les livrables. Les formulaires et le profil restent sur l'appareil tant que l'utilisateur ne synchronise pas avec ChatGPT ; les sauvegardes JSON manuelles ne sont pas chiffrées.

La distribution publique demande encore une identité distincte par utilisateur, une politique de confidentialité, des essais Android réels et un compte développeur. L'audit détaillé est remis séparément au propriétaire du projet. L'App Store nécessite une application iOS distincte : ce dépôt ne contient que du code Android.
