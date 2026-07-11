# Product

## Register

product

## Users

Toute personne utilisant un navigateur Android, y compris les utilisateurs peu familiers avec les noms de domaine et les techniques d’hameçonnage. L’application intervient pendant la navigation, au moment où une page tente de se faire passer pour une enseigne connue.

## Product Purpose

SupSécu vérifie localement l’adresse visible dans le navigateur et les indices de marque exposés à l’accessibilité Android. Quand une page revendique une marque sans utiliser l’un de ses domaines officiels, l’application explique le risque, affiche l’adresse officielle et propose de quitter ou d’ouvrir le bon site. Elle reste silencieuse lorsque l’adresse est légitime et fournit une vérification manuelle lorsque le navigateur ne rend pas sa barre d’adresse accessible.

## Brand Personality

Calme, directe et protectrice. L’interface doit rassurer quand tout va bien et devenir immédiatement explicite lorsqu’une intervention est nécessaire, sans jargon technique ni dramatisation permanente.

## Anti-references

- Les tableaux de bord antivirus surchargés de jauges et de scores incompréhensibles.
- Les interfaces alarmistes qui affichent du rouge ou des notifications lorsque rien de dangereux n’est établi.
- Les verdicts absolus et trompeurs quand l’application ne dispose que d’indices de risque.
- Les réglages techniques indispensables avant de pouvoir bénéficier de la protection de base.

## Design Principles

- **Silencieuse quand tout va bien.** Une adresse officielle ne déclenche aucune interruption.
- **Expliquer l’écart.** Montrer le domaine observé et l’adresse officielle, plutôt qu’un simple message « danger ».
- **Une issue évidente.** Quitter la page et ouvrir le site officiel doivent être les actions principales.
- **Protection locale par défaut.** Aucun historique de navigation ni contenu de page n’est envoyé à un serveur ; le réseau reste limité à la récupération volontaire des mises à jour GitHub.
- **Couverture honnête.** Automatiser partout où Android expose l’adresse et toujours proposer une vérification manuelle de secours.

## Accessibility & Inclusion

Les informations critiques sont communiquées par le texte, l’icône et la couleur conjointement. Les zones tactiles font au moins 48 dp, les contrastes visent 4,5:1 au minimum et le texte respecte l’agrandissement système. L’écran principal et l’alerte doivent être utilisables avec TalkBack, en mode sombre, et sans animation indispensable à la compréhension.
