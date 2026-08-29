package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EventSub WebSocket (wss://eventsub.wss.twitch.tv/ws) на чистом JDK —
 * без внешних библиотек. Получает события активации Channel Point Reward.
 */
final class EventSubSocket implements WebSocket.Listener {

    private static final String DEFAULT_URL = "wss://eventsub.wss.twitch.tv/ws";
    private static final long WATCHDOG_MS = 120_000L;

    private final TwitchManager manager;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PTC-EventSubWatchdog");
                t.setDaemon(true);
                return t;
            });

    private final StringBuilder buffer = new StringBuilder();
    private final AtomicBoolean closedByUs = new AtomicBoolean(false);
    private volatile WebSocket webSocket;
    private volatile long lastMessageAt = System.currentTimeMillis();

    EventSubSocket(TwitchManager manager) {
        this.manager = manager;
        scheduler.scheduleAtFixedRate(this::watchdog, 20, 20, TimeUnit.SECONDS);
    }

    synchronized void connect() {
        closedByUs.set(false);
        lastMessageAt = System.currentTimeMillis();
        buffer.setLength(0);
        WebSocket old = webSocket;
        webSocket = null;
        if (old != null) {
            try { old.abort(); } catch (Throwable ignored) { }
        }
        manager.onConnecting();
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(DEFAULT_URL), this)
                .whenComplete((ws, err) -> {
                    if (closedByUs.get()) {
                        if (ws != null) try { ws.abort(); } catch (Throwable ignored) { }
                        return;
                    }
                    if (err != null) {
                        PhantomTwitchCatsClient.LOGGER.warn("EventSub: ошибка подключения: {}", err.toString());
                        manager.onSocketClosed();
                    } else {
                        webSocket = ws;
                    }
                });
    }

    void shutdown() {
        closedByUs.set(true);
        scheduler.shutdownNow();
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"); } catch (Throwable ignored) { }
            try { ws.abort(); } catch (Throwable ignored) { }
        }
    }

    private void watchdog() {
        try {
            if (closedByUs.get() || webSocket == null) {
                return;
            }
            if (System.currentTimeMillis() - lastMessageAt > WATCHDOG_MS) {
                PhantomTwitchCatsClient.LOGGER.warn("EventSub: нет сообщений {} с — переподключаюсь", WATCHDOG_MS / 1000);
                forceReconnect();
            }
        } catch (Throwable t) {
            PhantomTwitchCatsClient.LOGGER.warn("EventSub watchdog: {}", t.toString());
        }
    }

    private void forceReconnect() {
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            try { ws.abort(); } catch (Throwable ignored) { }
        }
        manager.onSocketClosed(); // => TwitchManager переподключится с backoff
    }

    // ---------------------------------------------------- WebSocket.Listener

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        lastMessageAt = System.currentTimeMillis();
        buffer.append(data);
        if (last) {
            String message = buffer.toString();
            buffer.setLength(0);
            try {
                handleMessage(message);
            } catch (Throwable t) {
                PhantomTwitchCatsClient.LOGGER.warn("EventSub: ошибка обработки сообщения: {}", t.toString());
            }
        }
        ws.request(1); // обязательно: иначе придёт только первое сообщение
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        if (!closedByUs.get()) {
            PhantomTwitchCatsClient.LOGGER.info("EventSub: соединение закрыто ({} {})", statusCode, reason);
            manager.onSocketClosed();
        }
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        if (!closedByUs.get()) {
            PhantomTwitchCatsClient.LOGGER.warn("EventSub: ошибка соединения: {}", error.toString());
            manager.onSocketClosed();
        }
    }

    // ---------------------------------------------------- сообщения Twitch

    private void handleMessage(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        JsonObject metadata = root.has("metadata") && root.get("metadata").isJsonObject()
                ? root.getAsJsonObject("metadata") : new JsonObject();
        String type = str(metadata, "message_type");
        JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                ? root.getAsJsonObject("payload") : new JsonObject();

        switch (type) {
            case "session_welcome" -> {
                JsonObject session = payload.has("session") && payload.get("session").isJsonObject()
                        ? payload.getAsJsonObject("session") : new JsonObject();
                String sessionId = str(session, "id");
                if (!sessionId.isBlank()) {
                    manager.onSessionReady(sessionId);
                }
            }
            case "session_keepalive" -> { /* время последнего сообщения обновлено в onText */ }
            case "session_reconnect" -> {
                PhantomTwitchCatsClient.LOGGER.info("EventSub: сервер требует переподключение");
                forceReconnect();
            }
            case "revocation" -> manager.onRevoked();
            case "notification" -> {
                if (TwitchManager.REDEMPTION_SUB_TYPE.equals(str(metadata, "subscription_type"))
                        && payload.has("event") && payload.get("event").isJsonObject()) {
                    manager.onRedemption(parseRedemption(payload.getAsJsonObject("event")));
                }
            }
            default -> { }
        }
    }

    private static TwitchManager.Redemption parseRedemption(JsonObject e) {
        JsonObject reward = e.has("reward") && e.get("reward").isJsonObject()
                ? e.getAsJsonObject("reward") : new JsonObject();
        return new TwitchManager.Redemption(
                str(e, "id"),
                str(e, "user_id"),
                str(e, "user_login"),
                str(e, "user_name"),
                str(reward, "id"),
                str(reward, "title"),
                str(e, "user_input"),
                str(e, "status"),
                str(e, "broadcaster_user_id"));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}