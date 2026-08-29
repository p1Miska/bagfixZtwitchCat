package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.PhantomTwitchCatsClient;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;

public final class CatFactory {

    private CatFactory() {
    }

    /**
     * ВАЖНО: Cat.setVariant(Holder<CatVariant>) в исходниках 26.1.2 объявлен как
     * private — снаружи класса Cat его вызвать нельзя, поэтому выбор конкретного
     * окраса зрителем (по тексту награды) сейчас НЕ применяется к сущности:
     * кот всегда получает окрас по умолчанию, который назначает сам Cat при
     * создании (Cat.defineSynchedData -> DEFAULT_VARIANT). Резолвинг ниже
     * оставлен, чтобы окрас хотя бы отображался корректно в сообщениях чата,
     * но чтобы реально красить кота, нужен Mixin в приватное поле DATA_VARIANT_ID
     * (SynchedEntityData) — это отдельная, более сложная задача.
     */
    public static String resolveVariantId(ClientLevel world, String requested, boolean anyInput) {
        PtcConfig cfg = ConfigManager.get();
        HolderLookup.RegistryLookup<CatVariant> reg = world.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
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
        // Владелец (setOwnerReference/аналог) не выставляем: сигнатура у TamableAnimal
        // в 26.1.2 отличается от старой setOwnerUuid(UUID), а для чисто визуальной
        // сущности принадлежность игроку не критична.
        if (baby && cfg.allowKittens) e.setBaby(true);
        return e;
    }

    private static CatVariant find(HolderLookup.RegistryLookup<CatVariant> reg, String id) {
        Identifier i = Identifier.tryParse(id == null ? "" : (id.contains(":") ? id : "minecraft:" + id));
        if (i == null) return null;
        Optional<Holder.Reference<CatVariant>> holder = reg.get(ResourceKey.create(Registries.CAT_VARIANT, i));
        return holder.map(Holder::value).orElse(null);
    }

    private static String randomId(HolderLookup.RegistryLookup<CatVariant> reg, ClientLevel world) {
        List<Holder.Reference<CatVariant>> all = reg.listElements().toList();
        if (all.isEmpty()) return "minecraft:tabby";
        Holder.Reference<CatVariant> pick = all.get(world.getRandom().nextInt(all.size()));
        return pick.key().location().toString();
    }
}
