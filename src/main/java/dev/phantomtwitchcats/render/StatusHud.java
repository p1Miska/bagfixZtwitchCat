package dev.phantomtwitchcats.render;

import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import dev.phantomtwitchcats.twitch.TwitchManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class StatusHud {

    private StatusHud() {
    }

    // ВНИМАНИЕ: HUD API в Fabric был полностью переписан в районе 1.21.6 и снова
    // подвергся правкам имён под официальные маппинги в 26.1. Сигнатура ниже
    // (GuiGraphicsExtractor, DeltaTracker) соответствует актуальной документации Fabric,
    // но стоит свериться при сборке — это самая рискованная часть миграции.
    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("phantomtwitchcats", "status_hud"),
                (context, deltaTracker) -> {
                    PtcConfig cfg = ConfigManager.get();
                    if (!cfg.showHud) return;
                    Minecraft client = Minecraft.getInstance();
                    if (client.options.hideGui || client.level == null || client.player == null) return;

                    String text = "PhantomCats: Twitch " + TwitchManager.get().shortStatus()
                            + " | котов: " + CatManager.get().count();
                    context.text(client.font, text, 4, 4, 0xFFFFFF);
                });
    }
}
