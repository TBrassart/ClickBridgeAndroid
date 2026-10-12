# ClickBridge pour Android — étape 1 : lecture du Zwift Click

Mini-appli de test : elle se connecte au Zwift Click V1 en Bluetooth, fait l'échange de clés,
déchiffre les trames et affiche les appuis sur **+** et **−** à l'écran, avec un journal.
Elle n'envoie encore aucune touche à MyWhoosh (c'est l'étape 2).

## Obtenir l'APK avec GitHub Actions

1. Sur github.com, crée un dépôt **privé** (par exemple `clickbridge-android`).
2. Envoie-y **tout le contenu** de ce dossier, y compris le dossier caché `.github`
   (« Add file » > « Upload files », puis glisse les fichiers et dossiers).
3. Onglet **Actions** du dépôt : le workflow « Construire l'APK » se lance tout seul
   (sinon : clique dessus, puis « Run workflow »). Compte 3 à 6 minutes.
4. Une fois le travail terminé (coche verte), ouvre-le et télécharge l'artefact **ClickBridge-apk**
   (un .zip qui contient `app-debug.apk`).
5. Sur le Pixel : copie l'APK, ouvre-le, autorise l'installation depuis cette source si Android le demande.
   Si Play Protect affiche un avertissement, choisis « Installer quand même ».

## Premier essai

1. Ferme Zwift, Companion et toute appli qui tient le Click ; sur le PC, ferme ClickBridge.
2. Lance l'appli et accepte la permission « Appareils à proximité ».
3. Réveille le Click (appui sur un bouton, LED qui pulse en bleu).
4. Statut attendu : « Connecté : prêt », puis les gros carrés + et − s'allument à chaque appui.
5. Bouton « Copier le journal » : colle-le dans la conversation si quelque chose ne marche pas.

## Si la compilation échoue

Ouvre le travail en échec dans l'onglet Actions, copie les dernières lignes rouges du journal
et envoie-les.

## Contenu

- `app/src/main/java/app/clickbridge/ClickCrypto.java` : ECDH, HKDF, AES-CCM
- `app/src/main/java/app/clickbridge/ClickClient.java` : Bluetooth (scan, connexion, handshake)
- `app/src/main/java/app/clickbridge/MainActivity.java` : écran
- `tests/` : vérification de la cryptographie contre Python (`check_crypto.py`)
