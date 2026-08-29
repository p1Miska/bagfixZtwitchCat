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
    private EditBox clientSecretField;
    private EditBox rewardTitleField;
    private EditBox rewardIdField;
    private EditBox lifetimeField;
    private EditBox maxCatsField;
    private EditBox maxDistanceField;
    private EditBox authPortField;

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
        clientSecretField = null;
        rewardTitleField = null;
        rewardIdField = null;
        lifetimeField = null;
        maxCatsField = null;
        maxDistanceField = null;
        authPortField = null;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> switchTab(idx))
                    .bounds(cx - 158 + i * 106, 8, 102, 20).build());
        }

        if (tab == 0) {
            addRenderableWidget(Button.builder(Component.literal("Авторизовать Twitch"), b -> {
                applyFields();
                PtcConfig c = ConfigManager.get();
                if (c.clientId.isBlank() || c.clientSecret.isBlank()) {
                    flash("Сначала укажите Client ID и Client Secret", 0xFF5555);
                } else {
                    flash("Открываю браузер для авторизации…", 0xFFFFFF);
                    TwitchManager.get().startAuthorization();
                }
            }).bounds(cx - 158, 44, 155, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Отключить Twitch"), b -> {
                TwitchManager.get().disconnect(true);
                flash("Twitch отключён", 0xFFFFFF);
            }).bounds(cx + 3, 44, 155, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Выйти из Twitch (удалить токены)"), b -> {
                TwitchManager.get().logout();
                flash("Токены Twitch удалены", 0xFFFFFF);
            }).bounds(cx - 110, 68, 220, 20).build());

            clientIdField = field(cx - 40, 92, 195, cfg.clientId);
            clientSecretField = field(cx - 40, 116, 195, cfg.clientSecret);
            clientSecretField.setFormatter((text, first) ->
                    net.minecraft.util.FormattedCharSequence.forward(
                            "•".repeat(Math.max(0, Math.min(64, text.length() - first))),
                            net.minecraft.network.chat.Style.EMPTY));
            rewardTitleField = field(cx - 40, 140, 195, cfg.rewardTitle);
            rewardIdField = field(cx - 40, 164, 195, cfg.rewardId);

            addRenderableWidget(Button.builder(Component.literal("Загрузить награды из Twitch"), b -> {
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
            }).bounds(cx - 110, 188, 220, 20).build());
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
            authPortField = field(cx - 40, 44, 195, String.valueOf(cfg.authPort));

            toggle(76, "Автопосадка рядом со стримером", () -> cfg.autoSit, v -> cfg.autoSit = v);
            toggle(100, "Сообщения о призыве в чате", () -> cfg.announceSpawns, v -> cfg.announceSpawns = v);
            toggle(124, "Звуки фантомных котов (только у вас)", () -> cfg.localSounds, v -> cfg.localSounds = v);
            toggle(148, "HUD-строка состояния", () -> cfg.showHud, v -> cfg.showHud = v);
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
        if (clientSecretField != null) cfg.clientSecret = clientSecretField.getValue().trim();
        if (rewardTitleField != null) cfg.rewardTitle = rewardTitleField.getValue().trim();
        if (rewardIdField != null) cfg.rewardId = rewardIdField.getValue().trim();
        if (lifetimeField != null) cfg.lifetimeMinutes = parseInt(lifetimeField.getValue(), cfg.lifetimeMinutes);
        if (maxCatsField != null) cfg.maxCats = parseInt(maxCatsField.getValue(), cfg.maxCats);
        if (maxDistanceField != null) cfg.maxDistance = parseDouble(maxDistanceField.getValue(), cfg.maxDistance);
        if (authPortField != null) cfg.authPort = parseInt(authPortField.getValue(), cfg.authPort);
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
                    Component.literal("Twitch: " + tm.status()), cx, 34,
                    tm.isConnected() ? 0x55FF55 : 0xFF5555);
            drawLabel(context, "Client ID", 92);
            drawLabel(context, "Client Secret", 116);
            drawLabel(context, "Награда: название", 140);
            drawLabel(context, "Награда: ID", 164);
            context.centeredText(this.font,
                    Component.literal("Redirect URL в Twitch-приложении: http://localhost:"
                            + ConfigManager.get().authPort), cx, 216, 0xAAAAAA);
            context.centeredText(this.font,
                    Component.literal("ID надёжнее названия — переименование награды не сломает мод"),
                    cx, 228, 0x888888);
        } else if (tab == 1) {
            drawLabel(context, "Время жизни (мин)", 44);
            drawLabel(context, "Макс. котов", 74);
            drawLabel(context, "Дистанция (блоков)", 104);
        } else {
            drawLabel(context, "Порт OAuth", 44);
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
