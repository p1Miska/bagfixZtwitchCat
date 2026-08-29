MIGRATION — адаптация к точному билду Minecraft 26.1.2
Проект написан под актуальную стабильную линейку Fabric API. Для сборки подконкретный билд 26.1.2:

Шаг 1. Версии (обязательно)
https://fabricmc.net/develop → выбрать 26.1.2 → вписать в gradle.properties:minecraft_version, yarn_mappings, loader_version, fabric_version.В build.gradle — актуальную версию fabric-loom.modmenu_version — с https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/(или отключите ModMenu, см. ниже).

Шаг 2. gradlew build
Если конкретные имена API отличаются — исправления точечные:

Файл	Вызов	Если не компилируется
PhantomTwitchCatsClient	EntityType.Builder…build()	в старых версиях build(RegistryKey)/build(String)
CatFactory / PhantomCat	setVariant(RegistryEntry.of(v))	может зваться setCatVariant(...)
PhantomCat	setInSittingPose(...)	альтернативы: setSitting(true) и т.п.
CatFactory	setOwnerUuid, setTamed	проверьте наличие на CatEntity (интерфейс Tameable)
CatFactory	RegistryKeys.CAT_VARIANT + world.getRegistryManager()	вариант: getOrThrow(...)
WorldRenderHook	dispatcher.render(entity, x, y, z, yaw, tickDelta, matrices, consumers, light)	вариант для ≤1.21.1: dispatcher.getRenderer(e) + renderer.render(e, yaw, tickDelta, matrices, consumers, light); для эпохи EntityRenderState dispatcher.render сам вызывает updateRenderState
WorldRenderHook	WorldRenderer.getLightmapCoordinates(world, pos)	варианты сигнатур: (BlockRenderView, BlockPos) / (World, Entity)
PhantomCatRenderer	hasLabel(...)	в версиях с render-state имя управляется через setCustomNameVisible(true) (уже выставляется) — переопределение можно убрать
PtcConfigScreen / RewardPickerScreen	mouseScrolled(mx, my, horizontalAmount, verticalAmount)	в старых версиях 3 аргумента
PtcConfigScreen	clearAndInit(), TextFieldWidget(...), setRenderTextProvider	при отсутствии — пересоздайте экран: client.setScreen(new PtcConfigScreen(parent))
TwitchAuth	Util.getOperatingSystem().open(url)	замена: java.awt.Desktop.getDesktop().browse(URI.create(url))
StatusHud	HudRenderCallback (DrawContext, RenderTickCounter)	в старых версиях второй аргумент float
PhantomCat	ClientWorld.playSound(x,y,z,sound,cat,vol,pitch,false) — 9 аргументов	в некоторых версиях 8 аргументов (без boolean)
Twitch-часть (Helix/EventSub/Auth) не зависит от Minecraft API, кроме двух мест(Util.open, player.sendMessage) — адаптации почти не требует.

Отключение ModMenu (если артефакт недоступен)
Удалите блок modCompileOnly … modmenu … из build.gradle.
Удалите src/main/java/dev/phantomtwitchcats/compat/ModMenuCompat.java.
Из fabric.mod.json удалите "modmenu" из entrypoints и секцию "recommends".
Все функции (включая настройки) остаются доступны через /phantomcat config.