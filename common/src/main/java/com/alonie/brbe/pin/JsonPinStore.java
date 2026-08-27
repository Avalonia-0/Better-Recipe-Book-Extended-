package com.alonie.brbe.pin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

/**
 * JSON-file implementation of {@link PinStore}.
 *
 * <p>Reads are synchronous (called once at startup, acceptable).
 * Writes are <strong>asynchronous</strong> to avoid blocking the render thread.</p>
 */
public final class JsonPinStore implements PinStore {

    private static final Gson GSON = new Gson();
    private static final Type SET_TYPE = new TypeToken<HashSet<ResourceLocation>>() {}.getType();

    private final Path path;

    public JsonPinStore(Path gameDir) {
        this.path = gameDir.resolve("zzzbrbe.pins");
    }

    @Override
    public Set<ResourceLocation> load() {
        if (!Files.exists(path)) {
            return new HashSet<>();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Set<ResourceLocation> result = GSON.fromJson(json, SET_TYPE);
            return result != null ? result : new HashSet<>();
        } catch (IOException e) {
            System.err.println("[BRBE] Failed to read pins file: " + e.getMessage());
            return new HashSet<>();
        }
    }

    @Override
    public void save(Set<ResourceLocation> pinned) {
        // Write asynchronously — game thread must never block on disk I/O
        CompletableFuture.runAsync(() -> {
            try {
                String json = GSON.toJson(pinned);
                Files.createDirectories(path.getParent());
                Files.writeString(path, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("[BRBE] Failed to write pins file: " + e.getMessage());
            }
        });
    }
}
