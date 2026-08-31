package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Больше НЕ делает OAuth-флоу сам (без браузера, без локального HTTP-сервера,
 * без Client Secret). Вместо этого пользователь получает Access Token,
 * Refresh Token и Client ID вручную на https://twitchtokengenerator.com
 * и вставляет их в настройки мода.
 *
 * Обновление токена идёт через собственный API twitchtokengenerator.com
 * (GET /api/refresh/{refresh_token}), который не требует Client Secret —
 * сервис сам хранит секрет своего общего Twitch-приложения. Это подтверждено
 * официальным README проекта (github.com/swiftyspiffy/twitch-token-generator).
 *
 * ВАЖНО (известная особенность сервиса): в ответе /api/refresh/ поле
 * "client_id" может не совпадать с тем Client ID, что показывался на сайте
 * при создании токена (баг сервиса, встречается в issue-трекере). Поэтому
 * мы НИКОГДА не берём client_id из ответа рефреша — используем только тот,
 * что пользователь вставил в настройках сам.
 */
public final class TwitchAuth {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String REFRESH_URL = "https://twitchtokengenerator.com/api/refresh/";

    private TwitchAuth() {
    }

    /** Результат обновления: новый access-токен и (обычно) новый refresh-токен. */
    public record RefreshResult(String accessToken, String refreshToken) {
    }

    /**
     * Обновляет access-токен через twitchtokengenerator.com.
     * Формат ответа сервиса: {"success":true,"token":"...","refresh":"...","client_id":"..."}
     * (поля называются "token"/"refresh", НЕ "access_token"/"refresh_token" —
     * это собственный формат сайта, отличный от обычного Twitch OAuth-ответа).
     */
    public static RefreshResult refreshViaTokenGenerator(String refreshToken)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(REFRESH_URL + enc(refreshToken)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject obj = parse(resp.body());
        if (resp.statusCode() != 200 || !obj.has("success") || !obj.get("success").getAsBoolean()) {
            String msg = obj.has("message") ? obj.get("message").getAsString() : resp.body();
            throw new IOException("обновление токена через twitchtokengenerator.com не удалось: " + msg);
        }
        String newToken = str(obj, "token");
        String newRefresh = str(obj, "refresh");
        if (newToken == null || newToken.isBlank()) {
            throw new IOException("twitchtokengenerator.com не вернул новый токен");
        }
        // Если сайт не прислал новый refresh (пустая строка), оставляем старый.
        return new RefreshResult(newToken, (newRefresh == null || newRefresh.isBlank()) ? refreshToken : newRefresh);
    }

    private static JsonObject parse(String body) {
        try {
            return JsonParser.parseString(body == null ? "{}" : body).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Открывает ссылку в системном браузере (используется кнопкой "Открыть сайт для токена"). */
    public static void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Throwable ignored) {
        }
    }
}
