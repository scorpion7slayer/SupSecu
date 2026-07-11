# Confidentialité de SupSécu

L’analyse anti-fraude de SupSécu fonctionne localement. L’accès Internet est réservé aux mises à jour optionnelles distribuées depuis GitHub.

## Données traitées

Lorsque l’utilisateur active volontairement le service d’accessibilité, Android permet à SupSécu de consulter le contenu visible des fenêtres. L’application traite uniquement en mémoire :

- le texte candidat de la barre d’adresse ;
- le nom du paquet du navigateur ;
- les textes, rôles et descriptions accessibles nécessaires pour déterminer si une page revendique une marque protégée.

Le service ignore les fenêtres de SupSécu et ne considère jamais une URL écrite par la page elle-même comme l’adresse visitée.

## Données non collectées

SupSécu ne collecte, n’enregistre et ne transmet :

- aucun mot de passe ou contenu de champ saisi ;
- aucun historique de navigation ;
- aucun identifiant de compte ou de téléphone ;
- aucune donnée publicitaire ou analytique ;
- aucune URL visitée ni aucun contenu de page vers un serveur tiers.

Les alertes déjà montrées sont seulement mémorisées en RAM pendant la session du service afin d’éviter les répétitions. Elles disparaissent lorsque le service est arrêté.

## Mises à jour GitHub

Le code source et les APK sont hébergés dans un dépôt GitHub privé. Pour vérifier les versions sans retourner manuellement sur GitHub, l’utilisateur peut fournir un jeton GitHub en lecture seule autorisé à accéder à ce dépôt.

- le jeton est chiffré par une clé non exportable d’Android Keystore ;
- il reste dans le stockage privé de l’application ;
- il est envoyé uniquement à `https://api.github.com/` lors d’une vérification ou d’un téléchargement demandé ;
- il peut être supprimé immédiatement avec « Oublier l’accès GitHub » ;
- aucune adresse de navigation n’est associée à ces requêtes.

L’APK téléchargé est contrôlé par SHA-256 puis doit porter exactement le même certificat de signature que la version installée. Android affiche toujours sa propre confirmation avant l’installation.

## Notifications

Une notification peut contenir le domaine suspect et l’adresse officielle. Elle est marquée privée afin que son contenu soit masqué sur l’écran verrouillé selon les réglages Android.

## Contrôle utilisateur

L’utilisateur peut désactiver l’accès à tout moment dans **Réglages Android → Accessibilité → Protection des adresses web**. La vérification manuelle et le partage d’un lien restent possibles sans activer le service.
