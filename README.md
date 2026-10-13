# ClickBridge pour Android — version 0.3 (nouvelle interface)

Lit le Zwift Click V1 en Bluetooth (handshake chiffré) dans un service de premier plan et touche
l'écran de MyWhoosh à l'endroit des boutons de vitesse (service d'accessibilité).

## Nouveautés 0.3
- Page d'accueil colorée (thème clair / sombre, logo, jauge de batterie, indicateurs + et −).
- **Réglages** dans une page séparée, avec une explication sous chaque option.
- **Assistant de configuration** en 5 étapes (bouton orange bien visible sur l'accueil).
- **Aide** avec des rubriques dépliables (réglage des points, Click qui ne se connecte pas,
  accessibilité refusée, appuis ratés, batterie…).
- Icône de l'application : le logo ClickBridge.

## Mettre à jour le dépôt GitHub
Remplace les fichiers du dépôt par ceux de ce dossier (tout, y compris `.github`, `app/src/main/res`
et le nouveau dossier `app/src/main/assets`), puis ouvre l'onglet **Actions** : le workflow construit
l'APK. Télécharge l'artefact **ClickBridge-apk** et installe `app-debug.apk` par-dessus l'ancienne
version (tes réglages sont conservés).

## Première utilisation
Ouvre l'appli et touche le bouton orange **Assistant de configuration** : il te guide pas à pas.

## En cas de problème
Réglages > Diagnostic > « Copier le journal », puis colle-le dans la conversation.

## Contenu
- `MainActivity`, `SettingsActivity`, `WizardActivity`, `HelpActivity` : les quatre écrans
- `BaseActivity`, `Theme`, `Ui`, `BatteryBar`, `Actions` : thème, outils de dessin, actions communes
- `BridgeService` : service de premier plan (Bluetooth + notification)
- `ClickClient`, `ClickCrypto`, `Pb` : connexion, chiffrement, décodage (vérifiés contre Python dans `tests/`)
- `TapService`, `CalibrationOverlay` : toucher via l'accessibilité, pastilles de réglage
- `Bridge`, `Prefs` : état partagé et réglages
