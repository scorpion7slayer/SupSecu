# SupSécu

SupSécu est une application Android native qui avertit lorsqu’une page semble usurper une enseigne connue tout en utilisant un autre nom de domaine.

## Fonctionnement

1. L’utilisateur active explicitement le service « Protection des adresses web » dans les réglages d’accessibilité Android.
2. SupSécu cherche une barre d’adresse exposée par le navigateur, sans limiter la détection à une liste fermée d’applications.
3. Le domaine est normalisé en ASCII/IDN puis comparé sur une frontière DNS exacte. `lidl.be.evil.com` n’est donc jamais accepté comme sous-domaine de `lidl.be`.
4. Le domaine est aussi comparé à une base locale alimentée chaque jour par plusieurs listes publiques de sécurité.
5. Une page n’est accusée d’usurpation de marque que si elle revendique fortement cette marque : signalement confirmé, logo accessible, logo Lidl reconnu localement sur une page transactionnelle, formulaire de connexion marqué, titre et action de marque, ou identité cohérente dans plusieurs zones.
6. Une usurpation probable ou un domaine publiquement signalé déclenche une notification et une alerte plein écran. Lorsqu’une marque est connue, l’adresse officielle est affichée.

Un domaine seulement ressemblant, comme `amaz0n.com`, produit une notification prudente. Un domaine inconnu sans preuve de marque reste silencieux : SupSécu ne présente pas une intuition comme une certitude.

## Exemple Lidl

- Adresse frauduleuse fournie pour le scénario de test : `https://xcoemruf.shop/products/inventor-mobiele-airco-chilly-dc690`
- Adresse officielle utilisée comme source de vérité : `https://www.lidl.be/`

`xcoemruf.shop` est enregistré comme signalement frauduleux Lidl confirmé dans l’application. Il déclenche donc l’alerte et propose `https://www.lidl.be/` même si ni « Lidl » ni un nom ressemblant n’apparaît dans l’adresse. Les sous-domaines de ce domaine sont également couverts, contrairement à `xcoemruf.shop.example.com`.

Pour les nouvelles fraudes sans nom de marque dans l’URL, SupSécu combine les listes publiques, les indices d’accessibilité et une reconnaissance visuelle locale et conservatrice du logo Lidl sur les pages transactionnelles. La capture utilisée pour cette reconnaissance est immédiatement supprimée et n’est jamais transmise.

## Compatibilité navigateur

La détection est générique et ajoute des indices spécifiques pour Chrome, Firefox, Edge, Brave, Samsung Internet, DuckDuckGo, Opera, Vivaldi et les navigateurs Chromium. Elle fonctionne également avec un autre navigateur lorsqu’il expose une barre d’adresse identifiable et son contenu web au service d’accessibilité.

Android ne permet toutefois pas de garantir la lecture dans un navigateur qui masque volontairement ces éléments. Le repli universel est **Partager → SupSécu**, ou le champ « Vérifier une adresse » dans l’application.

## Marques protégées

Le registre local contient plus de 40 enseignes et services, notamment Lidl, Amazon, Apple Store, Samsung, Microsoft Store, Google Store, eBay, Zalando, AliExpress, Temu, SHEIN, Etsy, IKEA, Nike, adidas, H&M, Zara, UNIQLO, Decathlon, MediaMarkt, Fnac, Vanden Borre, Krëfel, Cdiscount, Darty, ALDI, Delhaize, Colruyt, Action, Walmart, Best Buy, Costco, Rakuten, bpost, itsme, PayPal et les principales banques belges.

Chaque domaine officiel est déclaré explicitement ; aucun joker tel que `amazon.<n’importe quel suffixe>` n’est accepté. Amazon couvre actuellement 21 places de marché officielles, dont `amazon.com`, `amazon.com.be`, `amazon.fr`, `amazon.de`, `amazon.nl`, `amazon.co.uk`, `amazon.co.jp` et `amazon.com.au`. Attention : `amazon.com.fr` n’est pas le domaine français officiel et reste donc signalé.

Les exceptions officielles hébergées sur un autre domaine, comme `track.bpost.cloud`, sont autorisées hôte par hôte sans élargir la confiance à la plateforme parente.

## Confidentialité

- aucune URL visitée, capture ni contenu de page envoyé sur Internet ;
- aucun compte et aucun serveur SupSécu ;
- aucune conservation de l’historique ou du contenu des pages ;
- analyse en mémoire sur l’appareil ;
- téléchargement quotidien de listes complètes de domaines dangereux, sans leur communiquer l’adresse visitée ;
- contenu des notifications masqué sur l’écran verrouillé.

La permission Internet sert uniquement à récupérer les versions publiques de SupSécu et les listes publiques de domaines dangereux. Aucun jeton GitHub et aucun compte ne sont nécessaires.

Les données de réputation viennent de [Phishing.Database](https://github.com/Phishing-Database/Phishing.Database), de la [liste d’avertissements CERT Polska](https://cert.pl/lista-ostrzezen/) et de [PhishDestroy](https://phishdestroy.io/dataset). Une source temporairement indisponible n’empêche pas les autres d’actualiser la base locale. Les détails d’attribution figurent dans [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Le détail se trouve dans [PRIVACY.md](PRIVACY.md).

## Construire

Pré-requis : JDK 17, Android SDK 36 et Build Tools 36.0.0.

```sh
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
./gradlew test lint assembleDebug
```

L’APK de développement est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

## Mise à jour hors Play Store

SupSécu consulte anonymement la dernière GitHub Release du dépôt public `scorpion7slayer/SupSecu`, au maximum une fois toutes les six heures. Lorsqu’une nouvelle version existe :

1. l’utilisateur confirme le téléchargement dans SupSécu ;
2. le manifeste et l’APK sont récupérés exclusivement en HTTPS ;
3. l’empreinte SHA-256 est comparée ;
4. le nom de paquet et le certificat de signature sont comparés à l’application installée ;
5. l’installateur système Android demande la confirmation finale.

Android impose une autorisation unique « Autoriser depuis cette source » pour les applications distribuées hors Play Store. SupSécu ne peut et ne cherche pas à contourner cette confirmation.

Pour publier une future version depuis le Mac de publication, augmenter `versionCode` et `versionName`, puis lancer :

```sh
./scripts/publish-release.sh v1.2.0
```

Le script exécute les tests et Android Lint, construit l’APK signé, génère le manifeste SHA-256 et crée la GitHub Release.
La clé privée reste dans `~/.config/supsecu/supsecu-release-v2.jks` et son mot de passe dans le Trousseau macOS sous `be.supsecu.app.release-keystore-v2`. `scripts/load-release-signing.sh` charge automatiquement les variables `SUPSECU_*` sans exposer le secret dans le dépôt.

## Premier lancement

1. Installer l’APK signé `SupSecu.apk` et ouvrir SupSécu.
2. Appuyer sur « Activer la protection » et lire la divulgation d’accès.
3. Activer « Protection des adresses web » dans Accessibilité.
4. Autoriser les notifications sur Android 13 ou supérieur.
5. Tester d’abord `https://www.lidl.be/`, puis partager l’URL d’exemple vers SupSécu en choisissant Lidl dans le vérificateur manuel.
6. Utiliser « Rechercher une mise à jour » pour vérifier l’accès anonyme à la dernière version publique.

## Commentaires et signalements

« Envoyer un commentaire » prépare un e-mail au contact du projet avec la version de SupSécu et les informations Android utiles. Aucun compte GitHub n’est requis et rien n’est envoyé avant la confirmation dans l’application e-mail. Si aucune application e-mail n’est disponible, SupSécu ouvre le partage Android. Depuis un résultat, « Partager ce signalement » ajoute l’URL et le verdict uniquement à la demande explicite de l’utilisateur.

## Limites de sécurité

SupSécu est une première couche locale, pas une garantie absolue. Une fraude très récente absente des listes, un navigateur qui cache son adresse, une marque absente du registre ou une page graphique dont le logo n’est pas reconnu peuvent rester indétectables. Les listes sont des signalements externes et peuvent exceptionnellement contenir une erreur ; l’interface présente donc ce résultat comme un signalement et non comme une certitude judiciaire.

La publication sur Google Play nécessitera la déclaration d’usage de l’API AccessibilityService et la divulgation visible déjà intégrée à l’écran d’activation.
