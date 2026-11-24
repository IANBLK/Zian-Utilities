package com.zianblk.zianutilities.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Texto traducible con el espanol de siempre como respaldo; el cliente lo resuelve con su idioma. */
public final class Tr {
    private static final char MARK = (char) 1, SEP = (char) 31;

    private Tr() {}

    public static MutableComponent c(String key, String es, Object... args) {
        return Component.translatableWithFallback(key, es, args);
    }

    /** Solo para pantallas del cliente: resuelve ahora en el idioma del juego. */
    public static String s(String key, String es, Object... args) {
        return c(key, es, args).getString();
    }

    /** Texto para viajar en un campo String de red: clave, respaldo en espanol y argumentos. */
    public static String enc(String key, String es, Object... args) {
        StringBuilder out = new StringBuilder().append(MARK).append(key).append(SEP)
            .append(String.format(java.util.Locale.ROOT, es, args));
        for (Object arg : args) out.append(SEP).append(arg);
        return out.toString();
    }

    /** Lo contrario de enc; el texto sin marca (el de siempre) pasa tal cual. */
    public static MutableComponent dec(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != MARK)
            return Component.literal(text == null ? "" : text);
        String[] parts = text.substring(1).split(String.valueOf(SEP), -1);
        return c(parts[0], parts[1], (Object[]) java.util.Arrays.copyOfRange(parts, 2, parts.length));
    }

    public static String decS(String text) {
        return dec(text).getString();
    }
}
