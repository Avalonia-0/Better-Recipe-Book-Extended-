package com.alonie.recipebookispain_extended;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class RecipeBookIsPainExtendedConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final boolean DEFAULT_EXTENDED_FEATURES = true;
    private static final int DEFAULT_BOTTOM_NUMBER = 16;
    private static final int MIN_BOTTOM_NUMBER = 6;
    private static final int MAX_BOTTOM_NUMBER = 16;

    private static RecipeBookIsPainExtendedConfig instance;
    private static long lastModified = -1L;

    private final boolean extendedFeatures;
    private final int bottomNumber;

    private RecipeBookIsPainExtendedConfig(boolean extendedFeatures, int bottomNumber) {
        this.extendedFeatures = extendedFeatures;
        this.bottomNumber = bottomNumber;
    }

    public boolean extendedFeatures() {
        return this.extendedFeatures;
    }

    public int bottomNumber() {
        return this.bottomNumber;
    }

    public static RecipeBookIsPainExtendedConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static boolean reloadIfChanged() {
        Path path = configPath();
        long modified = readLastModified(path);
        if (instance == null || modified != lastModified) {
            load();
            return true;
        }
        return false;
    }

    private static void load() {
        Path path = configPath();
        boolean dirty = false;
        boolean extendedFeatures = DEFAULT_EXTENDED_FEATURES;
        int bottomNumber = DEFAULT_BOTTOM_NUMBER;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    JsonObject object = root.getAsJsonObject();
                    JsonElement extendedElement = object.get("extended_features");
                    JsonElement bottomElement = object.get("bottom_number");

                    if (extendedElement != null && extendedElement.isJsonPrimitive()
                            && extendedElement.getAsJsonPrimitive().isBoolean()) {
                        extendedFeatures = extendedElement.getAsBoolean();
                    } else {
                        dirty = true;
                    }

                    if (bottomElement != null && bottomElement.isJsonPrimitive()
                            && bottomElement.getAsJsonPrimitive().isNumber()
                            && INTEGER_PATTERN.matcher(bottomElement.getAsString()).matches()) {
                        int value = bottomElement.getAsInt();
                        if (value >= MIN_BOTTOM_NUMBER && value <= MAX_BOTTOM_NUMBER) {
                            bottomNumber = value;
                        } else {
                            dirty = true;
                        }
                    } else {
                        dirty = true;
                    }
                } else {
                    dirty = true;
                }
            } catch (Exception e) {
                dirty = true;
                RecipeBookIsPain.LOGGER.warn("[RBIP] Could not read extended config; using defaults", e);
            }
        } else {
            dirty = true;
        }

        instance = new RecipeBookIsPainExtendedConfig(extendedFeatures, bottomNumber);

        if (dirty) {
            write();
        } else {
            lastModified = readLastModified(path);
        }
    }

    private static void write() {
        Path path = configPath();
        JsonObject object = new JsonObject();
        object.addProperty("extended_features", instance.extendedFeatures);
        object.addProperty("bottom_number", instance.bottomNumber);

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(object, writer);
            }
            lastModified = readLastModified(path);
        } catch (IOException e) {
            lastModified = readLastModified(path);
            RecipeBookIsPain.LOGGER.warn("[RBIP] Could not write extended config", e);
        }
    }

    private static Path configPath() {
        if (RecipeBookIsPain.PLATFORM == null) {
            return Path.of("config").resolve("recipe-book-is-pain-extended.json");
        }
        return RecipeBookIsPain.PLATFORM.getConfigDir().resolve("recipe-book-is-pain-extended.json");
    }

    private static long readLastModified(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }
}
