# Confidentialité de SupSécu

L’analyse anti-fraude de SupSécu fonctionne localement. L’accès Internet est réservé aux mises à jour publiques de l’application et au téléchargement périodique de listes publiques de domaines dangereux.

## Données traitées

Lorsque l’utilisateur active volontairement le service d’accessibilité, Android permet à SupSécu de consulter le contenu visible des fenêtres. L’application traite uniquement en mémoire :

- le texte candidat de la barre d’adresse ;
- le nom du paquet du navigateur ;
- les textes, rôles et descriptions accessibles nécessaires pour déterminer si une page revendique une marque protégée.
- sur Android 11 ou supérieur, une capture temporaire d’une page transactionnelle inconnue afin de rechercher localement un logo Lidl très caractéristique.

Le service ignore les fenêtres de SupSécu et ne considère jamais une URL écrite par la page elle-même comme l’adresse visitée.
La capture temporaire est analysée en mémoire, puis immédiatement libérée. Elle n’est ni enregistrée dans la galerie ou les fichiers, ni envoyée sur Internet.

## Données non collectées

SupSécu ne collecte, n’enregistre et ne transmet :

- aucun mot de passe ou contenu de champ saisi ;
- aucun historique de navigation ;
- aucun identifiant de compte ou de téléphone ;
- aucune donnée publicitaire ou analytique ;
- aucune URL visitée ni aucun contenu de page vers un serveur tiers.

Les alertes déjà montrées sont seulement mémorisées en RAM pendant la session du service afin d’éviter les répétitions. Elles disparaissent lorsque le service est arrêté.

## Listes publiques de sécurité

SupSécu télécharge au maximum une fois par jour des fichiers complets de domaines publiés par Phishing.Database, CERT Polska et PhishDestroy. La recherche du domaine visité se fait ensuite dans une base SQLite locale.

- les URL visitées ne sont jamais envoyées à ces fournisseurs ;
- les téléchargements utilisent uniquement des adresses HTTPS fixées dans l’application ;
- les redirections vers un autre hôte sont refusées ;
- les fichiers temporaires sont supprimés après import ;
- la base locale contient uniquement des noms de domaines et la date de mise à jour.

## Mises à jour GitHub

Le code source et les APK sont hébergés dans le dépôt public `scorpion7slayer/SupSecu`. L’application consulte anonymement l’API GitHub et n’enregistre ni jeton, ni compte, ni identifiant GitHub.

L’APK téléchargé est contrôlé par SHA-256 puis doit porter exactement le même certificat de signature que la version installée. Android affiche toujours sa propre confirmation avant l’installation.

## Commentaires

SupSécu peut préparer un e-mail adressé au contact du projet. Un commentaire général contient la version de l’application, la version Android et le modèle de l’appareil. L’URL et le verdict sont ajoutés seulement lorsque l’utilisateur choisit explicitement « Partager ce signalement ». L’utilisateur peut modifier le destinataire et le texte, puis doit confirmer l’envoi dans son application e-mail. Si aucune application e-mail n’est disponible, le sélecteur de partage Android est proposé. SupSécu ne reçoit aucune copie automatique autrement.

## Notifications

Une notification de marque contient uniquement son domaine officiel, jamais le domaine suspect détecté. Elle propose de quitter la page ou d’ouvrir le site officiel et reste marquée privée afin que son contenu soit masqué sur l’écran verrouillé selon les réglages Android.

## Contrôle utilisateur

L’utilisateur peut désactiver l’accès à tout moment dans **Réglages Android → Accessibilité → Protection des adresses web**. La vérification manuelle et le partage d’un lien restent possibles sans activer le service.
