# ClickBridge pour Android — version 0.4

Lit le Zwift Click V1 en Bluetooth (handshake chiffré) dans un service de premier plan et touche
l'écran de l'appli d'entraînement (MyWhoosh, Rouvy, Zwift…) à l'endroit des boutons de vitesse
(service d'accessibilité).

## Nouveautés 0.4
- **Appui continu** : si le bouton reste enfoncé, le toucher est répété (délai avant la première
  répétition et temps entre répétitions réglables, Réglages > Appui continu).
- **Profils** (Rouvy, MyWhoosh, Zwift…) : positions de boutons séparées, profil actif, profil par défaut,
  changement depuis l'accueil ou la notification (« Profil suivant »). Tes anciens points sont repris
  dans un profil « MyWhoosh ».
- **Export / import des profils** : fichier JSON ou presse-papiers (pratique téléphone ↔ tablette).
- **Historique des sessions** : date, durée, profil, nombre d'appuis, batterie ; export CSV.

## Mettre à jour le dépôt GitHub
Remplace les fichiers du dépôt par ceux de ce dossier (tout, y compris `.github`, `app/src/main/res`
et `app/src/main/assets`), puis ouvre l'onglet **Actions** : le workflow construit l'APK. Installe
`app-debug.apk` par-dessus l'ancienne version : réglages, Click mémorisé et points d'appui sont conservés.

## En cas de problème
Réglages > Diagnostic > « Copier le journal », puis colle-le dans la conversation.

## Contenu
- Écrans : `MainActivity`, `SettingsActivity`, `ProfilesActivity`, `HistoryActivity`, `WizardActivity`, `HelpActivity`
- Interface : `BaseActivity`, `Theme`, `Ui`, `BatteryBar`, `Actions`
- Service : `BridgeService` (Bluetooth, notification, répétition, historique), `ClickClient`, `ClickCrypto`, `Pb`
- Toucher : `TapService`, `CalibrationOverlay`
- Données : `Profile`, `ProfileBook`, `Profiles`, `SessionBook`, `SessionLog`, `Prefs`, `Bridge`
- `tests/` : cryptographie contre Python (`check_crypto.py`), profils et historique sur JVM (`BooksTest.java`)
