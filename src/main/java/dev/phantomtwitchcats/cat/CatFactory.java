package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class CatFactory {

    private CatFactory() {
    }

    /**
     * Правила выбора окраса:
     *  - указан известный окрас -> он;
     *  - указан неизвестный окрас или любое слово без окраса (например «мини») -> случайный;
     *  - пустой ввод -> случайный (если включено в конфиге) или дефолтный.
     */
    public static String resolveVariantId(ClientLevel world, String requested, boolean anyInput) {
        PtcConfig cfg = ConfigManager.get();
        Registry<CatVariant> reg = world.registryAccess().registryOrThrow(Registries.CAT_VARIANT);
        if (requested != null) {
            if (find(reg, requested) != null) return requested;
            return randomId(reg, world);
        }
        if (anyInput || cfg.randomColorWhenUnspecified) return randomId(reg, world);
        if (find(reg, cfg.defaultVariant) != null) return cfg.defaultVariant;
        return randomId(reg, world);
    }

    public static PhantomCatEntity build(ClientLevel world, LocalPlayer owner, String variantId,
                                         boolean baby, String displayName, int entityId) {
        PtcConfig cfg = ConfigManager.get();
        PhantomCatEntity e = new PhantomCatEntity(PhantomTwitchCatsClient.PHANTOM_CAT, world);
        e.setId(entityId);
        e.setCustomName(Component.literal(displayName == null ? "viewer" : displayName));
        e.setCustomNameVisible(cfg.showNames);
        e.setTame(true, false);
        try {
            e.setOwnerUUID(owner.getUUID());
        } catch (Throwable ignored) {
        }
        Registry<CatVariant> reg = world.registryAccess().registryOrThrow(Registries.CAT_VARIANT);
        CatVariant variant = find(reg, variantId);
        if (variant == null) variant = find(reg, "minecraft:tabby");
        if (variant != null) e.setVariant(Holder.direct(variant));
        if (baby && cfg.allowKittens) e.setBaby(true);
        return e;
    }

    private static CatVariant find(Registry<CatVariant> reg, String id) {
        Identifier i = Identifier.tryParse(id == null ? "" : (id.contains(":") ? id : "minecraft:" + id));
        return i == null ? null : reg.getValue(i);
    }

    private static String randomId(Registry<CatVariant> reg, ClientLevel world) {
        List<Identifier> ids = new ArrayList<>(reg.keySet());
        if (ids.isEmpty()) return "minecraft:tabby";
        return ids.get(world.random.nextInt(ids.size())).toString();
    }
}
