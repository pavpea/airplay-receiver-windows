package io.github.qiuspace.airplay.lib.internal;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class FairPlayVideoDecryptor {

    private final byte[] aesKey;
    private final byte[] sharedSecret;
    private final String streamConnectionID;

    private final Cipher aesCtrDecrypt;
    private final byte[] og = new byte[16];
    private final ByteBuffer ogInput = ByteBuffer.wrap(og);
    private final ByteBuffer ogOutput = ogInput.duplicate();

    private int nextDecryptCount;

    public FairPlayVideoDecryptor(byte[] aesKey, byte[] sharedSecret, String streamConnectionID) throws Exception {
        this.aesKey = aesKey;
        this.sharedSecret = sharedSecret;
        this.streamConnectionID = streamConnectionID;

        aesCtrDecrypt = Cipher.getInstance("AES/CTR/NoPadding");

        initAesCtrCipher();
    }

    public void decrypt(byte[] video) throws Exception {
        decrypt(ByteBuffer.wrap(video));
    }

    /**
     * Decrypts the remaining bytes of {@code video} in-place.  The input and
     * output views deliberately use duplicates because JCE rejects the exact
     * same ByteBuffer object even though overlapping storage is supported.
     */
    public void decrypt(ByteBuffer video) throws Exception {
        int length = video.remaining();
        int base = video.position();
        int pending = nextDecryptCount;
        int consumedPending = Math.min(pending, length);
        if (consumedPending > 0) {
            for (int i = 0; i < consumedPending; i++) {
                int position = base + i;
                video.put(position, (byte) (video.get(position) ^ og[(16 - nextDecryptCount) + i]));
            }
            if (consumedPending < pending) {
                nextDecryptCount = pending - consumedPending;
                return;
            }
        }

        int encryptlen = ((length - consumedPending) / 16) * 16;
        if (encryptlen > 0) {
            ByteBuffer input = video.duplicate();
            input.position(base + consumedPending);
            input.limit(base + consumedPending + encryptlen);
            ByteBuffer output = video.duplicate();
            output.position(base + consumedPending);
            int written = aesCtrDecrypt.update(input, output);
            if (written != encryptlen) {
                throw new IllegalStateException("Unexpected FairPlay AES output length: " + written);
            }
        }

        int restlen = (length - consumedPending) % 16;
        int reststart = length - restlen;
        nextDecryptCount = 0;
        if (restlen > 0) {
            Arrays.fill(og, (byte) 0);
            for (int i = 0; i < restlen; i++) {
                og[i] = video.get(base + reststart + i);
            }
            ogInput.clear();
            ogOutput.clear();
            int written = aesCtrDecrypt.update(ogInput, ogOutput);
            if (written != 16) {
                throw new IllegalStateException("Unexpected FairPlay tail output length: " + written);
            }
            for (int i = 0; i < restlen; i++) {
                video.put(base + reststart + i, og[i]);
            }
            nextDecryptCount = 16 - restlen;
        }
    }

    private void initAesCtrCipher() throws Exception {
        MessageDigest sha512Digest = MessageDigest.getInstance("SHA-512");
        sha512Digest.update(aesKey);
        sha512Digest.update(sharedSecret);
        byte[] eaesKey = sha512Digest.digest();

        byte[] skey = ("AirPlayStreamKey" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(skey);
        sha512Digest.update(eaesKey, 0, 16);
        byte[] hash1 = sha512Digest.digest();

        byte[] siv = ("AirPlayStreamIV" + streamConnectionID).getBytes(StandardCharsets.UTF_8);
        sha512Digest.update(siv);
        sha512Digest.update(eaesKey, 0, 16);
        byte[] hash2 = sha512Digest.digest();

        byte[] decryptAesKey = new byte[16];
        byte[] decryptAesIV = new byte[16];
        System.arraycopy(hash1, 0, decryptAesKey, 0, 16);
        System.arraycopy(hash2, 0, decryptAesIV, 0, 16);

        aesCtrDecrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptAesKey, "AES"), new IvParameterSpec(decryptAesIV));
    }
}
