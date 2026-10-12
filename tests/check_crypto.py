"""Vérifie la cryptographie Java (ClickCrypto) contre la bibliothèque Python `cryptography`.
Usage : python3 tests/check_crypto.py <dossier des classes compilées>"""
import os, subprocess, sys
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.ec import ECDH, SECP256R1, EllipticCurvePublicKey, generate_private_key
from cryptography.hazmat.primitives.ciphers.aead import AESCCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

proc = subprocess.Popen(["java", "-cp", sys.argv[1], "app.clickbridge.CryptoTest"],
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
def ask(line):
    proc.stdin.write(line + "\n"); proc.stdin.flush()
    return proc.stdout.readline().strip()

hello = bytes.fromhex(proc.stdout.readline().split()[1])
assert hello[:8] == b"RideOn\x01\x02" and len(hello) == 72, "hello invalide"
app64 = hello[8:]

dev = generate_private_key(SECP256R1())
dev64 = dev.public_key().public_bytes(Encoding.X962, PublicFormat.UncompressedPoint)[1:]
assert ask("DEV " + (b"RideOn\x01\x03" + dev64).hex()) == "ACCEPT true"
secret = dev.exchange(ECDH(), EllipticCurvePublicKey.from_encoded_point(SECP256R1(), b"\x04" + app64))
okm = HKDF(algorithm=hashes.SHA256(), length=36, salt=dev64 + app64, info=b"").derive(secret)
key, iv = okm[:32], okm[32:]

bad = 0; n = 0
sizes = [1, 3, 5, 7, 15, 16, 17, 31, 32, 33, 64]
for counter in [0, 1, 255, 256, 300, 65535, 70000, 2**24 + 5, 2**31]:
    for size in sizes:
        pt = os.urandom(size)
        c = counter.to_bytes(4, "little")
        frame = c + AESCCM(key, tag_length=4).encrypt(iv + c, pt, None)
        r = ask(f"FRAME {frame.hex()} {pt.hex()}"); n += 1
        if r != "OK": bad += 1; print("ÉCHEC", counter, size, r)
# trames réelles du Click (clair connu) : compteur + 5 octets chiffrés + tag
for plain in ["3708001001", "3708011000", "19105d"]:
    c = (7).to_bytes(4, "little")
    frame = c + AESCCM(key, tag_length=4).encrypt(iv + c, bytes.fromhex(plain), None)
    r = ask(f"FRAME {frame.hex()} {plain}"); n += 1
    if r != "OK": bad += 1; print("ÉCHEC trame réelle", plain, r)
# altérations : chaque bit modifié doit être rejeté
c = (9).to_bytes(4, "little")
frame = bytearray(c + AESCCM(key, tag_length=4).encrypt(iv + c, bytes.fromhex("3708001001"), None))
for i in range(len(frame)):
    t = bytearray(frame); t[i] ^= 0x01
    r = ask(f"BAD {bytes(t).hex()}"); n += 1
    if r != "REJECTED": bad += 1; print("trame altérée acceptée à l'octet", i)
ask("QUIT")
print(f"{n} vérifications, {bad} échec(s)")
sys.exit(1 if bad else 0)
