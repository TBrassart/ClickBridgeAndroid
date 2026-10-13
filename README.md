# ClickBridge pour Android — étape 2 : appuis tactiles sur MyWhoosh

L'appli lit le Zwift Click V1 (Bluetooth, handshake chiffré) dans un service de premier plan,
et transforme chaque appui sur **+** / **−** en toucher sur l'écran, à l'endroit des boutons de
vitesse de MyWhoosh, grâce à un service d'accessibilité.

## Mettre à jour le dépôt GitHub

Remplace les fichiers du dépôt par ceux de ce dossier (tout, y compris `.github` et `app/src/main/res`),
puis ouvre l'onglet **Actions** : le workflow construit l'APK. Télécharge l'artefact **ClickBridge-apk**
et installe `app-debug.apk` par-dessus l'ancienne version.

## Mise en route sur le téléphone / la tablette (à faire une fois)

1. Ouvre ClickBridge et appuie sur **Démarrer** (autorise « Appareils à proximité » et les notifications).
2. **Active le service d'accessibilité** :
   - appuie sur « Ouvrir les réglages d'accessibilité », puis « ClickBridge (appuis tactiles) » > activer ;
   - si Android refuse (« paramètre restreint ») : bouton « Infos de l'appli », menu ⋮ en haut à droite,
     « Autoriser les paramètres restreints », puis recommence.
3. **Règle les points d'appui** : ouvre MyWhoosh sur l'écran de course, puis (depuis ClickBridge ou
   depuis la notification « Régler les points ») fais apparaître les pastilles. Glisse **+** et **−**
   sur les boutons de vitesse de MyWhoosh, essaie avec « Tester + » / « Tester − », puis « Valider ».
   Les points sont mémorisés **séparément en portrait et en paysage**, et par appareil.
4. Bouton « Autoriser en arrière-plan » : évite qu'Android mette l'appli en veille.
5. Réveille le Click (LED bleue) : statut « Connecté : prêt », puis lance MyWhoosh.

## Si ça ne marche pas

« Afficher le journal » puis « Copier le journal », et colle-le dans la conversation. « Journal
détaillé : oui » ajoute les trames reçues.

## Contenu

- `BridgeService` : service de premier plan (Bluetooth + notification)
- `ClickClient` : scan, connexion, handshake ; adresse mémorisée, reconnexion automatique
- `ClickCrypto`, `Pb` : chiffrement et décodage des trames (vérifiés contre Python dans `tests/`)
- `TapService` : service d'accessibilité (geste de toucher)
- `CalibrationOverlay` : pastilles + et − à glisser
- `MainActivity`, `Bridge`, `Prefs` : écran, état partagé, réglages
