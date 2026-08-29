package dev.phantomtwitchcats.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;

/**
 * Обычная ванильная кошачья сущность, но она никогда не добавляется в мир.
 * Сервер не знает о ней: нет spawn-пакетов, нет tracker'а, нет взаимодействий.
 */
public class PhantomCatEntity extends Cat {

    public PhantomCatEntity(EntityType<? extends Cat> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isPushable() {
        return false; // реальные сущности не толкают фантомного кота
    }

    @Override
    public void push(Entity entity) {
        // фантомный кот не реагирует на толчки
    }
}
