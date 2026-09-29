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
        for (String name : List.of("prismatic_helmet", "prismatic_chestplate", "prismatic_leggings",
            "prismatic_boots", "prismatic_sword", "prismatic_axe", "prismatic_pickaxe",
            "prismatic_shovel", "prismatic_hoe")) {
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
            try (InputStream texture = require("/assets/zianutilities/textures/models/armor/prismatic_layer_"
                + layer + ".png")) {
                assertNotNull(ImageIO.read(texture), "Invalid armor layer " + layer);
            }
    }

    private static InputStream require(String path) {
        InputStream stream = ZianEquipmentAssetsTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing resource " + path);
        return stream;
    }
}
