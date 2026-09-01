package dev.phantomtwitchcats.render;

/**
 * ВРЕМЕННО ОТКЛЮЧЕНО. Метод ScreenEvents.afterRender(Screen) — раньше
 * стабильный годами (1.17-1.21) — в 26.1.2 не резолвится ("cannot find
 * symbol", хотя сам класс ScreenEvents найден). Судя по всему, Fabric Screen
 * API v1 тоже переименовали при переходе на 26.1, как и рендер-события.
 * Гадать не стал, чтобы не плодить новые ошибки компиляции — список котов
 * в паузе временно отключён.
 *
 * Чтобы включить: нужны актуальные сигнатуры класса
 * net.fabricmc.fabric.api.client.screen.v1.ScreenEvents для 26.1.2/26.2
 * (тем же способом, каким доставали LevelRenderEvents/LevelRenderContext —
 * decompiled fabric-api jar или mcsrc.dev-подобный источник).
 */
public final class CatPauseOverlay {

    private CatPauseOverlay() {
    }

    public static void register() {
        // no-op — см. комментарий класса
    }
}
