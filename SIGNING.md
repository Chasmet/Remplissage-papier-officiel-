# Signature Android permanente

Les anciennes APK jusqu'à la série `1.3.x` ont été générées avec des clés Debug différentes selon les runners GitHub Actions. Android ne peut pas les remplacer par une APK signée avec une autre clé.

## Chaîne permanente

À partir de `1.4.0`, toutes les Releases officielles doivent utiliser exactement la même clé Android permanente.

Empreinte SHA-256 autorisée :

`B5:BB:DB:25:21:AC:E4:77:BB:C1:AA:2F:43:1A:DD:E7:06:12:68:A3:8E:26:73:31:FC:B6:7D:1E:97:64:0A:FD`

Alias : `remplissage`

Le workflow GitHub Actions :

1. compile et exécute les vérifications de qualité ;
2. obtient temporairement la clé via une requête GitHub OIDC limitée au dépôt, à la branche `main` et au workflow Android ;
3. construit `assembleRelease` ;
4. vérifie avec `apksigner` que le certificat correspond exactement à l'empreinte permanente ci-dessus ;
5. calcule le SHA-256 de l'APK ;
6. publie l'APK uniquement si le commit est toujours le dernier commit de `main` ;
7. refuse de réutiliser un numéro de version déjà publié pour un autre commit ;
8. supprime le matériel de signature du runner après compilation.

La clé privée ne doit jamais être placée dans l'APK, dans le dépôt GitHub public ou dans un fichier utilisateur.

## Règle impérative

Ne jamais changer cette clé pour les futures versions. Toute APK destinée à remplacer `1.4.0` ou une version ultérieure doit être signée avec le certificat ci-dessus et utiliser un `versionCode` strictement supérieur.

Les versions `1.3.x` et antérieures peuvent nécessiter une désinstallation/réinstallation unique pour rejoindre la chaîne permanente.
