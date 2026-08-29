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
 * Ручной рендер фантомных котов после обычных сущностей.
 * Коты не добавляются в мир, поэтому их нужно рисовать самим.
 *
 * ВНИМАНИЕ: WorldRenderEvents -> LevelRenderEvents, WorldRenderContext ->
 * LevelRenderContext, matrixStack() -> poseStack() — подтверждено официальной
 * документацией Fabric (docs.fabricmc.net/develop/rendering/world, июль 2026).
 * Позиция камеры теперь берётся через ctx.levelState().cameraRenderState.pos
 * вместо ctx.camera().getPosition() — тоже подтверждено тем же источником.
 * Метод ctx.consumers() НЕ подтверждён явно (не был в примере) — если сборка
 * упадёт именно на нём, это первое, что нужно проверить/заменить.
 * LevelRenderer.getLightColor(...) заменён на статичный full-bright свет
 * (15728880) — не подтверждена реальная замена статического метода освещения,
 * так что коты будут всегда ярко освещены вместо честного локального света.
 */
public final class WorldRenderHook {

    private static final int FULL_BRIGHT_LIGHT = 15728880;

    private WorldRenderHook() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_ENTITIES.register(ctx -> {
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
                dispatcher.render(e, dx, dy, dz, yaw, tickDelta, matrices, consumers, FULL_BRIGHT_LIGHT);
            }
        });
    }
}
