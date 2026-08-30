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

    private static boolean loggedOnce = false;

    private WorldRenderHook() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> {
            Minecraft client = Minecraft.getInstance();
            ClientLevel world = client.level;
            if (world == null) {
                return;
            }

            PoseStack matrices = ctx.poseStack();
            SubmitNodeCollector collector = ctx.submitNodeCollector();
            if (matrices == null || collector == null) {
                if (!loggedOnce) {
                    dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.warn(
                            "[DEBUG] matrices или collector == null! matrices={}, collector={}", matrices, collector);
                    loggedOnce = true;
                }
                return;
            }

            float tickDelta = 1.0f;
            Vec3 camPos = ctx.levelState().cameraRenderState.pos;
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

            List<PhantomCat> cats = CatManager.get().active();
            if (!loggedOnce) {
                dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                        "[DEBUG] render hook сработал. Активных котов: {}, camPos={}", cats.size(), camPos);
                loggedOnce = true;
            }

            for (PhantomCat cat : cats) {
                PhantomCatEntity e = cat.entity();
                if (e == null || e.level() != world) {
                    dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                            "[DEBUG] кот пропущен: entity={}, e.level()={}, world={}",
                            e, e == null ? "null" : e.level(), world);
                    continue;
                }

                Vec3 pos = e.getPosition(tickDelta);
                double dx = pos.x - camPos.x;
                double dy = pos.y - camPos.y;
                double dz = pos.z - camPos.z;
                if (dx * dx + dy * dy + dz * dz > 90.0 * 90.0) {
                    dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                            "[DEBUG] кот отсечён по дистанции: pos={}, camPos={}, dist2={}",
                            pos, camPos, dx * dx + dy * dy + dz * dz);
                    continue;
                }

                dev.phantomtwitchcats.PhantomTwitchCatsClient.LOGGER.info(
                        "[DEBUG] отправляю кота на отрисовку: pos={}, dx={}, dy={}, dz={}", pos, dx, dy, dz);

                EntityRenderState state = dispatcher.extractEntity(e, tickDelta);
                dispatcher.submit(state, ctx.levelState().cameraRenderState, dx, dy, dz, matrices, collector);
            }
        });
    }
}
