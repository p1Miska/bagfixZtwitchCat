package dev.phantomtwitchcats.twitch;

import com.google.gson.JsonObject;
import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.CatRequest;
import dev.phantomtwitchcats.cat.InputParser;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Центральный менеджер Twitch-интеграции: авторизация, EventSub WebSocket,
 * обработка redemption'ов, возврат/подтверждение поинтов, авто-reconnect.
 */
public final class TwitchManager {

    public static final String[] SCOPES = {
            "channel:read:redemptions",   // чтение активаций наград + EventSub
            "channel:manage:redemptions"  // подтверждение (FULFILLED) и возврат (CANCELED) поинтов
    };
    public static final String REDEMPTION_SUB_TYPE = "channel.channel_points_custom_reward_redemption.add";

    private static final TwitchManager INSTANCE = new TwitchManager();

    public record Redemption(String id, String userId, String userLogin, String userName,
                             String rewardId, String rewardTitle, String userInput, String status,
                             String broadcasterId) {
    }

    public enum State { OFFLINE, NEEDS_SETUP, AUTHORIZING, CONNECTING, CONNECTED }

    private volatile State state = State.OFFLINE;
    private volatile String detail = "";
    private volatile boolean manualDisconnect = false;

    private final AuthStore store = AuthStore.get();
    private volatile EventSubSocket socket;

    private final ExecutorService io = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PTC-IO");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService reconnects = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PTC-Reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private final AtomicBoolean connectPending = new AtomicBoolean(false);
    private volatile int reconnectAttempts = 0;

    private TwitchManager() {
    }

    public static TwitchManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------- API

    public void autoConnect() {
        if (store.hasTokens() && !manualDisconnect) {
            connectNow();
        }
    }

    public void connectNow() {
        PtcConfig cfg = ConfigManager.get();
        if (cfg.clientId.isBlank()) {
            state = State.NEEDS_SETUP;
            detail = "не указан Client ID";
            return;
        }
        manualDisconnect = false;
        if (!store.hasTokens()) {
            startAuthorization();
            return;
        }
        io.execute(() -> {
            if (!connectPending.compareAndSet(false, true)) {
                return;
            }
            try {
                state = State.CONNECTING;
                detail = "";
                socket().connect();
            } finally {
                connectPending.set(false);
            }
        });
    }

    public void startAuthorization() {
        PtcConfig cfg = ConfigManager.get();
        if (cfg.clientId.isBlank() || cfg.clientSecret.isBlank()) {
            state = State.NEEDS_SETUP;
            detail = "укажите Client ID и Client Secret в настройках";
            chat("Для авторизации Twitch укажите Client ID и Client Secret в настройках мода.");
            return;
        }
        manualDisconnect = false;
        state = State.AUTHORIZING;
        detail = "ожидание входа через браузер";
        chat("Открываю браузер для авторизации Twitch… Если он не открылся — ссылка в логе (latest.log).");
        TwitchAuth.authorizeAsync(cfg.clientId, cfg.clientSecret, cfg.authPort,
                url -> PhantomTwitchCatsClient.LOGGER.info("Twitch OAuth URL: {}", url),
                new TwitchAuth.Callback() {
                    @Override
                    public void onSuccess() {
                        chat("Twitch авторизован! Подключаюсь…");
                        connectNow();
                    }

                    @Override
                    public void onError(String message) {
                        state = State.OFFLINE;
                        detail = message;
                        chat("Ошибка авторизации Twitch: " + message);
                    }
                });
    }

    public void disconnect(boolean manual) {
        if (manual) {
            manualDisconnect = true;
        }
        reconnectPending.set(false);
        EventSubSocket s = socket;
        socket = null;
        if (s != null) {
            s.shutdown();
        }
        state = State.OFFLINE;
        detail = manual ? "отключён вручную" : "";
    }

    public void logout() {
        disconnect(true);
        store.clear();
        detail = "токены удалены";
        chat("Токены Twitch удалены.");
    }

    public void loadRewards(java.util.function.Consumer<List<Helix.RewardInfo>> onSuccess,
                            java.util.function.Consumer<String> onError) {
        PtcConfig cfg = ConfigManager.get();
        if (cfg.clientId.isBlank() || !store.hasTokens()) {
            onError.accept("сначала авторизуйте Twitch и укажите Client ID");
            return;
        }
        io.execute(() -> {
            try {
                List<Helix.RewardInfo> rewards = authed(token ->
                        Helix.getCustomRewards(token, cfg.clientId, store.userId()));
                Minecraft.getInstance().execute(() -> onSuccess.accept(rewards));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                Minecraft.getInstance().execute(() -> onError.accept(msg));
            }
        });
    }

    public void shutdown() {
        manualDisconnect = true;
        EventSubSocket s = socket;
        socket = null;
        if (s != null) {
            s.shutdown();
        }
        io.shutdownNow();
        reconnects.shutdownNow();
        RedemptionDeduper.get().save();
    }

    public String status() {
        return switch (state) {
            case CONNECTED -> "подключён ✓" + (detail.isBlank() ? "" : " — " + detail);
            case CONNECTING -> "подключаюсь…";
            case AUTHORIZING -> "авторизация… " + detail;
            case NEEDS_SETUP -> "не настроен: " + detail;
            case OFFLINE -> detail.isBlank() ? "не подключён" : "не подключён (" + detail + ")";
        };
    }

    public String shortStatus() {
        return switch (state) {
            case CONNECTED -> "✓";
            case CONNECTING, AUTHORIZING -> "…";
            default -> "✗";
        };
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public State state() {
        return state;
    }

    // ------------------------------------------- колбэки из EventSubSocket

    void onConnecting() {
        if (state != State.AUTHORIZING) {
            state = State.CONNECTING;
        }
    }

    void onSessionReady(String sessionId) {
        io.execute(() -> {
            try {
                String userId = store.userId();
                authed(token -> {
                    JsonObject condition = new JsonObject();
                    condition.addProperty("broadcaster_user_id", userId);
                    Helix.createEventSubSubscription(token, ConfigManager.get().clientId,
                            sessionId, REDEMPTION_SUB_TYPE, "1", condition);
                    return null;
                });
                reconnectAttempts = 0;
                state = State.CONNECTED;
                detail = store.userLogin();
                PhantomTwitchCatsClient.LOGGER.info("Twitch EventSub: подключено ({}), слушаю награды", store.userLogin());
            } catch (Exception e) {
                PhantomTwitchCatsClient.LOGGER.warn("Не удалось создать подписку EventSub: {}", e.toString());
                state = State.OFFLINE;
                detail = "ошибка подписки: " + (e.getMessage() != null ? e.getMessage() : e.toString());
                scheduleReconnect();
            }
        });
    }

    void onSocketClosed() {
        if (manualDisconnect) {
            return;
        }
        if (state == State.CONNECTED || state == State.CONNECTING) {
            state = State.OFFLINE;
            detail = "соединение потеряно";
        }
        scheduleReconnect();
    }

    void onRevoked() {
        state = State.OFFLINE;
        detail = "подписка отозвана — нужна повторная авторизация";
        chat("Twitch отозвал подписку (проверьте токен). Выполните: /phantomcat twitch connect");
    }

    void onRedemption(Redemption r) {
        PtcConfig cfg = ConfigManager.get();
        // фильтр по настроенной награде: точный ID优先, иначе название
        if (!cfg.rewardId.isBlank()) {
            if (!cfg.rewardId.equals(r.rewardId())) {
                return;
            }
        } else if (!cfg.rewardTitle.isBlank()) {
            if (!cfg.rewardTitle.trim().equalsIgnoreCase(r.rewardTitle().trim())) {
                return;
            }
        } else {
            return; // награда не настроена
        }

        // дедупликация по redemption ID (в т.ч. повторная доставка после reconnect)
        if (!RedemptionDeduper.get().mark(r.id())) {
            PhantomTwitchCatsClient.LOGGER.info("Повторный redemption {} — пропускаю", r.id());
            return;
        }

        Minecraft.getInstance().execute(() -> handleRedemption(r));
    }

    private void handleRedemption(Redemption r) {
        CatRequest request = InputParser.parse(r.userInput(), r.userLogin(), r.userId());
        CatManager.SpawnResult result = CatManager.get().trySpawn(request);
        switch (result) {
            case SPAWNED -> {
                if (ConfigManager.get().fulfillRedemptions) {
                    io.execute(() -> updateRedemption(r, "FULFILLED"));
                }
            }
            case ALREADY_HAS_CAT -> {
                if (ConfigManager.get().refundRedemptions) {
                    chat(r.userLogin() + " уже имеет активного кота — поинты возвращены.");
                    io.execute(() -> updateRedemption(r, "CANCELED"));
                }
            }
            case LIMIT_REACHED -> {
                if (ConfigManager.get().refundRedemptions) {
                    chat("Достигнут лимит котов — поинты " + r.userLogin() + " возвращены.");
                    io.execute(() -> updateRedemption(r, "CANCELED"));
                }
            }
            case NO_WORLD -> {
                if (ConfigManager.get().refundRedemptions) {
                    chat("Стример не в игре — поинты " + r.userLogin() + " возвращены.");
                    io.execute(() -> updateRedemption(r, "CANCELED"));
                }
            }
        }
    }

    private void updateRedemption(Redemption r, String status) {
        if (r.rewardId().isBlank() || r.broadcasterId().isBlank()) {
            return;
        }
        try {
            authed(token -> {
                Helix.updateRedemptionStatus(token, ConfigManager.get().clientId,
                        r.broadcasterId(), r.rewardId(), r.id(), status);
                return null;
            });
            PhantomTwitchCatsClient.LOGGER.info("Redemption {} помечен как {}", r.id(), status);
        } catch (Exception e) {
            PhantomTwitchCatsClient.LOGGER.warn("Не удалось обновить redemption {}: {}", r.id(), e.toString());
        }
    }

    // ------------------------------------------------------------- внутреннее

    private EventSubSocket socket() {
        EventSubSocket s = socket;
        if (s == null) {
            synchronized (this) {
                s = socket;
                if (s == null) {
                    s = new EventSubSocket(this);
                    socket = s;
                }
            }
        }
        return s;
    }

    private void scheduleReconnect() {
        if (manualDisconnect || !store.hasTokens()) {
            return;
        }
        if (!reconnectPending.compareAndSet(false, true)) {
            return; // переподключение уже запланировано
        }
        long delayMs = Math.min(60_000L, 1_000L << Math.min(reconnectAttempts, 6));
        reconnectAttempts++;
        PhantomTwitchCatsClient.LOGGER.info("Twitch: переподключение через {} мс", delayMs);
        reconnects.schedule(() -> {
            reconnectPending.set(false);
            if (!manualDisconnect && store.hasTokens()) {
                connectNow();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private interface AuthedCall<T> {
        T run(String token) throws Exception;
    }

    /** Выполняет вызов Helix с валидным токеном; при 401 обновляет токен и повторяет один раз. */
    private <T> T authed(AuthedCall<T> call) throws Exception {
        String token = validToken();
        try {
            return call.run(token);
        } catch (Helix.UnauthorizedException e) {
            store.forceExpire();
            return call.run(validToken());
        }
    }

    private String validToken() throws Exception {
        if (!store.hasTokens()) {
            throw new IllegalStateException("нет сохранённых токенов Twitch");
        }
        if (store.isExpired()) {
            refreshToken(ConfigManager.get().clientId, ConfigManager.get().clientSecret);
        }
        return store.accessToken();
    }

    private void refreshToken(String clientId, String clientSecret) throws Exception {
        synchronized (store) {
            if (!store.isExpired()) {
                return; // другой поток уже обновил
            }
            try {
                JsonObject resp = TwitchAuth.refreshTokens(clientId, clientSecret, store.refreshToken());
                store.store(resp.get("access_token").getAsString(),
                        resp.get("refresh_token").getAsString(),
                        resp.has("expires_in") ? resp.get("expires_in").getAsLong() : 14_400L,
                        store.userId(), store.userLogin(), store.userName());
                PhantomTwitchCatsClient.LOGGER.info("Twitch-токен обновлён");
            } catch (Exception e) {
                store.clear();
                state = State.OFFLINE;
                detail = "токен устарел — нужна повторная авторизация";
                chat("Не удалось обновить Twitch-токен. Выполните: /phantomcat twitch connect");
                throw e;
            }
        }
    }

    private static void chat(String message) {
        // ChatComponent.addMessage приватный в 26.1.2 — вызвать снаружи нельзя.
        // Заменено на лог до появления публичного API для локальных сообщений.
        PhantomTwitchCatsClient.LOGGER.info("[PhantomCats] {}", message);
    }
}