package dev.phantomtwitchcats.cat;

import dev.phantomtwitchcats.config.ConfigManager;
import dev.phantomtwitchcats.config.PtcConfig;

import java.util.Locale;
import java.util.Map;

/**
 * Простой парсер ввода зрителя: «чер», «мини чер», «ЧЕР МИНИ» и т.д.
 * Никакого NLP: только точные ключевые слова и их префиксы из конфига.
 */
public final class InputParser {

    private InputParser() {
    }

    public static CatRequest parse(String rawInput, String displayName, String viewerId) {
        PtcConfig cfg = ConfigManager.get();
        String text = rawInput == null ? "" : rawInput.toLowerCase(Locale.ROOT).trim();

        String variant = null;
        boolean baby = false;
        boolean anyInput = false;

        for (String token : text.split("[^\\p{L}\\p{N}_]+")) {
            if (token.isEmpty()) continue;
            anyInput = true;
            if (isMiniKeyword(token, cfg)) {
                baby = true;
                continue;
            }
            String v = resolveVariant(token, cfg);
            if (v != null && variant == null) variant = v; // первый распознанный окрас
        }
        return new CatRequest(displayName, viewerId, variant, baby, anyInput);
    }

    private static boolean isMiniKeyword(String token, PtcConfig cfg) {
        for (String kw : cfg.miniKeywords) {
            if (kw.isEmpty()) continue;
            if (token.equals(kw)) return true;
            if (kw.length() >= 3 && token.startsWith(kw)) return true;
        }
        return false;
    }

    private static String resolveVariant(String token, PtcConfig cfg) {
        for (Map.Entry<String, String> e : cfg.colorAliases.entrySet()) {
            String alias = e.getKey();
            if (alias.isEmpty()) continue;
            if (token.equals(alias)) return e.getValue();
            if (alias.length() >= 3 && token.startsWith(alias)) return e.getValue();
        }
        return null;
    }
}