package dev.phantomtwitchcats.config;

import dev.phantomtwitchcats.twitch.Helix;
import dev.phantomtwitchcats.twitch.TwitchManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Экран настроек: Mod Menu -> Config или команда /phantomcat config. */
public class PtcConfigScreen extends Screen {

    private static final String[] TABS = {"Twitch", "Коты", "Прочее"};

    private final Screen parent;
    private int tab;

    private EditBox clientIdField;
    private EditBox accessTokenField;
    private EditBox refreshTokenField;
    private EditBox rewardTitleField;
    private EditBox rewardIdField;
    private EditBox lifetimeField;
    private EditBox maxCatsField;
    private EditBox maxDistanceField;

    private String message = "";
    private int messageColor = 0xFFFFFF;
    private long messageUntil;

    public PtcConfigScreen(Screen parent) {
        super(Component.literal("Phantom Twitch Cats — настройки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PtcConfig cfg = ConfigManager.get();
        int cx = this.width / 2;

        clientIdField = null;
        accessTokenField = null;
        refreshTokenField = null;
        rewardTitleField = null;
        rewardIdField = null;
        lifetimeField = null;
        maxCatsField = null;
        maxDistanceField = null;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> switchTab(idx))
                    .bounds(cx - 158 + i * 106, 8, 102, 20).build());
        }

        if (tab == 0) {
            addRenderableWidget(Button.builder(Component.literal("Открыть сайт для получения токена"), b -> {
                dev.phantomtwitchcats.twitch.TwitchAuth.openBrowser("https://twitchtokengenerator.com/");
                flash("Сайт открыт в браузере — авторизуйтесь и скопируйте 3 значения ниже", 0xFFFFFF);
            }).bounds(cx - 158, 44, 316, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Подключиться"), b -> {
                applyFields();
                TwitchManager.get().connectNow();
                flash("Подключаюсь…", 0xFFFFFF);
            }).bounds(cx - 158, 68, 155, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Отключить"), b -> {
                TwitchManager.get().disconnect(true);
                flash("Twitch отключён", 0xFFFFFF);
            }).bounds(cx + 3, 68, 155, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Очистить токены"), b -> {
                TwitchManager.get().logout();
                clientIdField.setValue("");
                accessTokenField.setValue("");
                refreshTokenField.setValue("");
                flash("Токены Twitch удалены", 0xFFFFFF);
            }).bounds(cx - 110, 92, 220, 20).build());

            clientIdField = field(cx - 20, 116, 195, cfg.clientId);
            accessTokenField = field(cx - 20, 140, 195, cfg.accessToken);
            refreshTokenField = field(cx - 20, 164, 195, cfg.refreshToken);
            rewardTitleField = field(cx - 20, 194, 195, cfg.rewardTitle);
            rewardIdField = field(cx - 20, 218, 195, cfg.rewardId);

            addRenderableWidget(Button.builder(Component.literal("Загрузить список наград с канала"), b -> {
                applyFields();
                flash("Загружаю список наград…", 0xFFFFFF);
                TwitchManager.get().loadRewards(
                        rewards -> {
                            if (rewards.isEmpty()) {
                                flash("Наград не найдено — создайте награду на канале", 0xFFAA00);
                            } else if (this.minecraft != null) {
                                this.minecraft.setScreen(new RewardPickerScreen(this, rewards));
                            }
                        },
                        error -> flash("Ошибка: " + error, 0xFF5555));
            }).bounds(cx - 110, 244, 220, 20).build());
        } else if (tab == 1) {
            lifetimeField = field(cx - 40, 44, 195, String.valueOf(cfg.lifetimeMinutes));
            maxCatsField = field(cx - 40, 74, 195, String.valueOf(cfg.maxCats));
            maxDistanceField = field(cx - 40, 104, 195, String.valueOf(cfg.maxDistance));

            toggle(140, "Один кот на зрителя", () -> cfg.oneCatPerViewer, v -> cfg.oneCatPerViewer = v);
            toggle(164, "Котята («мини»)", () -> cfg.allowKittens, v -> cfg.allowKittens = v);
            toggle(188, "Случайный окрас без выбора", () -> cfg.randomColorWhenUnspecified,
                    v -> cfg.randomColorWhenUnspecified = v);
            toggle(212, "Имена зрителей над котами", () -> cfg.showNames, v -> cfg.showNames = v);
        } else {
            toggle(44, "Автопосадка рядом со стримером", () -> cfg.autoSit, v -> cfg.autoSit = v);
            toggle(68, "Записывать призыв кота в лог (не в чат)", () -> cfg.announceSpawns, v -> cfg.announceSpawns = v);
            toggle(92, "Звуки фантомных котов (только у вас)", () -> cfg.localSounds, v -> cfg.localSounds = v);
            toggle(116, "HUD-строка состояния (сверху слева)", () -> cfg.showHud, v -> cfg.showHud = v);
        }

        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(cx - 60, this.height - 28, 120, 20).build());
    }

    private EditBox field(int x, int y, int width, String initial) {
        EditBox f = new EditBox(this.font, x, y, width, 18, Component.empty());
        f.setMaxLength(256);
        f.setValue(initial == null ? "" : initial);
        addRenderableWidget(f);
        return f;
    }

    private void toggle(int y, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(toggleText(label, get.get()), b -> {
            set.accept(!get.get());
            b.setMessage(toggleText(label, get.get()));
        }).bounds(this.width / 2 - 110, y, 220, 20).build());
    }

    private static Component toggleText(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ВКЛ" : "ВЫКЛ"));
    }

    private void switchTab(int idx) {
        applyFields();
        this.tab = idx;
        this.rebuildWidgets();
    }

    private void applyFields() {
        PtcConfig cfg = ConfigManager.get();
        if (clientIdField != null) cfg.clientId = clientIdField.getValue().trim();
        if (accessTokenField != null) cfg.accessToken = accessTokenField.getValue().trim();
        if (refreshTokenField != null) cfg.refreshToken = refreshTokenField.getValue().trim();
        if (rewardTitleField != null) cfg.rewardTitle = rewardTitleField.getValue().trim();
        if (rewardIdField != null) cfg.rewardId = rewardIdField.getValue().trim();
        if (lifetimeField != null) cfg.lifetimeMinutes = parseInt(lifetimeField.getValue(), cfg.lifetimeMinutes);
        if (maxCatsField != null) cfg.maxCats = parseInt(maxCatsField.getValue(), cfg.maxCats);
        if (maxDistanceField != null) cfg.maxDistance = parseDouble(maxDistanceField.getValue(), cfg.maxDistance);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void flash(String text, int color) {
        this.message = text;
        this.messageColor = color;
        this.messageUntil = System.currentTimeMillis() + 6000;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int cx = this.width / 2;

        if (tab == 0) {
            TwitchManager tm = TwitchManager.get();
            context.centeredText(this.font,
                    Component.literal("Статус: " + tm.status()), cx, 34,
                    tm.isConnected() ? 0x55FF55 : 0xFF5555);
            drawLabel(context, "Client ID", 116);
            drawLabel(context, "Access Token", 140);
            drawLabel(context, "Refresh Token (необязательно)", 164);
            drawLabel(context, "Награда: название", 194);
            drawLabel(context, "Награда: ID", 218);
            context.centeredText(this.font,
                    Component.literal("1) Открыть сайт  2) Авторизоваться  3) Скопировать 3 значения сюда"),
                    cx, 270, 0xAAAAAA);
            context.centeredText(this.font,
                    Component.literal("ID награды надёжнее названия — переименование награды не сломает мод"),
                    cx, 282, 0x888888);
        } else if (tab == 1) {
            drawLabel(context, "Время жизни (мин)", 44);
            drawLabel(context, "Макс. котов", 74);
            drawLabel(context, "Дистанция (блоков)", 104);
        } else {
            String path = FabricLoader.getInstance().getConfigDir()
                    .resolve("phantom-twitch-cats.json").toString();
            context.centeredText(this.font,
                    Component.literal("Алиасы окрасов и слова «мини» редактируются в файле:"), cx, 180, 0xAAAAAA);
            context.centeredText(this.font, Component.literal(path), cx, 192, 0x888888);
            context.centeredText(this.font,
                    Component.literal("После правки файла: /phantomcat reload"), cx, 204, 0x888888);
        }

        if (!message.isEmpty() && System.currentTimeMillis() < messageUntil) {
            context.centeredText(this.font, Component.literal(message),
                    cx, this.height - 44, messageColor);
        }
    }

    private void drawLabel(GuiGraphicsExtractor context, String text, int y) {
        context.text(this.font, Component.literal(text),
                this.width / 2 - 158, y + 5, 0xE0E0E0);
    }

    @Override
    public void onClose() {
        applyFields();
        ConfigManager.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        applyFields();
        ConfigManager.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

/** Выбор Channel Point Reward из списка наград канала. */
class RewardPickerScreen extends Screen {

    private static final int VISIBLE = 8;

    private final Screen parent;
    private final List<Helix.RewardInfo> rewards;
    private int offset;

    RewardPickerScreen(Screen parent, List<Helix.RewardInfo> rewards) {
        super(Component.literal("Выберите награду Twitch"));
        this.parent = parent;
        this.rewards = rewards;
    }

    @Override
    protected void init() {
        int row = 0;
        for (int i = offset; i < rewards.size() && row < VISIBLE; i++, row++) {
            Helix.RewardInfo r = rewards.get(i);
            String label = r.title() + " — " + r.cost() + " поинтов";
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                ConfigManager.get().rewardId = r.id();
                ConfigManager.get().rewardTitle = r.title();
                ConfigManager.save();
                onClose();
            }).bounds(this.width / 2 - 120, 36 + row * 24, 240, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, rewards.size() - VISIBLE);
        int old = offset;
        offset = Mth.clamp(offset + (verticalAmount < 0 ? 1 : -1), 0, max);
        if (offset != old) {
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        if (offset > 0) {
            context.centeredText(this.font, Component.literal("▲ ещё"),
                    this.width / 2, 26, 0xAAAAAA);
        }
        if (offset + VISIBLE < rewards.size()) {
            context.centeredText(this.font, Component.literal("▼ ещё"),
                    this.width / 2, this.height - 42, 0xAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
