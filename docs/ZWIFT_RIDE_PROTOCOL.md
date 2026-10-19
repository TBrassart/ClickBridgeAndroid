# Zwift Ride — protocole Bluetooth observé (firmware 1.1.0)

Fiche issue de **captures réelles** d'un Zwift Ride (une capture par unité, mode chiffré). Tout ce qui
est listé comme « observé » a été vu dans les captures ; ce qui est « non vérifié » ne l'a pas été.
Les trames déchiffrées de référence sont dans `tests/fixtures/ride_frames.txt`, le décodeur Python
dans `docs/zwift_ride_decode.py`.

## 1. Deux unités, deux connexions

Le Ride expose **deux périphériques BLE indépendants**, tous deux nommés `Zwift Ride`. Il faut se
connecter aux deux (chacun a son propre handshake et sa propre clé).

| Unité  | Données constructeur (société 0x094A = 2378) | Boutons |
|--------|-----------------------------------------------|---------|
| gauche | 1er octet = `0x08` (puis 2 octets de l'adresse) | croix (haut, bas, gauche, droite), levier haut, levier bas |
| droite | 1er octet = `0x07` | A, B, Y, Z, levier haut, levier bas |

(Rappel : le Zwift Click V1 a `0x09`.) Le nom étant identique, c'est cet octet qui distingue les unités.

## 2. GATT (observé)

Service `00000001-19ca-4651-86e5-fa29dcdd09d1`, comme le Click :

| Caractéristique | Propriétés | Rôle |
|---|---|---|
| `...0002...` | notify | messages (boutons, batterie…) |
| `...0003...` | write sans réponse / write | handshake (écriture) |
| `...0004...` | read, indicate | réponse du handshake (**indication de 72 octets : MTU ≥ 75 nécessaire**) |
| `...0006...` | read, write, indicate | non utilisée |

Informations standard : firmware `1.1.0`, matériel `B.0`.

## 3. Handshake (identique au Click V1 chiffré)

1. S'abonner à `0002` (notify) et `0004` (indicate).
2. Écrire sur `0003` : `"RideOn"` + `01 02` + clé publique P-256 du client (64 octets : X‖Y, sans le préfixe `04`).
3. Le Ride répond sur `0004` : `"RideOn"` + `02 01` + sa clé publique (64 octets) → **72 octets**. (Le Click V1 répond `01 03` : ne pas contrôler ces deux octets.)
4. Secret = ECDH P-256. `HKDF-SHA256(secret, longueur 36, sel = clé_appareil(64) ‖ clé_client(64), info = vide)`.
   Clé AES = 32 premiers octets, `iv` = 4 derniers octets.

## 4. Trames sur `0002`

`compteur (4 octets, little-endian) ‖ données chiffrées ‖ tag (4 octets)`, AES-CCM, tag de 4 octets,
nonce de 8 octets = `iv (4) ‖ compteur (4)`, pas de données associées.

* Les deux premières trames (compteurs 0 et 1) arrivent **avant** la réponse du handshake : elles ne
  peuvent pas être déchiffrées à ce moment-là, on les ignore.
* Ne pas se fier au 2ᵉ octet pour distinguer chiffré/clair (le compteur dépasse 255) : toute trame d'au moins 9 octets est chiffrée.
* Aucun « keepalive » écrit par le client n'a été nécessaire pendant les ~90 s de capture.

## 5. Messages (déchiffrés : 1 octet de type + protobuf)

| Type | Contenu | Remarque |
|---|---|---|
| `0x15` | vide | ~1 par seconde, au repos |
| `0x19` | `{2: pourcentage}` | batterie (ex. 86 %) |
| `0x23` | boutons (voir ci-dessous) | émis jusqu'à ~15 fois/s pendant et juste après une activité |
| `0x2a` | `{1: 3, 2: octets}` | vu une fois par session, signification inconnue |

### Message `0x23`

* **Champ 1** (varint, 32 bits) : masque des boutons. `0xFFFFFFFF` au repos ; **un bit à 0 = bouton enfoncé**.
* **Champ 3** (répété) : paddles analogiques `{1: emplacement, 2: valeur}`, valeur de 0 à 200. L'unité gauche utilise l'emplacement 0, la droite l'emplacement 1 (les leviers orange).

| Bit | Bouton | Unité |
|---|---|---|
| `0x0001` | croix gauche | gauche |
| `0x0002` | croix haut | gauche |
| `0x0004` | croix droite | gauche |
| `0x0008` | croix bas | gauche |
| `0x0100` | levier haut | gauche |
| `0x0200` | levier bas | gauche |
| `0x0010` | A | droite |
| `0x0020` | B | droite |
| `0x0040` | Y | droite |
| `0x0080` | Z | droite |
| `0x1000` | levier haut | droite |
| `0x2000` | levier bas | droite |

**Non vérifié** : bits `0x0400`, `0x0800`, `0x4000`, `0x8000` (probablement marche/arrêt et « power-up »), non capturés.
Le levier « bas » déplace aussi légèrement l'analogique (valeurs 20 à 32) en même temps que son bit.

## 6. Événements normalisés proposés

`LEFT, UP, RIGHT, DOWN, A, B, Y, Z, SHIFT_UP_L, SHIFT_DN_L, SHIFT_UP_R, SHIFT_DN_R` : un événement
« enfoncé » quand le bit passe à 0, « relâché » quand il repasse à 1, par unité.

## 7. Limites de ces captures

Une seule unité de chaque côté, firmware 1.1.0, une session de ~90 s par unité ; pas de test de longue durée, de
reconnexion ni d'appuis simultanés sur plusieurs boutons.
