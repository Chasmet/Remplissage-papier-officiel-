# Remplissage papier officiel

Application Android de remplissage de formulaires PDF reliée à ChatGPT par un serveur MCP Supabase.

Depuis la version 1.12, l’application détecte les zones de saisie, génère une image-guide séparée et transmet des `field_id` stables. ChatGPT choisit le champ selon son libellé; le serveur place ensuite le contenu sur l’ancre exacte mesurée par Android. Les coordonnées libres restent disponibles uniquement pour les zones sans repère fiable.

Les APK signés sont publiés dans les Releases GitHub. Le gestionnaire de mise à jour intégré aux réglages lit cette dernière Release afin de proposer chaque nouvelle version dans l’application.
