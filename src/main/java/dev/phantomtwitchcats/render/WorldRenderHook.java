package dev.phantomtwitchcats.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.PhantomCat;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ручной рендер фантомных котов. Сигнатуры подтверждены реальным исходником
 * (LevelRenderContext.java из fabric-api, EntityRenderDispatcher из mcsrc.dev
 * для 26.2 — архитектура рендера сущностей в 26.x двухфазная:
 *   1) extractEntity(entity, partialTicks)  -> EntityRenderState (снимок данных)
 *   2) submit(state, cameraRenderState, dx, dy, dz, poseStack, submitNodeCollector)
 *      -> кладёт геометрию в очередь на отрисовку (SubmitNodeCollector),
 *         больше НЕТ прямого render() с MultiBufferSource/light для сущностей.
 *
 * Единственное упрощение: partialTicks захардкожен в 1.0f, так как в
 * LevelRenderContext нет метода tickDelta()/deltaTracker() — реальный
 * publicly-доступный источник partial tick не подтверждён. Из-за этого
 * движение котов может выглядеть чуть менее плавным (без интерполяции
 * между тиками), но это чисто косметическая мелочь.
 */
public final class WorldRenderHook {

    private static int lastLoggedCatCount = -1;

    /** Снимки котов, собранные на фазе извлечения — используются на фазе отрисовки. */
    private record Pending(net.minecraft.client.renderer.entity.state.EntityRenderState state,
                            double dx, double dy, double dz) {
    }

    private static final List<Pending> pending = new java.util.ArrayList<>();

    private WorldRenderHook() {
    }

    /**
     * Двухфазная схема (подтверждено реальным примером Fabric API):
     *  1) END_EXTRACTION (LevelExtractionContext — БЕЗ poseStack()/submitNodeCollector()):
     *     тут только extractEntity() — читаем данные из мира, снимаем EntityRenderState.
     *  2) AFTER_TRANSLUCENT_TERRAIN (LevelRenderContext — С poseStack()/submitNodeCollector()):
     *     тут submit() — кладём снятые снимки в очередь на отрисовку.
     */
    public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(ctx -> {
            Minecraft client = Minecraft.getInstance();
            ClientLevel world = client.level;
            pending.clear();
            if (world == null) {
                return;
            }

            float tickDelta = 1.0f;
            Vec3 camPos = ctx.levelState().cameraRenderState.pos;
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

            List<PhantomCat> cats = CatManager.get().active();
            if (cats.size() != lastLoggedCatCount) {
                dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                        "[DEBUG] Активных котов: {}, camPos={}", cats.size(), camPos);
                lastLoggedCatCount = cats.size();
            }

            for (PhantomCat cat : cats) {
                PhantomCatEntity e = cat.entity();
                if (e == null || e.level() != world) {
                    continue;
                }

                Vec3 pos = e.getPosition(tickDelta);
                double dx = pos.x - camPos.x;
                double dy = pos.y - camPos.y;
                double dz = pos.z - camPos.z;
                if (dx * dx + dy * dy + dz * dz > 90.0 * 90.0) {
                    continue;
                }

                var state = dispatcher.extractEntity(e, tickDelta);
                pending.add(new Pending(state, dx, dy, dz));
            }
        });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> {
            if (pending.isEmpty()) {
                return;
            }
            PoseStack matrices = ctx.poseStack();
            SubmitNodeCollector collector = ctx.submitNodeCollector();
            if (matrices == null || collector == null) {
                dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.warn(
                        "[DEBUG] render-фаза: matrices или collector == null! matrices={}, collector={}",
                        matrices, collector);
                return;
            }
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            var camera = ctx.levelState().cameraRenderState;
            for (Pending p : pending) {
                dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                        "[DEBUG] submit(): dx={}, dy={}, dz={}, state={}", p.dx(), p.dy(), p.dz(), p.state());
                dispatcher.submit(p.state(), camera, p.dx(), p.dy(), p.dz(), matrices, collector);
            }
        });
    }
}
