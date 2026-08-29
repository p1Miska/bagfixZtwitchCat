package dev.phantomtwitchcats.entity;

import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * CatRenderer — не дженерик-класс, жёстко привязан к Cat/CatRenderState
 * (система render state, введённая в 1.21.2+). Кастомный override
 * hasLabel/shouldShowName убран: в 26.1.2 у этого метода другая сигнатура
 * (принимает render state/дистанцию, а не саму сущность), и без реального
 * исходника CatRenderer её нельзя восстановить надёжно. Имя над котом
 * будет отображаться по стандартным ванильным правилам видимости.
 */
public class PhantomCatRenderer extends CatRenderer {

    public PhantomCatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
