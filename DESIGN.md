---
name: SupSécu
description: Protection locale et lisible contre les domaines frauduleux sur Android.
colors:
  primary: "oklch(0.480 0.105 170)"
  primary-tint: "oklch(0.930 0.035 170)"
  background: "oklch(1.000 0.000 0)"
  surface: "oklch(0.965 0.006 170)"
  ink: "oklch(0.220 0.025 170)"
  muted: "oklch(0.480 0.035 170)"
  outline: "oklch(0.820 0.015 170)"
  danger: "oklch(0.480 0.180 28)"
  danger-surface: "oklch(0.960 0.025 28)"
  success: "oklch(0.460 0.110 155)"
  success-surface: "oklch(0.950 0.030 155)"
typography:
  display:
    fontFamily: "Roboto, system sans-serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: 1.08
  title:
    fontFamily: "Roboto, system sans-serif"
    fontSize: "20sp"
    fontWeight: 500
  body:
    fontFamily: "Roboto, system sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: 1.18
  label:
    fontFamily: "Roboto, system sans-serif"
    fontSize: "14sp"
    fontWeight: 500
rounded:
  compact: "8dp"
  control: "12dp"
  panel: "16dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  section: "32dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.background}"
    rounded: "{rounded.control}"
    padding: "14dp 20dp"
    height: "52dp"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.background}"
    rounded: "{rounded.control}"
    padding: "14dp 20dp"
    height: "52dp"
  button-secondary:
    backgroundColor: "{colors.background}"
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    padding: "14dp 20dp"
    height: "52dp"
  input:
    backgroundColor: "{colors.background}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10dp 14dp"
    height: "56dp"
  panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "18dp"
---

# Design System: SupSécu

## Overview

**Creative North Star: "Le garde-fou de poche"**

SupSécu est utilisé au moment précis où une offre semble trop belle, souvent sur un téléphone tenu d’une main et sous une lumière imparfaite. L’application reste blanche, nette et calme pendant la configuration ; seule une preuve de risque transforme l’écran en avertissement explicite.

L’interface doit disparaître derrière la décision à prendre. Elle refuse les tableaux de bord antivirus surchargés, les scores opaques et la dramatisation permanente. Le domaine observé et l’adresse officielle sont toujours plus importants que la décoration.

**Key Characteristics:**

- Hiérarchie courte et immédiatement scannable.
- Actions tactiles de 48 dp minimum.
- Teal protecteur pour l’action normale ; rouge réservé au risque établi.
- Retour d’état textuel, iconographique et coloré.
- Mode sombre structurellement identique au mode clair.

## Colors

La stratégie est restreinte : fond neutre, surfaces à peine teintées vers le teal et couleurs sémantiques rares.

### Primary

- **Teal bouclier** : activation de la protection, liens sûrs et actions principales hors alerte.
- **Brume teal** : surfaces d’état positif, retours tactiles et enveloppe de l’icône.

### Secondary

- **Rouge d’intervention** : uniquement pour une adresse non officielle ou une action qui quitte le site dangereux.
- **Vert de confirmation** : uniquement pour confirmer une correspondance avec un domaine officiel.

### Neutral

- **Blanc direct** : fond clair, sans teinte chaude artificielle.
- **Encre minérale** : texte principal à fort contraste.
- **Gris-teal lisible** : texte secondaire ; jamais utilisé pour une information critique.

### Named Rules

**The Silent Safety Rule.** Le rouge est interdit lorsque l’analyse ne dispose d’aucune preuve suffisante.

**The Domain Contrast Rule.** Les domaines observé et officiel utilisent simultanément couleur, libellé et position ; la couleur seule ne porte jamais le verdict.

## Typography

**Display Font:** Roboto (police système Android)
**Body Font:** Roboto (police système Android)
**Label/Mono Font:** Roboto Mono ou monospace système pour les domaines

**Character:** Une seule famille familière évite toute étrangeté dans un outil de sécurité. Le monospace est réservé aux adresses, où l’alignement des caractères aide réellement la lecture.

### Hierarchy

- **Display** (700, 30sp, 1.08) : décision principale et alerte plein écran.
- **Headline** (700, 24sp) : titres courts sur petit écran.
- **Title** (500, 20sp) : sections et panneaux.
- **Body** (400, 16sp, 1.18) : explications limitées à quelques lignes.
- **Label** (500, 14sp) : champs, domaines et états.

### Named Rules

**The Plain Language Rule.** Les titres décrivent la situation ; aucun jargon de cybersécurité ne remplace une explication concrète.

## Elevation

Le système est plat par défaut. La profondeur vient des surfaces tonales et de l’ordre vertical, pas de grandes ombres diffuses. L’alerte plein écran remplace la scène entière et n’utilise donc ni carte flottante ni verre translucide.

### Named Rules

**The Flat-By-Default Rule.** Aucune ombre décorative ; un contour fin est réservé aux champs et aux adresses copiables.

## Components

### Buttons

- **Shape:** courbe contenue (12dp), jamais une pilule.
- **Primary:** teal plein, texte blanc, hauteur minimale 52dp.
- **Pressed / Focus:** changement tonal immédiat et focus Android natif ; aucune animation décorative.
- **Danger:** rouge plein, exclusivement pour « Quitter ce site ».
- **Secondary:** fond de l’écran et contour teal de 2dp pour ouvrir l’adresse officielle.

### Cards / Containers

- **Corner Style:** panneaux calmes à 16dp ; résultats compacts à 12dp.
- **Background:** couche tonale sans ombre.
- **Shadow Strategy:** aucune ombre au repos.
- **Border:** aucun contour sur les panneaux ; contour de 1dp seulement sur les domaines et champs.
- **Internal Padding:** 16–18dp.

### Inputs / Fields

- **Style:** fond de page, contour neutre de 1dp, rayon 12dp et texte de 16sp.
- **Focus:** accent système teal et curseur visible.
- **Error / Disabled:** message textuel explicite ; jamais une bordure rouge seule.

### Navigation

Un écran vertical unique. Les réglages système et le navigateur s’ouvrent avec les affordances Android standards ; aucune navigation inventée.

### Full-screen security alert

Icône d’avertissement, verdict, domaine observé et domaine officiel précèdent les actions. « Continuer malgré le risque » exige un second toucher dans une fenêtre courte pour éviter l’erreur accidentelle.

## Do's and Don'ts

### Do:

- **Do** rester silencieux lorsqu’un domaine officiel est reconnu.
- **Do** afficher les noms de domaine en monospace et avec leur libellé complet.
- **Do** conserver des zones tactiles d’au moins 48dp et respecter l’agrandissement système.
- **Do** utiliser le teal pour la protection normale et réserver le rouge à l’intervention.
- **Do** maintenir la même hiérarchie en mode clair et sombre.

### Don't:

- **Don't** construire un « tableau de bord antivirus surchargé de jauges et de scores incompréhensibles ».
- **Don't** créer une « interface alarmiste qui affiche du rouge ou des notifications lorsque rien de dangereux n’est établi ».
- **Don't** annoncer un « verdict absolu et trompeur » à partir d’un simple domaine inconnu.
- **Don't** rendre des réglages techniques indispensables avant la vérification manuelle.
- **Don't** utiliser de glassmorphism, de texte en dégradé, de carte au rayon supérieur à 16dp ou de grande ombre décorative.
