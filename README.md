# ClickBridge pour Android — version 0.5

Lit le Zwift Click V1 ou le Zwift Ride en Bluetooth (handshake chiffré) dans un service de premier
plan et touche l'écran de l'appli d'entraînement (MyWhoosh, Rouvy, Zwift…) à l'endroit des boutons
(service d'accessibilité).

## Nouveautés 0.5 — Zwift Ride
- **Zwift Ride** pris en charge (les deux unités, gauche et droite, connectées en même temps) :
  croix directionnelle, leviers haut/bas des deux côtés, boutons A, B, Y, Z.
  Les leviers orange (analogiques) et les boutons marche/arrêt ne sont pas pris en charge.
- Choix de l'appareil dans **Réglages > Appareil** (Zwift Click ou Zwift Ride).
- **Profils par bouton** : « Gérer les profils » > « Boutons du profil » choisit les boutons pilotés
  (par défaut : + et − pour le Click, les 4 leviers pour le Ride). Le calque de réglage affiche une
  pastille par bouton ; toucher une pastille la teste.
- L'accueil montre le dernier bouton enfoncé ; les leviers « haut » comptent comme « + » et les
  leviers « bas » comme « − » (indicateurs, répétition, historique).
- Les profils s'exportent au format 2 (les profils de la version 0.4 se relisent sans perte).

## Mettre à jour le dépôt GitHub
Remplace les fichiers du dépôt par ceux de ce dossier (tout, y compris `.github`, `app/src/main/res`,
`app/src/main/assets`, `docs` et `tests`), puis ouvre l'onglet **Actions** : le workflow construit l'APK.
Installe `app-debug.apk` par-dessus l'ancienne version : réglages, profils et historique sont conservés.

## En cas de problème
Réglages > Diagnostic > « Journal détaillé : activé », puis « Copier le journal » et colle-le dans la conversation.

## Contenu
- Écrans : `MainActivity`, `SettingsActivity`, `ProfilesActivity`, `HistoryActivity`, `WizardActivity`, `HelpActivity`
- Interface : `BaseActivity`, `Theme`, `Ui`, `BatteryBar`, `Actions`
- Service : `BridgeService` (manettes, notification, répétition, historique), `ClickClient` (une connexion BLE), `ClickCrypto`, `Pb`
- Manettes : `Buttons` (identifiants communs), `RideProtocol` (masque de boutons du Ride)
- Toucher : `TapService`, `CalibrationOverlay`
- Données : `Profile`, `ProfileBook`, `Profiles`, `SessionBook`, `SessionLog`, `Prefs`, `Bridge`
- `docs/` : fiche de protocole du Ride et décodeur Python (pour l'outil PC)
- `tests/` : cryptographie, profils, historique et décodage du Ride sur les trames réelles
