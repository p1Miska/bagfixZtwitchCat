package dev.phantomtwitchcats.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.CatRequest;
import dev.phantomtwitchcats.cat.InputParser;
import dev.phantomtwitchcats.cat.PhantomCat;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfigScreen;
import dev.phantomtwitchcats.twitch.TwitchManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Locale;

/** Локальные клиентские команды — работают на любом сервере, ничего не отправляют серверу. */
public final class PhantomCatCommands {

    private static final SuggestionProvider<FabricClientCommandSource> ARG_SUGGESTIONS = (context, builder) -> {
        for (String s : List.of("чер", "бел", "рыж", "сер", "сиам", "брит", "калико", "перс", "рэгд", "джел",
                "мини", "чер мини", "бел мини", "рыж мини", "сер мини", "сиам мини")) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    };

    private PhantomCatCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("phantomcat")
                        .then(ClientCommandManager.literal("spawn")
                                .then(ClientCommandManager.argument("user", StringArgumentType.word())
                                        .executes(ctx -> spawn(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "user"), ""))
                                        .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                                .suggests(ARG_SUGGESTIONS)
                                                .executes(ctx -> spawn(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "user"),
                                                        StringArgumentType.getString(ctx, "args"))))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("user", StringArgumentType.word())
                                        .executes(ctx -> remove(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "user")))))
                        .then(ClientCommandManager.literal("removeall")
                                .executes(ctx -> {
                                    int n = CatManager.get().clearAll();
                                    ctx.getSource().sendFeedback(Component.literal("Удалено фантомных котов: " + n)
                                            .withStyle(ChatFormatting.GREEN));
                                    return n;
                                }))
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> {
                                    List<PhantomCat> cats = CatManager.get().active();
                                    if (cats.isEmpty()) {
                                        ctx.getSource().sendFeedback(Component.literal("Активных фантомных котов нет."));
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(Component.literal("Фантомные коты (" + cats.size() + "):"));
                                    for (PhantomCat c : cats) {
                                        ctx.getSource().sendFeedback(Component.literal(" - " + c.displayName()
                                                + " [" + c.prettyVariant() + (c.baby() ? ", котёнок" : "") + "] "
                                                + (c.isSitting() ? "сидит" : "гуляет")
                                                + ", осталось " + c.remainingTime()));
                                    }
                                    return cats.size();
                                }))
                        .then(ClientCommandManager.literal("reload")
                                .executes(ctx -> {
                                    ConfigManager.load();
                                    ctx.getSource().sendFeedback(Component.literal("Конфигурация перезагружена.")
                                            .withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("config")
                                .executes(ctx -> {
                                    Minecraft client = Minecraft.getInstance();
                                    client.execute(() -> client.setScreen(new PtcConfigScreen(null)));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("twitch")
                                .then(ClientCommandManager.literal("status")
                                        .executes(ctx -> {
                                            ctx.getSource().sendFeedback(Component.literal("Twitch: "
                                                    + TwitchManager.get().status()));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("connect")
                                        .executes(ctx -> {
                                            TwitchManager.get().connectNow();
                                            ctx.getSource().sendFeedback(Component.literal("Подключаюсь к Twitch…"));
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("disconnect")
                                        .executes(ctx -> {
                                            TwitchManager.get().disconnect(false);
                                            return 1;
                                        })))
        ));
    }

    private static int spawn(FabricClientCommandSource source, String user, String args)
            throws CommandSyntaxException {
        CatRequest request = InputParser.parse(args, user, "cmd:" + user.toLowerCase(Locale.ROOT));
        CatManager.SpawnResult result = CatManager.get().trySpawn(request);
        switch (result) {
            case SPAWNED -> source.sendFeedback(Component.literal("Призван фантомный кот: " + user)
                    .withStyle(ChatFormatting.GREEN));
            case ALREADY_HAS_CAT -> source.sendError(Component.literal(
                    "У " + user + " уже есть активный кот (при покупке через Twitch поинты вернулись бы зрителю)."));
            case LIMIT_REACHED -> source.sendError(Component.literal(
                    "Достигнут лимит фантомных котов (" + ConfigManager.get().maxCats + ")."));
            case NO_WORLD -> source.sendError(Component.literal("Сейчас нет активного мира."));
        }
        return result == CatManager.SpawnResult.SPAWNED ? 1 : 0;
    }

    private static int remove(FabricClientCommandSource source, String user) throws CommandSyntaxException {
        boolean removed = CatManager.get().removeByName(user);
        if (removed) {
            source.sendFeedback(Component.literal("Кот " + user + " удалён.").withStyle(ChatFormatting.GREEN));
        } else {
            source.sendError(Component.literal("Кот с именем " + user + " не найден."));
        }
        return removed ? 1 : 0;
    }
}
