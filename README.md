# SupSécu

SupSécu est une application Android native qui avertit lorsqu’une page semble usurper une enseigne connue tout en utilisant un autre nom de domaine.

## Fonctionnement

1. L’utilisateur active explicitement le service « Protection des adresses web » dans les réglages d’accessibilité Android.
2. SupSécu cherche une barre d’adresse exposée par le navigateur, sans limiter la détection à une liste fermée d’applications.
3. Le domaine est normalisé en ASCII/IDN puis comparé sur une frontière DNS exacte. `lidl.be.evil.com` n’est donc jamais accepté comme sous-domaine de `lidl.be`.
4. Une page n’est accusée d’usurpation que si elle revendique fortement une marque : logo accessible et action transactionnelle, formulaire de connexion marqué, titre et action de marque, ou identité cohérente dans plusieurs zones.
5. Une usurpation probable déclenche une notification et une alerte plein écran indiquant le domaine observé et l’adresse officielle.

Un domaine seulement ressemblant, comme `amaz0n.com`, produit une notification prudente. Un domaine inconnu sans preuve de marque reste silencieux : SupSécu ne présente pas une intuition comme une certitude.

## Exemple Lidl

- Adresse frauduleuse fournie pour le scénario de test : `https://xcoemruf.shop/products/inventor-mobiele-airco-chilly-dc690`
- Adresse officielle utilisée comme source de vérité : `https://www.lidl.be/`

Le premier lien déclenche l’alerte automatique si la page expose une identité Lidl suffisamment forte à l’accessibilité Android. Dans le vérificateur manuel, choisir « Lidl » permet de confirmer explicitement la marque revendiquée.

## Compatibilité navigateur

La détection est générique et ajoute des indices spécifiques pour Chrome, Firefox, Edge, Brave, Samsung Internet, DuckDuckGo, Opera, Vivaldi et les navigateurs Chromium. Elle fonctionne également avec un autre navigateur lorsqu’il expose une barre d’adresse identifiable et son contenu web au service d’accessibilité.

Android ne permet toutefois pas de garantir la lecture dans un navigateur qui masque volontairement ces éléments. Le repli universel est **Partager → SupSécu**, ou le champ « Vérifier une adresse » dans l’application.

## Marques protégées

Le registre local contient plus de 40 enseignes et services, notamment Lidl, Amazon, Apple Store, Samsung, Microsoft Store, Google Store, eBay, Zalando, AliExpress, Temu, SHEIN, Etsy, IKEA, Nike, adidas, H&M, Zara, UNIQLO, Decathlon, MediaMarkt, Fnac, Vanden Borre, Krëfel, Cdiscount, Darty, ALDI, Delhaize, Colruyt, Action, Walmart, Best Buy, Costco, Rakuten, bpost, itsme, PayPal et les principales banques belges.

Chaque domaine officiel est déclaré explicitement ; aucun joker tel que `amazon.<n’importe quel suffixe>` n’est accepté. Amazon couvre actuellement 21 places de marché officielles, dont `amazon.com`, `amazon.com.be`, `amazon.fr`, `amazon.de`, `amazon.nl`, `amazon.co.uk`, `amazon.co.jp` et `amazon.com.au`. Attention : `amazon.com.fr` n’est pas le domaine français officiel et reste donc signalé.

Les exceptions officielles hébergées sur un autre domaine, comme `track.bpost.cloud`, sont autorisées hôte par hôte sans élargir la confiance à la plateforme parente.

## Confidentialité

- aucune URL visitée ni aucun contenu de page envoyé sur Internet ;
- aucun compte et aucun serveur SupSécu ;
- aucune conservation de l’historique ou du contenu des pages ;
- analyse en mémoire sur l’appareil ;
- contenu des notifications masqué sur l’écran verrouillé.

La permission Internet sert uniquement au module de mise à jour GitHub. Pour le dépôt privé, l’utilisateur enregistre une fois un jeton GitHub en lecture seule. Il est chiffré avec Android Keystore et envoyé uniquement à `api.github.com` lors d’une vérification ou d’un téléchargement.

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

SupSécu consulte la dernière GitHub Release du dépôt privé `scorpion7slayer/SupSecu`, au maximum une fois toutes les six heures et seulement après l’enregistrement d’un accès GitHub. Lorsqu’une nouvelle version existe :

1. l’utilisateur confirme le téléchargement dans SupSécu ;
2. le manifeste et l’APK sont récupérés exclusivement en HTTPS ;
3. l’empreinte SHA-256 est comparée ;
4. le nom de paquet et le certificat de signature sont comparés à l’application installée ;
5. l’installateur système Android demande la confirmation finale.

Android impose une autorisation unique « Autoriser depuis cette source » pour les applications distribuées hors Play Store. SupSécu ne peut et ne cherche pas à contourner cette confirmation.

Pour publier une future version, augmenter `versionCode` et `versionName`, exporter les quatre variables de signature `SUPSECU_*`, puis lancer :

```sh
./scripts/publish-release.sh v1.2.0
```

Le script exécute les tests et Android Lint, construit l’APK signé, génère le manifeste SHA-256 et crée la GitHub Release.

## Premier lancement

1. Installer l’APK signé `SupSecu.apk` et ouvrir SupSécu.
2. Appuyer sur « Activer la protection » et lire la divulgation d’accès.
3. Activer « Protection des adresses web » dans Accessibilité.
4. Autoriser les notifications sur Android 13 ou supérieur.
5. Tester d’abord `https://www.lidl.be/`, puis partager l’URL d’exemple vers SupSécu en choisissant Lidl dans le vérificateur manuel.
6. Pour les mises à jour privées, créer un jeton GitHub à accès fin limité à ce dépôt avec la permission de lecture du contenu, puis l’enregistrer dans la section « Mises à jour sécurisées ».

## Limites de sécurité

SupSécu est une première couche locale, pas un moteur mondial de réputation. Une page entièrement graphique, un navigateur qui cache son adresse, une marque absente du registre ou une fraude sans identité reconnaissable peuvent rester indétectables. L’application n’envoie pas les URL vers un service distant, ce qui protège la vie privée mais empêche aussi la vérification de l’ancienneté d’un domaine ou de listes de menaces en temps réel.

La publication sur Google Play nécessitera la déclaration d’usage de l’API AccessibilityService et la divulgation visible déjà intégrée à l’écran d’activation.
