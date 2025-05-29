package net.bearster.learningmod.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModPaintings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<String> paintings = new ArrayList<>();

    public void makePainting(String paintingName, int height, int width) {
        // Create painting JSON object
        Map<String, Object> painting = new LinkedHashMap<>();
        painting.put("asset_id", "learningmod:" + paintingName);
        painting.put("height", height);
        painting.put("width", width);

        // Path for painting file
        Path paintingPath = Paths.get(paintingDataPath, paintingName + ".json");

        try {
            Files.createDirectories(paintingPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(paintingPath)) {
                GSON.toJson(painting, writer);
            }
            LOGGER.info("Wrote painting JSON to: {}", paintingPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write painting JSON: {}", e.getMessage(), e);
        }

        // Add to tag values
        paintings.add("learningmod:" + paintingName);

        // Create placeable tag JSON
        Map<String, Object> placeable = new LinkedHashMap<>();
        placeable.put("values", paintings);

        Path tagPath = Paths.get(placeableDataPath);

        try {
            Files.createDirectories(tagPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(tagPath)) {
                GSON.toJson(placeable, writer);
            }
            LOGGER.info("Wrote placeable tag JSON to: {}", tagPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write placeable tag JSON: {}", e.getMessage(), e);
        }
    }

    private String paintingDataPath = "D:\\Codeing\\Java\\Minecraft\\forge-1.21.1-learning-mod\\src\\main\\resources\\data\\learningmod\\painting_variant"; // Change this line. You want to change the path
    private String placeableDataPath = "D:\\Codeing\\Java\\Minecraft\\forge-1.21.1-learning-mod\\src\\main\\resources\\data\\minecraft\\tags\\painting_variant\\placeable.json"; // Change this line. You want to change the path

    public void registerPaintings() {
        LOGGER.info("Registering paintings...");
        makePainting("jojo_botw", 4, 8);
        makePainting("jojo_knight", 6, 8);
        makePainting("saw_them", 2, 2);
        makePainting("shrimp", 1, 2);
        makePainting("wanderer", 2, 1);
        makePainting("world", 2, 2);
    }
}