package com.zianblk.zianutilities.neoforge.equipment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ZianEquipmentTagsTest {
    @Test
    void prismaticEquipmentHasTheMatchingVanillaTags() throws Exception {
        String[][] tagToItem = {
            {"head_armor", "helmet"}, {"chest_armor", "chestplate"},
            {"leg_armor", "leggings"}, {"foot_armor", "boots"},
            {"swords", "sword"}, {"axes", "axe"}, {"pickaxes", "pickaxe"},
            {"shovels", "shovel"}, {"hoes", "hoe"}
        };
        for (String[] pair : tagToItem) {
            String path = "data/minecraft/tags/item/" + pair[0] + ".json";
            Path file = sourceFile(path);
            assertTrue(Files.isRegularFile(file), "Missing tag: " + file);
            var values = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("values");
            for (String set : new String[]{"prismatic", "dark_reaper"}) {
            String expected = "zianutilities:" + set + "_" + pair[1];
            assertTrue(values.asList().stream().anyMatch(value -> expected.equals(value.getAsString())),
                path + " does not include " + expected);
            }
        }
    }

    @Test
    void bowsCanReceiveVanillaBowEnchantmentsWithoutReplacingVanillaMembers() throws Exception {
        var tag = JsonParser.parseString(Files.readString(sourceFile("data/minecraft/tags/item/enchantable/bow.json"))).getAsJsonObject();
        assertTrue(!tag.get("replace").getAsBoolean());
        for (String shape : new String[]{"curve", "regular", "long", "harp"}) {
            String expected = "zianutilities:" + shape + "_bow";
            assertTrue(tag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> expected.equals(value.getAsString())), expected);
        }
    }

    private static Path sourceFile(String relative) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("src/main/resources").resolve(relative);
            if (Files.isRegularFile(direct)) return direct;
            Path nested = current.resolve("neoforge/src/main/resources").resolve(relative);
            if (Files.isRegularFile(nested)) return nested;
            current = current.getParent();
        }
        return Path.of(relative);
    }
}
