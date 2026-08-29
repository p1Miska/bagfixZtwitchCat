package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OAuth Authorization Code Flow:
 * браузер -> http://localhost:PORT (локальный сервер мода) -> обмен кода на токены.
 * Токены сохраняются в AuthStore и дальше автоматически обновляются.
 */
public final class TwitchAuth {

    public interface Callback {
        void onSuccess();

        void onError(String message);
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private TwitchAuth() {
    }

    public static void authorizeAsync(String clientId, String clientSecret, int port,
                                      Consumer<String> urlSink, Callback callback) {
        Thread t = new Thread(() -> run(clientId, clientSecret, port, urlSink, callback), "PTC-TwitchAuth");
        t.setDaemon(true);
        t.start();
    }

    private static void run(String clientId, String clientSecret, int port,
                            Consumer<String> urlSink, Callback callback) {
        String redirectUri = "http://localhost:" + port;
        String state = randomState();
        String url = "https://id.twitch.tv/oauth2/authorize"
                + "?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(String.join(" ", TwitchManager.SCOPES))
                + "&state=" + enc(state);

        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(300_000); // ждём стримера максимум 5 минут
            if (urlSink != null) {
                urlSink.accept(url);
            }
            openBrowser(url);

            while (true) {
                Map<String, String> params;
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10_000);
                    params = readRequest(socket);
                    respondHtml(socket);
                }
                if (params.isEmpty()) {
                    continue; // например, запрос favicon — ждём настоящий редирект
                }

                if (params.containsKey("error")) {
                    callback.onError("Twitch: " + params.getOrDefault("error_description", params.get("error")));
                    return;
                }
                if (!state.equals(params.get("state"))) {
                    callback.onError("несовпадающий параметр state в ответе Twitch");
                    return;
                }
                String code = params.get("code");
                if (code == null || code.isBlank()) {
                    callback.onError("Twitch не прислал код авторизации");
                    return;
                }

                JsonObject tokens = exchangeCode(clientId, clientSecret, redirectUri, code);
                String accessToken = require(tokens, "access_token");
                String refreshToken = require(tokens, "refresh_token");
                long expiresIn = tokens.has("expires_in") && !tokens.get("expires_in").isJsonNull()
                        ? tokens.get("expires_in").getAsLong() : 14_400L;

                Helix.UserInfo user = Helix.getUser(accessToken, clientId);
                AuthStore.get().store(accessToken, refreshToken, expiresIn,
                        user.id(), user.login(), user.displayName());
                callback.onSuccess();
                return;
            }
        } catch (Exception e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /** Обновление access-токена по refresh-токену. */
    public static JsonObject refreshTokens(String clientId, String clientSecret, String refreshToken)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + enc(clientId)
                                + "&client_secret=" + enc(clientSecret)
                                + "&grant_type=refresh_token"
                                + "&refresh_token=" + enc(refreshToken)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject obj = parse(resp.body());
        if (resp.statusCode() != 200) {
            throw new IOException("обновление токена: HTTP " + resp.statusCode() + " — "
                    + (obj.has("message") ? obj.get("message").getAsString() : resp.body()));
        }
        return obj;
    }

    // ------------------------------------------------------------------

    private static JsonObject exchangeCode(String clientId, String clientSecret,
                                           String redirectUri, String code)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "client_id=" + enc(clientId)
                                + "&client_secret=" + enc(clientSecret)
                                + "&code=" + enc(code)
                                + "&grant_type=authorization_code"
                                + "&redirect_uri=" + enc(redirectUri)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject obj = parse(resp.body());
        if (resp.statusCode() != 200) {
            throw new IOException("получение токена: HTTP " + resp.statusCode() + " — "
                    + (obj.has("message") ? obj.get("message").getAsString() : resp.body()));
        }
        return obj;
    }

    /** Читает HTTP GET-запрос браузера и возвращает параметры query string. */
    private static Map<String, String> readRequest(Socket socket) {
        Map<String, String> params = new LinkedHashMap<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || !line.startsWith("GET ")) {
                return params;
            }
            int q = line.indexOf('?');
            int sp = line.indexOf(' ', 4);
            String query = (q >= 0 && sp > q) ? line.substring(q + 1, sp) : "";
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                // заголовки не нужны
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // неполный запрос — просто дождёмся следующего
        }
        return params;
    }

    private static void respondHtml(Socket socket) {
        try {
            String body = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                    + "<title>Phantom Twitch Cats</title></head>"
                    + "<body style=\"font-family:sans-serif;text-align:center;padding-top:60px;"
                    + "background:#18181b;color:#e9e9e9\">"
                    + "<h2>Phantom Twitch Cats</h2>"
                    + "<p>Авторизация обработана — это окно можно закрыть и вернуться в Minecraft.</p>"
                    + "</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static void openBrowser(String url) {
        // Используем java.awt.Desktop напрямую вместо net.minecraft.Util —
        // тот класс не резолвится в 26.1.2 по старому импорту, а Desktop
        // не зависит от маппингов Minecraft вообще и работает надёжно.
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } else {
                PhantomTwitchCatsClient.LOGGER.warn("Desktop.browse недоступен, откройте ссылку вручную: {}", url);
            }
        } catch (Throwable t) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось открыть браузер автоматически: {}", t.toString());
        }
    }

    private static String randomState() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static JsonObject parse(String body) {
        try {
            return JsonParser.parseString(body == null ? "{}" : body).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String require(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new IllegalStateException("в ответе Twitch нет поля \"" + key + "\"");
        }
        return o.get(key).getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}