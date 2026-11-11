package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Senha opcional para abrir o app (4 números). Guarda só um resumo (PBKDF2 com SHA-1,
 * porque o Android 7.1 não tem SHA-256 nesse método). Depois de 5 erros, espera cada vez mais.
 */
final class AppLock {

    private static final String PREFS = "asc_applock";
    private static final int ITERATIONS = 20000;

    private AppLock() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static boolean enable(Context c, String pin) {
        if (!ParentalStore.isValidPin(pin)) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] h = hash(pin, salt);
        if (h == null) {
            return false;
        }
        prefs(c).edit().putBoolean("on", true)
                .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("hash", Base64.encodeToString(h, Base64.NO_WRAP))
                .putInt("fails", 0).putLong("until", 0L).apply();
        return true;
    }

    static void disable(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Segundos de espera que faltam (0 = pode tentar). */
    static long lockedSeconds(Context c) {
        long until = prefs(c).getLong("until", 0L);
        long now = System.currentTimeMillis();
        return until > now ? (until - now + 999L) / 1000L : 0L;
    }

    static boolean check(Context c, String pin) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("on", false)) {
            return true;
        }
        if (lockedSeconds(c) > 0) {
            return false;
        }
        boolean ok = false;
        try {
            byte[] expected = Base64.decode(p.getString("hash", ""), Base64.NO_WRAP);
            byte[] actual = hash(pin, Base64.decode(p.getString("salt", ""), Base64.NO_WRAP));
            ok = actual != null && MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            ok = false;
        }
        SharedPreferences.Editor ed = p.edit();
        if (ok) {
            ed.putInt("fails", 0).putLong("until", 0L);
        } else {
            int fails = p.getInt("fails", 0) + 1;
            ed.putInt("fails", fails);
            if (fails >= 5) {
                long wait = 30L << Math.min(fails - 5, 7);
                wait = Math.min(wait, 3600L);
                ed.putLong("until", System.currentTimeMillis() + wait * 1000L);
            }
        }
        ed.apply();
        return ok;
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return f.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 160))
                    .getEncoded();
        } catch (Exception e) {
            return null;
        }
    }
}
