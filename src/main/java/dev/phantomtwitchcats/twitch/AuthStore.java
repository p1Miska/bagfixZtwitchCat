package dev.phantomtwitchcats.twitch;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Локальное хранилище OAuth-токенов Twitch. Никогда не логируется и не отправляется куда-либо, кроме Twitch. */
public final class AuthStore {

    private static final AuthStore INSTANCE = new AuthStore();

    private final Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("phantom-twitch-cats-auth.json");

    private String accessToken;
    private String refreshToken;
    private String userId;
    private String userLogin;
    private String userName;
    private long expiresAtMs;

    private AuthStore() {
        load();
    }

    public static AuthStore get() {
        return INSTANCE;
    }

    public synchronized boolean hasTokens() {
        return accessToken != null && !accessToken.isBlank()
                && refreshToken != null && !refreshToken.isBlank();
    }

    /** Истёк ли access-токен (с запасом 5 минут). */
    public synchronized boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs - 300_000L;
    }

    public synchronized void forceExpire() {
        this.expiresAtMs = 0;
    }

    public synchronized void store(String accessToken, String refreshToken, long expiresInSec,
                                   String userId, String userLogin, String userName) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.userLogin = userLogin;
        this.userName = userName;
        this.expiresAtMs = System.currentTimeMillis() + Math.max(60L, expiresInSec) * 1000L;
        save();
    }

    public synchronized void clear() {
        accessToken = null;
        refreshToken = null;
        userId = null;
        userLogin = null;
        userName = null;
        expiresAtMs = 0;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    public synchronized String accessToken() {
        return accessToken;
    }

    public synchronized String refreshToken() {
        return refreshToken;
    }

    public synchronized String userId() {
        return userId == null ? "" : userId;
    }

    public synchronized String userLogin() {
        return userLogin == null ? "" : userLogin;
    }

    public synchronized String userName() {
        return userName == null ? "" : userName;
    }

    private void load() {
        try {
            if (!Files.exists(path)) {
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            accessToken = str(o, "access_token");
            refreshToken = str(o, "refresh_token");
            userId = str(o, "user_id");
            userLogin = str(o, "user_login");
            userName = str(o, "user_name");
            expiresAtMs = o.has("expires_at_ms") && !o.get("expires_at_ms").isJsonNull()
                    ? o.get("expires_at_ms").getAsLong() : 0;
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось прочитать Twitch-авторизацию: {}", e.toString());
        }
    }

    private void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("access_token", accessToken);
            o.addProperty("refresh_token", refreshToken);
            o.addProperty("user_id", userId);
            o.addProperty("user_login", userLogin);
            o.addProperty("user_name", userName);
            o.addProperty("expires_at_ms", expiresAtMs);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось сохранить Twitch-авторизацию: {}", e.toString());
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}