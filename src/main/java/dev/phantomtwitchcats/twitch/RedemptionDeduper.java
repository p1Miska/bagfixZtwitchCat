package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Помнит обработанные redemption ID. Twitch доставляет события «минимум один раз»,
 * поэтому без дедупликации один redemption мог бы создать двух котов.
 * Список сохраняется на диск и переживает перезапуск игры.
 */
public final class RedemptionDeduper {

    private static final RedemptionDeduper INSTANCE = new RedemptionDeduper();
    private static final int MAX_ENTRIES = 4096;
    private static final long SAVE_INTERVAL_MS = 30_000L;

    private final Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("phantom-twitch-cats-redemptions.json");
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();
    private long lastSave = 0L;

    private RedemptionDeduper() {
    }

    public static RedemptionDeduper get() {
        return INSTANCE;
    }

    /**
     * @return true, если id новый (обрабатываем);
     *         false — этот redemption уже обрабатывали (дубликат от Twitch).
     */
    public synchronized boolean mark(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (seen.containsKey(id)) {
            return false;
        }
        seen.put(id, System.currentTimeMillis());
        trim();
        long now = System.currentTimeMillis();
        if (now - lastSave > SAVE_INTERVAL_MS) {
            save();
        }
        return true;
    }

    private void trim() {
        while (seen.size() > MAX_ENTRIES) {
            Iterator<String> it = seen.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else {
                break;
            }
        }
    }

    public synchronized void load() {
        try {
            if (!Files.exists(path)) {
                return;
            }
            JsonArray arr = JsonParser.parseString(Files.readString(path)).getAsJsonArray();
            for (var el : arr) {
                seen.put(el.getAsString(), 0L);
            }
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось загрузить список redemption: {}", e.toString());
        }
    }

    public synchronized void save() {
        try {
            JsonArray arr = new JsonArray();
            for (String id : seen.keySet()) {
                arr.add(id);
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, arr.toString());
            lastSave = System.currentTimeMillis();
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось сохранить список redemption: {}", e.toString());
        }
    }
}