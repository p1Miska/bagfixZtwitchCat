package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Менеджер фантомных котов. Весь доступ — только из главного потока клиента
 * (тик, рендер, команды, Minecraft.execute для Twitch-событий).
 */
public final class CatManager {

    private static final CatManager INSTANCE = new CatManager();

    public enum SpawnResult { SPAWNED, LIMIT_REACHED, ALREADY_HAS_CAT, NO_WORLD }

    private final List<PhantomCat> cats = new ArrayList<>();
    private int nextEntityId = 900_100_000;
    private boolean wasUsePressed;

    private CatManager() {
    }

    public static CatManager get() {
        return INSTANCE;
    }

    public List<PhantomCat> active() {
        return cats;
    }

    public int count() {
        return cats.size();
    }

    public SpawnResult trySpawn(CatRequest request) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        LocalPlayer player = client.player;
        if (world == null || player == null) return SpawnResult.NO_WORLD;

        PtcConfig cfg = ConfigManager.get();
        if (cats.size() >= cfg.maxCats) return SpawnResult.LIMIT_REACHED;
        if (cfg.oneCatPerViewer && request.viewerId() != null) {
            for (PhantomCat c : cats) {
                if (request.viewerId().equals(c.viewerId())) return SpawnResult.ALREADY_HAS_CAT;
            }
        }

        String variantId = CatFactory.resolveVariantId(world, request.variantId(), request.anyInput());
        PhantomCatEntity entity = CatFactory.build(world, player, variantId,
                request.baby(), request.displayName(), nextEntityId++);

        BlockPos spot = SafeSpotFinder.find(world, player.blockPosition(), 1, 3);
        double x = spot != null ? spot.getX() + 0.5 : player.getX();
        double y = spot != null ? spot.getY() : player.getY();
        double z = spot != null ? spot.getZ() + 0.5 : player.getZ();
        float face = player.getYRot() + 180.0f;
        entity.moveTo(x, y, z, face, 0.0f);
        entity.setYBodyRot(face);
        entity.setYHeadRot(face);

        double slotAngle = (cats.size() * 61.0) % 360.0;
        PhantomCat cat = new PhantomCat(entity, request, variantId,
                cfg.lifetimeMinutes * 60L * 20L, slotAngle);
        cats.add(cat);

        for (int i = 0; i < 10; i++) {
            world.addParticle(ParticleTypes.POOF,
                    x + (world.random.nextDouble() - 0.5) * 0.6,
                    y + 0.2 + world.random.nextDouble() * 0.6,
                    z + (world.random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (cfg.localSounds) {
            world.playLocalSound(x, y, z, SoundEvents.CAT_AMBIENT, SoundSource.NEUTRAL, 0.5f, 1.0f, false);
        }

        boolean kitten = request.baby() && cfg.allowKittens;
        if (cfg.announceSpawns) {
            player.displayClientMessage(Component.literal("» ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(request.displayName()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(kitten ? " призвал фантомного котёнка!" : " призвал фантомного кота!")
                            .withStyle(ChatFormatting.AQUA)), true);
        }
        PhantomTwitchCatsClient.LOGGER.info("Призван фантомный кот {} ({}, котёнок: {})",
                request.displayName(), variantId, kitten);
        return SpawnResult.SPAWNED;
    }

    public void tick(Minecraft client) {
        ClientLevel world = client.level;
        LocalPlayer player = client.player;
        if (world == null || player == null) return;

        if (!cats.isEmpty()) {
            List<PhantomCat> expired = null;
            for (PhantomCat cat : cats) {
                PhantomCatEntity e = cat.entity();
                if (e == null || e.level() != world || e.isRemoved()) {
                    cat.rebind(world, player); // смена мира/измерения/респавн: пересоздаём, таймер живёт
                }
                cat.tick(world, player);
                if (cat.expired()) {
                    if (expired == null) expired = new ArrayList<>();
                    expired.add(cat);
                }
            }
            if (expired != null) {
                for (PhantomCat cat : expired) {
                    cats.remove(cat);
                    cat.onRemoved(world);
                }
            }
        }
        handlePetting(client, world, player);
    }

    public void onWorldReady() {
        Minecraft client = Minecraft.getInstance();
        if (!cats.isEmpty() && client.player != null && ConfigManager.get().announceSpawns) {
            client.player.displayClientMessage(Component.literal("» Фантомных котов с вами: " + cats.size())
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    public void onDisconnect() {
        for (PhantomCat cat : cats) cat.detach();
    }

    public boolean removeByName(String name) {
        for (PhantomCat cat : cats) {
            if (cat.displayName().equalsIgnoreCase(name.trim())) {
                cats.remove(cat);
                Minecraft client = Minecraft.getInstance();
                if (client.level != null) cat.onRemoved(client.level);
                return true;
            }
        }
        return false;
    }

    public int clearAll() {
        Minecraft client = Minecraft.getInstance();
        int n = cats.size();
        for (PhantomCat cat : new ArrayList<>(cats)) {
            cats.remove(cat);
            if (client.level != null) cat.onRemoved(client.level);
        }
        return n;
    }

    /**
     * Чисто локальное взаимодействие: клик ЛКМ «по коту» (когда прицел в пустоту,
     * т.е. серверное использование не срабатывает) — посадить/поднять кота.
     */
    private void handlePetting(Minecraft client, ClientLevel world, LocalPlayer player) {
        boolean pressed = client.options.keyUse.isDown();
        boolean clicked = pressed && !wasUsePressed;
        wasUsePressed = pressed;
        if (!clicked || cats.isEmpty()) return;

        HitResult target = client.hitResult;
        if (target != null && target.getType() != HitResult.Type.MISS) return;

        Vec3 origin = player.getEyePosition();
        Vec3 end = origin.add(player.getViewVector(1.0f).scale(4.5));
        PhantomCat best = null;
        double bestDist = Double.MAX_VALUE;
        for (PhantomCat cat : cats) {
            PhantomCatEntity e = cat.entity();
            if (e == null) continue;
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.2).clip(origin, end);
            if (hit.isPresent()) {
                double d = origin.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = cat;
                }
            }
        }
        if (best != null) {
            best.toggleSitting();
            PhantomCatEntity e = best.entity();
            if (ConfigManager.get().localSounds && e != null) {
                world.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.CAT_PURR,
                        SoundSource.NEUTRAL, 0.6f, 1.0f, false);
            }
        }
    }
}
