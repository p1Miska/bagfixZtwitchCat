package dev.phantomtwitchcats.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("phantom-twitch-cats.json");

    private static PtcConfig config = new PtcConfig();

    private ConfigManager() {
    }

    public static PtcConfig get() {
        return config;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                PtcConfig loaded = GSON.fromJson(Files.readString(PATH), PtcConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось прочитать конфиг, использую значения по умолчанию: {}", e.toString());
        }
        config.normalize();
        save();
    }

    public static void save() {
        config.normalize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(config));
        } catch (IOException e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось сохранить конфиг: {}", e.toString());
        }
    }
}