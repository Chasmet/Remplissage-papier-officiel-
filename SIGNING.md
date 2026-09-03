# Signature Android permanente

Les APK `1.x` historiques ont été générées avec des clés Android Debug différentes selon les runners GitHub Actions. Elles ne peuvent donc pas se remplacer entre elles de façon fiable.

À partir de la branche `main` version `2.0.0`, les Releases destinées aux mises à jour internes doivent être signées avec une clé permanente stockée uniquement dans GitHub Actions Secrets.

## Secrets requis

- `ANDROID_KEYSTORE_BASE64` : keystore JKS encodé en Base64.
- `ANDROID_KEYSTORE_PASSWORD` : mot de passe du keystore et de la clé.

Alias fixe : `remplissage`

Empreinte SHA-256 autorisée :

`B5:BB:DB:25:21:AC:E4:77:BB:C1:AA:2F:43:1A:DD:E7:06:12:68:A3:8E:26:73:31:FC:B6:7D:1E:97:64:0A:FD`

Le workflow compile toujours `assembleDebug` pour vérifier le projet, mais ne publie aucune Release de mise à jour si les secrets de signature sont absents. Quand ils sont présents, il compile `assembleRelease`, vérifie l'empreinte du certificat avec `apksigner`, puis publie uniquement cette APK signée.

## Migration 1.x -> 2.x

À cause de l'ancienne signature Debug non stable, le passage à `2.0.0` nécessite une désinstallation/réinstallation unique. Après installation de `2.0.0`, toutes les versions futures doivent conserver la clé permanente ci-dessus afin que les mises à jour internes remplacent l'application sans supprimer ses données.
