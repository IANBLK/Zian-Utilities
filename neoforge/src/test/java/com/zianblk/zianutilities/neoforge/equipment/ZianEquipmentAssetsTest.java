package com.zianblk.zianutilities.neoforge.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ZianEquipmentAssetsTest {
    @Test
    void allEquipmentTexturesAreInTheItemAtlas() throws Exception {
        for (String set : List.of("prismatic", "dark_reaper")) {
        for (String part : List.of("helmet", "chestplate", "leggings", "boots", "sword", "axe", "pickaxe", "shovel", "hoe")) {
            String name = set + "_" + part;
            String modelPath = "/assets/zianutilities/models/item/" + name + ".json";
            try (InputStream stream = require(modelPath)) {
                String texture = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("textures").get("layer0").getAsString();
                assertEquals("zianutilities:item/" + name, texture, modelPath);
            }
            try (InputStream texture = require("/assets/zianutilities/textures/item/" + name + ".png")) {
                assertNotNull(ImageIO.read(texture), "Invalid PNG for " + name);
            }
        }
        for (int layer = 1; layer <= 2; layer++)
            try (InputStream texture = require("/assets/zianutilities/textures/models/armor/" + set + "_layer_"
                + layer + ".png")) {
                var image = ImageIO.read(texture);
                assertNotNull(image, "Invalid armor layer " + layer);
                assertEquals(2, image.getWidth() / image.getHeight(), "Armor UV aspect ratio");
            }
        }
    }

    @Test
    void bowsResolveAllDrawingModelsAndTexturesWithoutInheritedVanillaOverrides() throws Exception {
        for (String shape : List.of("curve", "regular", "long", "harp")) {
            String name = shape + "_bow";
            try (var stream = require("/assets/zianutilities/models/item/" + name + ".json")) {
                var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var overrides = model.getAsJsonArray("overrides");
                assertEquals(3, overrides.size());
                for (var override : overrides) {
                    String target = override.getAsJsonObject().get("model").getAsString();
                    String[] id = target.split(":", 2);
                    try (var pulling = require("/assets/" + id[0] + "/models/" + id[1] + ".json")) {
                        var drawing = JsonParser.parseReader(new InputStreamReader(pulling, StandardCharsets.UTF_8)).getAsJsonObject();
                        assertEquals("zianutilities:item/" + name, drawing.get("parent").getAsString());
                        assertEquals(0, drawing.getAsJsonArray("overrides").size());
                        String[] texture = drawing.getAsJsonObject("textures").get("layer0").getAsString().split(":", 2);
                        try (var png = require("/assets/" + texture[0] + "/textures/" + texture[1] + ".png")) {
                            assertNotNull(ImageIO.read(png));
                        }
                    }
                }
            }
            try (var png = require("/assets/zianutilities/textures/item/" + name + ".png")) {
                assertNotNull(ImageIO.read(png));
            }
        }
    }

    private static InputStream require(String path) {
        InputStream stream = ZianEquipmentAssetsTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource " + path);
        return stream;
    }
}
