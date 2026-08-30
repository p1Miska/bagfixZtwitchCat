package dev.phantomtwitchcats.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.PhantomCat;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ручной рендер фантомных котов. Подтверждённая двухфазная схема (по реальному
 * примеру из Fabric API): END_EXTRACTION — собрать данные из мира/сущностей,
 * дальше отдельное событие отрисовки с LevelRenderContext (poseStack(),
 * levelState().cameraRenderState.pos — тоже подтверждено).
 *
 * ЕДИНСТВЕННОЕ, что здесь всё ещё не подтверждено реальным исходником —
 * точная сигнатура EntityRenderDispatcher.render(...) в 26.1.2 (старая
 * 9-аргументная версия не существует). Ниже — best-effort вызов через
 * тот же общий паттерн extract->render, что и у CatRenderer/Gui
 * (extractRenderState -> render); если сборка упадёт именно на этих двух
 * строках — нужны реальные сигнатуры класса EntityRenderDispatcher.
 */
public final class WorldRenderHook {

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
            MultiBufferSource consumers = ctx.consumers();
            if (matrices == null || consumers == null) {
                return;
            }

            float tickDelta = ctx.tickDelta();
            Vec3 camPos = ctx.levelState().cameraRenderState.pos;
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

            List<PhantomCat> cats = CatManager.get().active();
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

                float yaw = net.minecraft.util.Mth.rotLerp(tickDelta, e.yRotO, e.getYRot());
                dispatcher.render(e, dx, dy, dz, yaw, tickDelta, matrices, consumers, 15728880);
            }
        });
    }
}
