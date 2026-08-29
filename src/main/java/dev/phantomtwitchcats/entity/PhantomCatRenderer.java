package dev.phantomtwitchcats.entity;

import dev.phantomtwitchcats.config.ConfigManager;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * ВНИМАНИЕ (риск миграции): начиная с MC 1.21.2 рендереры сущностей работают
 * через промежуточный объект render state (для кота это CatRenderState),
 * а не напрямую с объектом сущности в момент отрисовки. CatRenderer —
 * НЕ дженерик-класс (в отличие от старого CatEntityRenderer<T> в Yarn для
 * версий до рефакторинга), он жёстко привязан к типу Cat/CatRenderState.
 * Поэтому здесь просто extends CatRenderer, без параметра типа.
 *
 * Оригинальный метод hasLabel(PhantomCatEntity) мог быть переименован
 * (возможно, в shouldShowName) и мог сменить сигнатуру на принимающую
 * CatRenderState/дистанцию до камеры вместо самой сущности — проверьте
 * при сборке и поправьте override ниже по сообщению компилятора.
 */
public class PhantomCatRenderer extends CatRenderer {

    public PhantomCatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean hasLabel(PhantomCatEntity entity) {
        // Имя зрителя постоянно над головой (в пределах дальности ванильных меток).
        return ConfigManager.get().showNames;
    }
}
