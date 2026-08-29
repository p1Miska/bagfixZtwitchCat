package dev.phantomtwitchcats;

import dev.phantomtwitchcats.cat.CatManager;
import dev.phantomtwitchcats.command.PhantomCatCommands;
import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.entity.PhantomCatEntity;
import dev.phantomtwitchcats.entity.PhantomCatRenderer;
import dev.phantomtwitchcats.render.StatusHud;
import dev.phantomtwitchcats.render.WorldRenderHook;
import dev.phantomtwitchcats.twitch.RedemptionDeduper;
import dev.phantomtwitchcats.twitch.TwitchManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PhantomTwitchCatsClient implements ClientModInitializer {
    public static final String MOD_ID = "phantomtwitchcats";
    public static final Logger LOGGER = LoggerFactory.getLogger("PhantomTwitchCats");

    /** Клиентский тип сущности. Никогда не отправляется на сервер. */
    public static EntityType<PhantomCatEntity> PHANTOM_CAT;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        RedemptionDeduper.get().load();

        PHANTOM_CAT = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "phantom_cat"),
                EntityType.Builder.of(PhantomCatEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 0.7f)
                        .build("phantom_cat"));
        FabricDefaultAttributeRegistry.register(PHANTOM_CAT, Cat.createAttributes());
        EntityRendererRegistry.register(PHANTOM_CAT, PhantomCatRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(CatManager.get()::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CatManager.get().onWorldReady());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CatManager.get().onDisconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());

        WorldRenderHook.register();
        StatusHud.register();
        PhantomCatCommands.register();

        TwitchManager.get().autoConnect();
        LOGGER.info("Phantom Twitch Cats загружен");
    }

    private static void shutdown() {
        try { ConfigManager.save(); } catch (Exception e) { LOGGER.warn("Конфиг не сохранён: {}", e.toString()); }
        try { RedemptionDeduper.get().save(); } catch (Exception ignored) { }
        try { TwitchManager.get().shutdown(); } catch (Exception ignored) { }
    }
}
