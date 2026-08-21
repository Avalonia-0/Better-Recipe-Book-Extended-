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
import net.minecraft.resources.Identifier;

/**
 * JSON-file implementation of {@link PinStore}.
 *
 * <p>Reads are synchronous (called once at startup, acceptable).
 * Writes are <strong>asynchronous</strong> to avoid blocking the render thread.</p>
 */
public final class JsonPinStore implements PinStore {

    private static final Gson GSON = new Gson();
    private static final Type SET_TYPE = new TypeToken<HashSet<Identifier>>() {}.getType();

    /** New home (same directory level as the query pins / tab pins). */
    private final Path path;
    /** Legacy pre-rename location ({@code zzzbrbe.pins}), read once for migration. */
    private final Path legacyPath;

    public JsonPinStore(Path gameDir) {
        this.path = gameDir.resolve("zzzbrbe.pins.json");
        this.legacyPath = gameDir.resolve("zzzbrbe.pins");
    }

    @Override
    public Set<Identifier> load() {
        if (Files.exists(path)) {
            return readFrom(path);
        }
        if (Files.exists(legacyPath)) {
            // Migration: the recipe-book pins moved to zzzbrbe.pins.json to sit
            // next to the query/tab pin files; read the legacy file and write
            // the new one (async) so the old file stops being authoritative.
            Set<Identifier> legacy = readFrom(legacyPath);
            if (!legacy.isEmpty()) {
                save(legacy);
            }
            return legacy;
        }
        return new HashSet<>();
    }

    private static Set<Identifier> readFrom(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Set<Identifier> result = GSON.fromJson(json, SET_TYPE);
            return result != null ? result : new HashSet<>();
        } catch (IOException e) {
            System.err.println("[BRBE] Failed to read pins file: " + e.getMessage());
            return new HashSet<>();
        }
    }

    @Override
    public void save(Set<Identifier> pinned) {
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
