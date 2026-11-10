package com.asclive.tvbox;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Protege textos pequenos (listas de favoritos, chave secreta do aparelho...) com AES-GCM e
 * uma chave do Android Keystore (Android 6 ou mais). Abaixo disso, ou se o Keystore falhar,
 * guarda sem criptografia, sempre no armazenamento privado do app.
 */
final class Secure {

    private static final String KEY_ALIAS = "asc_live_key";
    private static final String PREFIX_ENC = "K:";
    private static final String PREFIX_PLAIN = "P:";

    private Secure() {
    }

    /** Protege o texto para guardar. Nunca devolve null. */
    static String seal(String plain) {
        byte[] data = (plain == null ? "" : plain).getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                return PREFIX_ENC + encrypt(data);
            } catch (Exception e) {
                // Keystore com problema neste aparelho: usa o plano B.
            }
        }
        return PREFIX_PLAIN + Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /** Desfaz o seal. Devolve texto vazio se não for possível ler (chave perdida, dado ruim). */
    static String reveal(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        try {
            if (stored.startsWith(PREFIX_PLAIN)) {
                byte[] d = Base64.decode(stored.substring(PREFIX_PLAIN.length()), Base64.NO_WRAP);
                return new String(d, StandardCharsets.UTF_8);
            }
            if (stored.startsWith(PREFIX_ENC) && Build.VERSION.SDK_INT >= 23) {
                return new String(decrypt(stored.substring(PREFIX_ENC.length())),
                        StandardCharsets.UTF_8);
            }
            // Valor antigo, guardado sem proteção: devolve como está.
            return stored;
        } catch (Exception e) {
            return "";
        }
    }

    @TargetApi(23)
    private static String encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] enc = cipher.doFinal(data);
        byte[] out = new byte[iv.length + enc.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(enc, 0, out, iv.length, enc.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    @TargetApi(23)
    private static byte[] decrypt(String b64) throws Exception {
        byte[] in = Base64.decode(b64, Base64.NO_WRAP);
        if (in.length < 13) {
            throw new IllegalStateException("dado curto");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, in, 0, 12));
        return cipher.doFinal(in, 12, in.length - 12);
    }

    @TargetApi(23)
    private static SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }
}
