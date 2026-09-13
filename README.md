# 🪖 TanksCraft

**TanksCraft** est un mod **NeoForge pour Minecraft 1.21.1** inspiré de *World of Tanks* : le menu principal de Minecraft est remplacé par **le Hangar**, le garage de chars façon WoT, où l'on feuillette sa collection avant de partir au combat.

> Comme BlockFront réinventait Call of Duty dans Minecraft, TanksCraft réinvente World of Tanks : chars pilotables, tourelle qui suit la vue du pilote, obus explosifs… et un garage digne de ce nom.

![Java 21](https://img.shields.io/badge/Java-21-orange) ![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-blue) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

## 🏠 Le Hangar (nouveau menu principal)

Dès le lancement du jeu, le menu titre vanilla disparaît au profit du hangar :

- **Char 3D animé** au centre de la scène, modèle paramétrique (coque, chenilles, tourelle, canon, frein de bouche, cupule) avec texture de camouflage propre à chaque nation ;
- **Rotation à la souris** (glisser) et **zoom molette**, comme dans le garage WoT ;
- **Carrousel de chars** en bas : 10 chars de 5 nations (France, Allemagne, URSS, États-Unis, Royaume-Uni), tiers I à VII, classes LT / MT / HT / TD ;
- **Fiche technique** à gauche : puissance de feu, mobilité, blindage, camouflage, portée de vue, PDV, dégâts, vitesse, rechargement ;
- **Devises** en haut à droite (crédits, or, XP libre) et profil du commandant en haut à gauche ;
- **Grand bouton « AU COMBAT ! »** qui ouvre la sélection de monde, plus Multijoueur, Options, Mods et Quitter ;
- Le char sélectionné est **persisté** dans `config/tankscraft-garage.json`.

## ⚔️ Le gameplay

- **Char pilotable** : déployez-le avec l'objet *Char* (clic droit au sol), montez dedans (clic droit sur le char) ;
- **ZQSD/WASD** pour conduire (pivot sur place, marche arrière), la tourelle **suit votre regard** ;
- **Clic gauche** pour tirer un **obus explosif** (rechargement 3 s, barre de rechargement au-dessus de la barre d'action) ;
- Les chars ont **400 PDV** et **explosent** en flammes à la destruction — y compris char contre char ;
- Les obus détruisent le terrain (explosion type TNT).

### Fabrication

```
┌───────────┐
│ Bloc fer  │
│ Bloc TNT  │   8 blocs de fer + 1 TNT
│ Bloc fer  │
└───────────┘
```

Le char est aussi disponible dans l'onglet créatif **TanksCraft**.

## 📦 Installation

1. Installez **NeoForge 21.1.x** pour Minecraft **1.21.1** (Java 21 requis) ;
2. Placez le jar du mod (voir [les releases](../../releases)) dans le dossier `mods/` ;
3. Lancez le jeu — le hangar remplace le menu principal.

## 🔨 Compilation depuis les sources

```bash
./gradlew build        # le jar arrive dans build/libs/
./gradlew runClient    # lance un client de dev avec le mod
```

Un workflow GitHub Actions (`.github/workflows/build.yml`) compile le mod à chaque push et publie un jar en pré-release `build-latest`.

## 🗂️ Architecture du code

```
src/main/java/com/tankscraft/
├── TanksCraft.java              # point d'entrée @Mod
├── tank/                        # TankDefinition, collection (Tanks)
├── entity/                      # TankEntity (pilotable), ShellEntity (obus)
├── item/                        # TankItem (déploiement)
├── network/                     # paquet de tir FireCannonPayload
├── registry/                    # entités, items, onglet créatif
└── client/
    ├── garage/                  # GarageScreen (menu principal), GarageState
    ├── render/                  # TankModels, TankRenderer, ShellRenderer
    ├── ClientEvents.java        # remplacement du titre, entrées, tir
    ├── ClientModEvents.java     # enregistrement rendus + HUD
    └── TankHudLayer.java        # barre de rechargement
```

Les textures de camouflage sont générées par `tools/gen_textures.py` (aucune dépendance, PNG écrit à la main) ; le layout UV est synchronisé avec `TankModels.java`.

## 🗺️ Feuille de route (idées)

- [ ] Batailles générées automatiquement (arène, équipes, compte de points)
- [ ] Progression : XP, déblocage des chars tier supérieur
- [ ] Dégâts localisés (baissier, chenilles, canon endommagé)
- [ ] Sons de moteur, consumables (extincteur, réparation)
- [ ] Multi-place : tireur + conducteur

## ⚖️ Licence

MIT — voir [LICENSE](LICENSE). Projet fan-made non affilié à Wargaming ni à Mojang.
