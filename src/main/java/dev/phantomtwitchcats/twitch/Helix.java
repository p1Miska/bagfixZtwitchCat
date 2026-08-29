package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Минимальный клиент Twitch Helix API на чистом JDK HttpClient (без библиотек). */
public final class Helix {

    public static final class UnauthorizedException extends IOException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public record UserInfo(String id, String login, String displayName) {
    }

    public record RewardInfo(String id, String title, int cost) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Helix() {
    }

    public static UserInfo getUser(String token, String clientId) throws IOException, InterruptedException {
        HttpRequest req = request(token, clientId)
                .uri(URI.create("https://api.twitch.tv/helix/users"))
                .GET().build();
        HttpResponse<String> resp = send(req);
        expectOk(resp, "получение пользователя");
        JsonArray data = JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonArray("data");
        if (data.size() == 0) {
            throw new IOException("Twitch не вернул данные пользователя");
        }
        JsonObject u = data.get(0).getAsJsonObject();
        return new UserInfo(str(u, "id"), str(u, "login"), str(u, "display_name"));
    }

    public static void createEventSubSubscription(String token, String clientId, String sessionId,
                                                  String type, String version, JsonObject condition)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("type", type);
        body.addProperty("version", version);
        body.add("condition", condition);
        JsonObject transport = new JsonObject();
        transport.addProperty("method", "websocket");
        transport.addProperty("session_id", sessionId);
        body.add("transport", transport);

        HttpRequest req = request(token, clientId)
                .uri(URI.create("https://api.twitch.tv/helix/eventsub/subscriptions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() == 409) {
            PhantomTwitchCatsClient.LOGGER.info("EventSub: подписка уже существует (409) — пропускаю");
            return;
        }
        expectOk(resp, "создание подписки EventSub");
    }

    /**
     * Обновление статуса redemption.
     * CANCELED = возврат Channel Points зрителю, FULFILLED = успешно выполнено.
     * Twitch позволяет менять только UNFULFILLED redemption'ы.
     */
    public static void updateRedemptionStatus(String token, String clientId, String broadcasterId,
                                              String rewardId, String redemptionId, String status)
            throws IOException, InterruptedException {
        URI uri = URI.create("https://api.twitch.tv/helix/channel_points/custom_rewards/redemptions"
                + "?broadcaster_id=" + enc(broadcasterId)
                + "&reward_id=" + enc(rewardId)
                + "&id=" + enc(redemptionId));
        JsonObject body = new JsonObject();
        body.addProperty("status", status);
        HttpRequest req = request(token, clientId)
                .uri(uri)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() == 404) {
            PhantomTwitchCatsClient.LOGGER.warn("Redemption {} не найден (404) — возможно, уже обработан", redemptionId);
            return;
        }
        expectOk(resp, "обновление статуса redemption");
    }

    public static List<RewardInfo> getCustomRewards(String token, String clientId, String broadcasterId)
            throws IOException, InterruptedException {
        URI uri = URI.create("https://api.twitch.tv/helix/channel_points/custom_rewards?broadcaster_id="
                + enc(broadcasterId) + "&only_manageable_rewards=false");
        HttpRequest req = request(token, clientId).uri(uri).GET().build();
        HttpResponse<String> resp = send(req);
        if (resp.statusCode() == 404) {
            return List.of(); // на канале нет собственных наград
        }
        expectOk(resp, "получение списка наград");
        List<RewardInfo> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (root.has("data") && root.get("data").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("data")) {
                JsonObject r = el.getAsJsonObject();
                out.add(new RewardInfo(str(r, "id"), str(r, "title"),
                        r.has("cost") ? r.get("cost").getAsInt() : 0));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------

    private static HttpRequest.Builder request(String token, String clientId) {
        return HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + token)
                .header("Client-Id", clientId)
                .timeout(Duration.ofSeconds(10));
    }

    private static HttpResponse<String> send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401) {
            throw new UnauthorizedException("Twitch API: 401 Unauthorized");
        }
        return resp;
    }

    private static void expectOk(HttpResponse<String> resp, String what) throws IOException {
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException(what + ": HTTP " + resp.statusCode() + " — " + briefError(resp.body()));
        }
    }

    private static String briefError(String body) {
        try {
            JsonObject o = JsonParser.parseString(body == null ? "" : body).getAsJsonObject();
            return o.has("message") ? o.get("message").getAsString() : String.valueOf(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}