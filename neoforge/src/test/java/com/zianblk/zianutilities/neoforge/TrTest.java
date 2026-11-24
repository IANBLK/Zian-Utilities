package com.zianblk.zianutilities.neoforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrTest {
    @Test void roundTripKeepsKeyFallbackAndArguments() {
        String text = Tr.enc("zianutilities.x", "Hola %s y %s", "Ana", 5);
        assertArrayEquals(new String[] {"zianutilities.x", "Hola Ana y 5", "Ana", "5"}, Tr.parse(text));
    }

    @Test void withoutArgumentsHasOnlyKeyAndFallback() {
        assertArrayEquals(new String[] {"k", "Texto"}, Tr.parse(Tr.enc("k", "Texto")));
    }

    @Test void emptyArgumentsSurvive() {
        assertArrayEquals(new String[] {"k", "a  b", "", ""}, Tr.parse(Tr.enc("k", "a %s b", "", "")));
    }

    @Test void plainTextIsNotParsed() {
        assertNull(Tr.parse("Este Pokémon ya no está en tu equipo"));
    }

    @Test void nullAndEmptyDoNotThrow() {
        assertNull(Tr.parse(null));
        assertNull(Tr.parse(""));
    }

    @Test void markWithoutSeparatorIsNotParsed() {
        assertNull(Tr.parse((char) 1 + "solo clave"));
        assertNull(Tr.parse(String.valueOf((char) 1)));
        assertNull(Tr.parse((char) 1 + "" + (char) 31 + "sin clave"));
    }

    @Test void encodedTextKeepsTheSpanishFallbackVisible() {
        assertTrue(Tr.enc("k", "Grupo No descubierto: no puede criar").contains("descubierto"));
        assertEquals((char) 1, Tr.enc("k", "x").charAt(0));
    }
}
