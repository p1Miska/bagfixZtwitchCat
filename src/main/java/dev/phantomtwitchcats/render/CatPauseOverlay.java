package dev.phantomtwitchcats.render;

import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.PhantomCat;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Список активных фантомных котов (ник + оставшееся время) в левом верхнем
 * углу экрана паузы (ESC). Реализовано через Fabric Screen API v1
 * (ScreenEvents.AFTER_INIT + afterRender) — без Mixin, поэтому не зависит
 * от внутренней структуры GameMenuScreen/PauseScreen.
 *
 * ВНИМАНИЕ: сигнатуры Screen API v1 (ScreenEvents.AFTER_INIT, afterRender)
 * взяты по памяти как исторически стабильные — они не проверены реальным
 * исходником для 26.1.2, в отличие от остального кода в этом моде. Если
 * сборка упадёт именно на этом файле — нужны точные сигнатуры класса
 * net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.
 */
public final class CatPauseOverlay {

    private CatPauseOverlay() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                List<PhantomCat> cats = CatManager.get().active();
                if (cats.isEmpty()) {
                    return;
                }
                int x = 6;
                int y = 6;
                graphics.text(client.font, Component.literal("Фантомные коты (" + cats.size() + "):"),
                        x, y, 0xFFFFFF);
                y += 12;
                for (PhantomCat cat : cats) {
                    String line = cat.displayName() + " — " + cat.remainingTime();
                    graphics.text(client.font, Component.literal(line), x, y, 0xC0C0C0);
                    y += 10;
                }
            });
        });
    }
}
