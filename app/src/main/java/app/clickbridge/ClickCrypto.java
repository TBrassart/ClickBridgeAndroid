package app.clickbridge;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Échange de clés ECDH (P-256) + HKDF-SHA256 + AES-CCM (tag de 4 octets),
 * comme observé sur le Zwift Click V1 (firmware 1.1.0).
 * AES-CCM est écrit à la main (RFC 3610) car Android ne le fournit pas.
 */
public final class ClickCrypto {
    private static final int TAG = 4;

    private final KeyPair keyPair;
    private final byte[] pub64;
    private byte[] key;
    private byte[] iv;

    public ClickCrypto() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = g.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
        pub64 = concat(fixed32(pub.getW().getAffineX()), fixed32(pub.getW().getAffineY()));
    }

    /** Message de départ : "RideOn" + 01 02 + notre clé publique (64 octets). */
    public byte[] hello() {
        byte[] head = {'R', 'i', 'd', 'e', 'O', 'n', 1, 2};
        return concat(head, pub64);
    }

    /** Traite la réponse de l'appareil (RideOn 01 03 + sa clé publique). */
    public boolean accept(byte[] data) throws GeneralSecurityException {
        if (data.length != 72) return false;
        byte[] rideOn = {'R', 'i', 'd', 'e', 'O', 'n'};
        if (!Arrays.equals(Arrays.copyOfRange(data, 0, 6), rideOn)) return false;

        byte[] dev64 = Arrays.copyOfRange(data, 8, 72);
        ECPublicKey mine = (ECPublicKey) keyPair.getPublic();
        ECPoint w = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(dev64, 0, 32)),
                new BigInteger(1, Arrays.copyOfRange(dev64, 32, 64)));
        PublicKey peer = KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(w, mine.getParams()));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(keyPair.getPrivate());
        ka.doPhase(peer, true);
        byte[] secret = ka.generateSecret();

        byte[] okm = hkdf(secret, concat(dev64, pub64), new byte[0], 36);
        key = Arrays.copyOfRange(okm, 0, 32);
        iv = Arrays.copyOfRange(okm, 32, 36);
        return true;
    }

    public boolean ready() {
        return key != null;
    }

    /** Trame = compteur (4 octets) + données chiffrées + tag (4 octets). */
    public byte[] decrypt(byte[] frame) throws GeneralSecurityException {
        if (key == null) throw new IllegalStateException("clé absente");
        if (frame.length < 4 + 1 + TAG) throw new GeneralSecurityException("trame trop courte");
        byte[] nonce = new byte[8];
        System.arraycopy(iv, 0, nonce, 0, 4);
        System.arraycopy(frame, 0, nonce, 4, 4);
        byte[] body = Arrays.copyOfRange(frame, 4, frame.length - TAG);
        byte[] tag = Arrays.copyOfRange(frame, frame.length - TAG, frame.length);
        return ccmDecrypt(key, nonce, body, tag);
    }

    // ------------------------------------------------------------------

    static byte[] ccmDecrypt(byte[] key, byte[] nonce, byte[] ct, byte[] tag)
            throws GeneralSecurityException {
        Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

        // déchiffrement (mode compteur)
        byte[] pt = new byte[ct.length];
        for (int off = 0, i = 1; off < ct.length; off += 16, i++) {
            byte[] s = aes.doFinal(counterBlock(nonce, i));
            int n = Math.min(16, ct.length - off);
            for (int j = 0; j < n; j++) pt[off + j] = (byte) (ct[off + j] ^ s[j]);
        }
        byte[] s0 = aes.doFinal(counterBlock(nonce, 0));

        // CBC-MAC sur le message en clair (pas de données associées)
        byte[] b0 = new byte[16];
        b0[0] = 0x0E;                                   // M=4, L=8-1=7 -> drapeaux 0x0E
        System.arraycopy(nonce, 0, b0, 1, 8);
        long len = pt.length;
        for (int k = 15; k >= 9; k--) {
            b0[k] = (byte) (len & 0xFF);
            len >>>= 8;
        }
        byte[] x = aes.doFinal(b0);
        for (int off = 0; off < pt.length; off += 16) {
            byte[] blk = new byte[16];
            System.arraycopy(pt, off, blk, 0, Math.min(16, pt.length - off));
            for (int j = 0; j < 16; j++) blk[j] ^= x[j];
            x = aes.doFinal(blk);
        }

        int diff = 0;
        for (int j = 0; j < TAG; j++) diff |= (x[j] ^ s0[j]) ^ tag[j];
        if (diff != 0) throw new AEADBadTagException("tag invalide");
        return pt;
    }

    private static byte[] counterBlock(byte[] nonce, long counter) {
        byte[] a = new byte[16];
        a[0] = 0x06;                                    // L-1
        System.arraycopy(nonce, 0, a, 1, 8);
        for (int k = 15; k >= 9; k--) {
            a[k] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }
        return a;
    }

    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length)
            throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < length; i++) {
            t = hmac(prk, t, info, new byte[]{(byte) i});
            int n = Math.min(t.length, length - pos);
            System.arraycopy(t, 0, okm, pos, n);
            pos += n;
        }
        return okm;
    }

    private static byte[] hmac(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) mac.update(p);
        return mac.doFinal();
    }

    private static byte[] fixed32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int len = Math.min(32, raw.length);
        System.arraycopy(raw, raw.length - len, out, 32 - len, len);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
