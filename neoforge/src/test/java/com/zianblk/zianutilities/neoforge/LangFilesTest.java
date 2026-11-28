package com.zianblk.zianutilities.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LangFilesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[-#+0,(]*\\d*(\\.\\d+)?[sdfxXeEgG]");
    private static final String STRING = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    private static Map<String, String> load(String name) throws Exception {
        var json = JsonParser.parseString(new String(
            LangFilesTest.class.getResourceAsStream("/assets/zianutilities/lang/" + name).readAllBytes(),
            StandardCharsets.UTF_8)).getAsJsonObject();
        var map = new TreeMap<String, String>();
        json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        return map;
    }

    private static String placeholders(String text) {
        var found = new java.util.ArrayList<String>();
        var m = PLACEHOLDER.matcher(text);
        while (m.find()) found.add(m.group());
        java.util.Collections.sort(found);
        return String.join(",", found);
    }

    @Test void allLanguagesHaveSameKeysAndPlaceholders() throws Exception {
        var es = load("es_es.json");
        for (var other : new String[] {"en_us.json", "es_mx.json"}) {
            var map = load(other);
            assertEquals(es.keySet(), map.keySet(), "Claves distintas en " + other);
            es.forEach((key, text) -> assertEquals(placeholders(text), placeholders(map.get(key)), key + " en " + other));
        }
    }

    @Test void mexicanSpanishMatchesSpain() throws Exception {
        assertEquals(load("es_es.json"), load("es_mx.json"));
    }

    /** Todo Tr.c/Tr.s/Tr.enc y translatableWithFallback del codigo tiene su clave en es_es con el mismo espanol de respaldo. */
    @Test void codeFallbacksMatchSpanishLang() throws Exception {
        var es = load("es_es.json");
        var calls = Pattern.compile("(?:\\bTr\\.(?:c|s|enc)|translatableWithFallback|LocalizedException\\.(?:State|Argument)|\\bbad)\\(\\s*" + STRING + ",\\s*" + STRING);
        int checked = 0;
        try (Stream<Path> files = Files.walk(sources())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var m = calls.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    var key = m.group(1);
                    // PaseRewardJson.bad("range", ...) and friends: short keys under the pass problem prefix
                    if (!key.contains(".") && m.group().startsWith("bad(") || (!key.contains(".") && m.group().contains(".bad("))) key = "zianutilities.pase.problem." + key;
                    if (!key.contains("zianutilities")) continue;
                    assertTrue(es.containsKey(key), file.getFileName() + ": falta la clave " + key);
                    assertEquals(unescape(m.group(2)), es.get(key), file.getFileName() + ": respaldo distinto en " + key);
                    checked++;
                }
            }
        }
        assertTrue(checked > 100, "Se esperaban muchas claves en el codigo, se vieron " + checked);
    }

    /** Sube desde el directorio de trabajo hasta encontrar las fuentes del modulo (Gradle no fija el directorio). */
    private static Path sources() {
        String tail = "src/main/java/com/zianblk/zianutilities/neoforge/Tr.java";
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(tail))) return dir.resolve("src/main/java");
            if (Files.exists(dir.resolve("neoforge").resolve(tail))) return dir.resolve("neoforge/src/main/java");
        }
        throw new IllegalStateException("No se encuentran las fuentes del modulo");
    }

    private static String unescape(String java) { return java.replace("\\n", "\n").replace("\\\"", "\""); }
}
