package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Клиентский «мозг» фантомного кота: следование, посадка, взгляды,
 * прыжки, плавание, телепорт при превышении дистанции.
 */
public final class PhantomCat {

    private final String viewerId;
    private final String displayName;
    private final String resolvedVariantId;
    private final boolean baby;
    private final int entityId;
    private final double slotAngleDeg; // «личное место» вокруг игрока

    private PhantomCatEntity entity;
    private long lifeTicks;
    private boolean expired;
    private boolean tickBroken;

    private boolean sitting;
    private int stateTimer;
    private int lookTimer;
    private float headYawTarget;
    private float headPitchTarget;

    private boolean wandering;
    private double wanderX, wanderZ;

    private int stuckCheckTimer;
    private double stuckLastX, stuckLastZ;

    PhantomCat(PhantomCatEntity entity, CatRequest request, String resolvedVariantId,
               long lifeTicks, double slotAngleDeg) {
        this.entity = entity;
        this.viewerId = request.viewerId();
        this.displayName = request.displayName();
        this.resolvedVariantId = resolvedVariantId;
        this.baby = request.baby();
        this.lifeTicks = lifeTicks;
        this.entityId = entity.getId();
        this.slotAngleDeg = slotAngleDeg;
        this.stateTimer = 80;
        this.lookTimer = 20;
        this.headYawTarget = entity.getYRot();
        this.stuckLastX = entity.getX();
        this.stuckLastZ = entity.getZ();
    }

    // ------------------------------------------------------------------

    public PhantomCatEntity entity() { return entity; }
    public String viewerId() { return viewerId; }
    public String displayName() { return displayName; }
    public boolean baby() { return baby; }
    public boolean expired() { return expired; }
    public boolean isSitting() { return sitting; }

    public String prettyVariant() {
        String s = resolvedVariantId == null ? "?" : resolvedVariantId;
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    public String remainingTime() {
        long totalSeconds = Math.max(0, lifeTicks / 20);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    public void toggleSitting() {
        sitting = !sitting;
        stateTimer = sitting ? 200 : 60;
    }

    /** Сущность больше не актуальна (смена мира/выход с сервера) — данные живут дальше. */
    void detach() {
        this.entity = null;
    }

    /** Смерть/респавн/смена мира/сервера: пересоздаём сущность в новом мире, таймер сохраняем. */
    void rebind(ClientLevel world, LocalPlayer player) {
        PhantomCatEntity e = CatFactory.build(world, player, resolvedVariantId, baby, displayName, entityId);
        BlockPos spot = SafeSpotFinder.find(world, player.blockPosition(), 1, 3);
        double x = spot != null ? spot.getX() + 0.5 : player.getX();
        double y = spot != null ? spot.getY() : player.getY();
        double z = spot != null ? spot.getZ() + 0.5 : player.getZ();
        float face = player.getYRot() + 180.0f;
        e.moveTo(x, y, z, face, 0.0f);
        e.setYBodyRot(face);
        e.setYHeadRot(face);
        this.entity = e;
        this.wandering = false;
        this.tickBroken = false;
    }

    void onRemoved(ClientLevel world) {
        if (entity != null) {
            poof(world, 12);
            if (ConfigManager.get().localSounds) {
                world.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.CAT_AMBIENT, SoundSource.NEUTRAL, 0.4f, 0.8f, false);
            }
        }
        entity = null;
    }

    // ------------------------------------------------------------------

    public void tick(ClientLevel world, LocalPlayer player) {
        if (expired || entity == null) return;
        PhantomCatEntity e = this.entity;
        PtcConfig cfg = ConfigManager.get();

        if (--lifeTicks <= 0) {
            expired = true;
            return;
        }
        if (baby && !e.isBaby()) e.setBaby(true);

        double dx = player.getX() - e.getX();
        double dz = player.getZ() - e.getZ();
        double distH = Math.sqrt(dx * dx + dz * dz);

        // Лава или слишком далеко -> телепорт в безопасную точку рядом с игроком
        if (e.isInLava() || distH > cfg.maxDistance) {
            teleportTo(world, player);
            dx = player.getX() - e.getX();
            dz = player.getZ() - e.getZ();
            distH = Math.sqrt(dx * dx + dz * dz);
        }

        // Тик ванильной сущности: анимации лап/хвоста, позы, возраст и т.п.
        if (!tickBroken) {
            try {
                e.tick();
            } catch (Throwable t) {
                tickBroken = true;
                PhantomTwitchCatsClient.LOGGER.warn("Не удалось тикать кота {}: {}", displayName, t.toString());
            }
        }

        // --- сидит / стоит ---
        stateTimer--;
        if (sitting) {
            if (distH > 8.0 || stateTimer <= 0) {
                sitting = false;
                stateTimer = 80 + world.random.nextInt(160);
            }
        } else {
            if (stateTimer <= 0) stateTimer = 100 + world.random.nextInt(300);
            if (cfg.autoSit && distH < 3.5 && world.random.nextInt(400) == 0) {
                sitting = true;
                stateTimer = 100 + world.random.nextInt(400);
            }
        }
        e.setInSittingPose(sitting);

        // --- цель движения ---
        double targetX = e.getX();
        double targetZ = e.getZ();
        double speed = 0;
        if (!sitting) {
            if (distH > 4.0) {
                targetX = player.getX();
                targetZ = player.getZ();
                speed = distH > 12 ? 0.34 : distH > 7 ? 0.27 : 0.20;
            } else if (wandering) {
                targetX = wanderX;
                targetZ = wanderZ;
                speed = 0.15;
                if (e.distanceToSqr(wanderX, e.getY(), wanderZ) < 0.36) wandering = false;
            } else {
                double ang = Math.toRadians(slotAngleDeg);
                targetX = player.getX() + Math.cos(ang) * 2.2;
                targetZ = player.getZ() + Math.sin(ang) * 2.2;
                if (e.distanceToSqr(targetX, e.getY(), targetZ) > 0.6) speed = 0.15;
                if (world.random.nextInt(500) == 0) {
                    wandering = true;
                    wanderX = e.getX() + (world.random.nextDouble() - 0.5) * 4.0;
                    wanderZ = e.getZ() + (world.random.nextDouble() - 0.5) * 4.0;
                }
            }
        }

        double tdx = targetX - e.getX();
        double tdz = targetZ - e.getZ();
        double td = Math.sqrt(tdx * tdx + tdz * tdz);
        double vx = 0, vz = 0;
        boolean moving = false;
        if (speed > 0 && td > 0.35) {
            vx = tdx / td * speed;
            vz = tdz / td * speed;
            moving = true;
        }

        // не наступать в опасное
        if (moving && e.onGround() && SafeSpotFinder.isDangerousAhead(world, e, vx, vz)) {
            vx = 0;
            vz = 0;
            moving = false;
        }

        // --- вертикаль (своя гравитация/плавание) ---
        double vy = e.getDeltaMovement().y;
        if (e.isInWater() || e.isInLava()) {
            vy = 0.18;
        } else if (!e.onGround()) {
            vy = Math.max(vy - 0.08, -3.0);
        } else if (vy < 0) {
            vy = 0;
        }

        e.setDeltaMovement(vx, vy, vz);
        e.move(MoverType.SELF, new Vec3(vx, vy, vz));

        // прыжок, если упёрлись в блок; редкий «игривый» прыжок на месте
        if (moving && e.onGround() && e.horizontalCollision) {
            e.setDeltaMovement(vx, 0.5, vz);
        }
        if (!sitting && e.onGround() && world.random.nextInt(1200) == 0 && distH < 6.0) {
            e.setDeltaMovement(e.getDeltaMovement().x, 0.42, e.getDeltaMovement().z);
        }

        // --- повороты тела ---
        if (moving) {
            float moveYaw = (float) (Mth.atan2(vz, vx) * (180.0 / Math.PI)) - 90.0f;
            e.setYBodyRot(lerpAngle(e.yBodyRot, moveYaw, 0.25f));
        } else {
            e.setYBodyRot(lerpAngle(e.yBodyRot, headYawTarget, sitting ? 0.05f : 0.08f));
        }

        // --- взгляд (на игрока или по сторонам) ---
        lookTimer--;
        if (moving || lookTimer <= 0) {
            if (moving || (distH < 6.0 && world.random.nextFloat() < 0.55f)) {
                aimHeadAt(e, player.getX(), player.getEyeY(), player.getZ());
            } else {
                headYawTarget = e.yBodyRot + (world.random.nextFloat() - 0.5f) * 140.0f;
                headPitchTarget = (world.random.nextFloat() - 0.5f) * 24.0f;
            }
            if (lookTimer <= 0) lookTimer = 30 + world.random.nextInt(90);
        }
        e.setYHeadRot(lerpAngle(e.yHeadRot, headYawTarget, 0.25f));
        e.setXRot(Mth.lerp(0.25f, e.getXRot(), Mth.clamp(headPitchTarget, -30.0f, 30.0f)));

        // --- застревание ---
        if (moving && distH > 6.0) {
            stuckCheckTimer++;
            if (stuckCheckTimer >= 40) {
                if (e.distanceToSqr(stuckLastX, e.getY(), stuckLastZ) < 0.25) {
                    teleportTo(world, player);
                }
                stuckCheckTimer = 0;
                stuckLastX = e.getX();
                stuckLastZ = e.getZ();
            }
        } else {
            stuckCheckTimer = 0;
            stuckLastX = e.getX();
            stuckLastZ = e.getZ();
        }

        // --- мяу ---
        if (cfg.localSounds && world.random.nextInt(800) == 0) {
            world.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.CAT_AMBIENT,
                    SoundSource.NEUTRAL, 0.4f, 0.9f + world.random.nextFloat() * 0.25f, false);
        }
    }

    // ------------------------------------------------------------------

    private void aimHeadAt(PhantomCatEntity e, double x, double y, double z) {
        double ddx = x - e.getX();
        double ddy = y - e.getEyeY();
        double ddz = z - e.getZ();
        double dh = Math.sqrt(ddx * ddx + ddz * ddz);
        headYawTarget = (float) (Mth.atan2(ddz, ddx) * (180.0 / Math.PI)) - 90.0f;
        headPitchTarget = Mth.clamp(
                (float) (-(Mth.atan2(ddy, dh) * (180.0 / Math.PI))), -30.0f, 30.0f);
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + Mth.wrapDegrees(to - from) * t;
    }

    private void teleportTo(ClientLevel world, LocalPlayer player) {
        if (entity == null) return;
        poof(world, 6);
        BlockPos spot = SafeSpotFinder.find(world, player.blockPosition(), 1, 3);
        double x = spot != null ? spot.getX() + 0.5 : player.getX();
        double y = spot != null ? spot.getY() : player.getY();
        double z = spot != null ? spot.getZ() + 0.5 : player.getZ();
        float face = player.getYRot() + 180.0f;
        entity.moveTo(x, y, z, face, 0.0f);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setYBodyRot(face);
        entity.setYHeadRot(face);
        wandering = false;
        poof(world, 6);
    }

    private void poof(ClientLevel world, int count) {
        if (entity == null) return;
        for (int i = 0; i < count; i++) {
            world.addParticle(ParticleTypes.POOF,
                    entity.getX() + (world.random.nextDouble() - 0.5) * 0.6,
                    entity.getY() + 0.2 + world.random.nextDouble() * 0.5,
                    entity.getZ() + (world.random.nextDouble() - 0.5) * 0.6,
                    0, 0.02, 0);
        }
    }
}
