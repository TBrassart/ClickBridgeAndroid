"""Décodage des messages du Zwift Ride (firmware 1.1.0) — référence pour l'outil PC.

Entrée : le message DÉCHIFFRÉ (premier octet = type, le reste = protobuf).
Le déchiffrement (ECDH P-256 + HKDF-SHA256 + AES-CCM) est identique à celui du Zwift Click V1 :
voir ZWIFT_RIDE_PROTOCOL.md.

Usage :
    from zwift_ride_decode import decode
    ev = decode(plaintext)       # {'type': 'buttons', 'pressed': {'SHIFT_UP_R'}, 'analog': {1: 199}} ...
"""

TYPE_KEEPALIVE = 0x15
TYPE_BATTERY = 0x19
TYPE_BUTTONS = 0x23

# Bit à 0 dans le masque = bouton enfoncé. Chaque unité n'envoie que ses propres boutons.
BUTTON_BITS = {
    0x0001: "LEFT",  0x0002: "UP",  0x0004: "RIGHT", 0x0008: "DOWN",       # unité gauche : croix
    0x0100: "SHIFT_UP_L", 0x0200: "SHIFT_DN_L",                              # unité gauche : leviers
    0x0010: "A", 0x0020: "B", 0x0040: "Y", 0x0080: "Z",                      # unité droite
    0x1000: "SHIFT_UP_R", 0x2000: "SHIFT_DN_R",                              # unité droite : leviers
}


def _varint(buf, i):
    value = shift = 0
    while True:
        b = buf[i]
        i += 1
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, i
        shift += 7


def parse_protobuf(buf):
    """{champ: [valeurs]} ; les entiers sont des int, les blocs des bytes."""
    out, i = {}, 0
    while i < len(buf):
        tag, i = _varint(buf, i)
        field, wire = tag >> 3, tag & 7
        if wire == 0:
            v, i = _varint(buf, i)
        elif wire == 2:
            n, i = _varint(buf, i)
            v = bytes(buf[i:i + n])
            i += n
        elif wire == 5:
            v = bytes(buf[i:i + 4]); i += 4
        elif wire == 1:
            v = bytes(buf[i:i + 8]); i += 8
        else:
            break
        out.setdefault(field, []).append(v)
    return out


def pressed_buttons(mask):
    return {name for bit, name in BUTTON_BITS.items() if not mask & bit}


def decode(plaintext):
    t = plaintext[0]
    fields = parse_protobuf(plaintext[1:])
    if t == TYPE_BUTTONS:
        mask = fields.get(1, [0xFFFFFFFF])[0]
        analog = {}
        for block in fields.get(3, []):            # paddles analogiques : {1: emplacement, 2: valeur 0..200}
            sub = parse_protobuf(block)
            analog[sub.get(1, [0])[0]] = sub.get(2, [0])[0]
        return {"type": "buttons", "mask": mask, "pressed": pressed_buttons(mask), "analog": analog}
    if t == TYPE_BATTERY:
        return {"type": "battery", "percent": fields.get(2, [None])[0]}
    if t == TYPE_KEEPALIVE:
        return {"type": "keepalive"}
    return {"type": "unknown", "id": t, "fields": fields}


if __name__ == "__main__":
    import sys
    # Vérification sur les trames réelles : python zwift_ride_decode.py ../tests/fixtures/ride_frames.txt
    seen = {}
    for line in open(sys.argv[1], encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        unit, marker, hexa = line.strip().split("|")
        ev = decode(bytes.fromhex(hexa))
        if ev["type"] == "buttons":
            seen.setdefault((unit, marker), set()).update(ev["pressed"])
    for key, buttons in seen.items():
        print(f"{key[0]:7} {key[1]:32} -> {sorted(buttons)}")
