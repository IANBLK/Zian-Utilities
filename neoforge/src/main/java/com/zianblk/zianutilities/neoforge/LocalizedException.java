package com.zianblk.zianutilities.neoforge;

import java.util.Locale;

/** Errores para el jugador con clave de idioma; getMessage() sigue siendo el espanol de siempre (logs y smokes no cambian). */
public final class LocalizedException {
    private LocalizedException() {}

    public interface Info {
        String key();
        String es();
        Object[] args();
    }

    public static final class State extends IllegalStateException implements Info {
        private final String key, es; private final Object[] args;
        public State(String key, String es, Object... args) {
            super(String.format(Locale.ROOT, es, args));
            this.key = key; this.es = es; this.args = args;
        }
        public State cause(Throwable cause) { initCause(cause); return this; }
        @Override public String key() { return key; }
        @Override public String es() { return es; }
        @Override public Object[] args() { return args; }
    }

    public static final class Argument extends IllegalArgumentException implements Info {
        private final String key, es; private final Object[] args;
        public Argument(String key, String es, Object... args) {
            super(String.format(Locale.ROOT, es, args));
            this.key = key; this.es = es; this.args = args;
        }
        @Override public String key() { return key; }
        @Override public String es() { return es; }
        @Override public Object[] args() { return args; }
    }
}
