package dev.phantomtwitchcats.render;

import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.cat.PhantomCat;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ручной рендер фантомных котов после обычных сущностей.
 * Коты не добавляются в мир, поэтому их нужно рисовать самим —
 * ванильным рендерером котов (модель, анимации, ошейник, метка с именем).
 */
public final class WorldRenderHook {

    private WorldRenderHook() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            Minecraft client = Minecraft.getInstance();
            ClientLevel world = client.level;
            if (world == null) {
                return;
            }

            PoseStack matrices = ctx.matrixStack();
            MultiBufferSource consumers = ctx.consumers();
            Camera camera = ctx.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            float tickDelta = ctx.tickDelta();
            Vec3 camPos = camera.getPosition();
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

                float yaw = Mth.rotLerp(tickDelta, e.yRotO, e.getYRot());
                int light = LevelRenderer.getLightColor(world, e.blockPosition());
                dispatcher.render(e, dx, dy, dz, yaw, tickDelta, matrices, consumers, light);
            }
        });
    }
}
