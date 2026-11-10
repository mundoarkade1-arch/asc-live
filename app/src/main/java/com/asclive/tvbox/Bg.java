package com.asclive.tvbox;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trabalho de rede e disco SEMPRE fora da thread principal.
 * O resultado só volta se a tarefa não foi cancelada e a tela ainda existe.
 */
final class Bg {

    interface Work<T> {
        T run() throws Exception;
    }

    interface Done<T> {
        void ok(T value);

        void fail(Exception e);
    }

    /** Tarefa que precisa saber se foi cancelada (busca longa). */
    interface WorkH<T> {
        T run(Handle h) throws Exception;
    }

    /** Permite cancelar (e saber se foi cancelado). */
    static final class Handle implements SearchIndex.Cancel {
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(5);

    private Bg() {
    }

    static <T> Handle run(final Activity act, final Work<T> work, final Done<T> done) {
        return runH(act, h -> work.run(), done);
    }

    static <T> Handle runH(final Activity act, final WorkH<T> work, final Done<T> done) {
        final Handle h = new Handle();
        POOL.execute(() -> {
            T value = null;
            Exception error = null;
            try {
                value = work.run(h);
            } catch (Exception e) {
                error = e;
            }
            final T v = value;
            final Exception err = error;
            MAIN.post(() -> {
                if (h.isCancelled() || act.isFinishing() || act.isDestroyed()) {
                    return;
                }
                if (err != null) {
                    done.fail(err);
                } else {
                    done.ok(v);
                }
            });
        });
        return h;
    }

    /** Roda algo na thread principal. */
    static void ui(Runnable r) {
        MAIN.post(r);
    }
}
