package dev.phantomtwitchcats.cat;

/**
 * @param displayName Twitch-логин зрителя (имя над котом)
 * @param viewerId    Twitch user ID (для правила «один кот на зрителя» и дедупликации)
 * @param variantId   запрошенный окрас или null
 * @param baby        запрошен котёнок («мини»)
 * @param anyInput    зритель что-то написал (используется для правила случайного окраса)
 */
public record CatRequest(String displayName, String viewerId, String variantId, boolean baby, boolean anyInput) {
}