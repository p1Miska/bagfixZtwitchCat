package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;

import java.util.Set;

/** Поиск безопасной позиции для телепорта/спавна кота рядом с игроком. */
public final class SafeSpotFinder {

    private static final Set<Block> DANGEROUS_BLOCKS = Set.of(
            Blocks.LAVA, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.MAGMA_BLOCK, Blocks.CACTUS,
            Blocks.SWEET_BERRY_BUSH, Blocks.POINTED_DRIPSTONE, Blocks.WITHER_ROSE,
            Blocks.POWDER_SNOW, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE);

    private SafeSpotFinder() {
    }

    /** @return позиция «ног» или null, если ничего безопасного не нашли */
    public static BlockPos find(ClientLevel world, BlockPos center, int minRadius, int maxRadius) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            int r = minRadius + (maxRadius > minRadius ? world.random.nextInt(maxRadius - minRadius + 1) : 0);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (isSafe(world, feet)) return feet;
            }
        }
        return null;
    }

    public static boolean isSafe(ClientLevel world, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState belowState = world.getBlockState(below);
        if (belowState.isAir()) return false;
        if (!belowState.getFluidState().isEmpty()) return false;
        if (belowState.getCollisionShape(world, below).isEmpty()) return false;
        if (isDangerousPos(world, below) || isDangerousPos(world, below.below())) return false;
        for (int i = 0; i < 2; i++) {
            BlockPos p = feet.above(i);
            BlockState s = world.getBlockState(p);
            if (!s.getCollisionShape(world, p).isEmpty()) return false;
            if (isDangerousPos(world, p)) return false;
        }
        return true;
    }

    public static boolean isDangerousPos(ClientLevel world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        if (s.getFluidState().is(FluidTags.LAVA)) return true;
        return DANGEROUS_BLOCKS.contains(s.getBlock());
    }

    /** Не шагать ли коту в лаву/кактус/огонь впереди. */
    public static boolean isDangerousAhead(ClientLevel world, PhantomCatEntity e, double vx, double vz) {
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len < 1.0E-6) return false;
        double nx = vx / len;
        double nz = vz / len;
        for (double d = 0.8; d <= 2.2; d += 0.7) {
            BlockPos floor = BlockPos.containing(e.getX() + nx * d, e.getY() - 0.5, e.getZ() + nz * d);
            if (isDangerousPos(world, floor)) return true;
            BlockPos body = BlockPos.containing(e.getX() + nx * d, e.getY() + 0.1, e.getZ() + nz * d);
            if (isDangerousPos(world, body)) return true;
        }
        return false;
    }
}
