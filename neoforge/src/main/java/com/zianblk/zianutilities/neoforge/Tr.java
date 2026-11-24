package com.zianblk.zianutilities.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Texto traducible con el espanol de siempre como respaldo; el cliente lo resuelve con su idioma. */
public final class Tr {
    private Tr() {}

    public static MutableComponent c(String key, String es, Object... args) {
        return Component.translatableWithFallback(key, es, args);
    }

    /** Solo para pantallas del cliente: resuelve ahora en el idioma del juego. */
    public static String s(String key, String es, Object... args) {
        return c(key, es, args).getString();
    }
}
