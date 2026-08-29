package dev.phantomtwitchcats.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PtcConfig {

    // ---- Twitch ----
    public String clientId = "";
    public String clientSecret = "";
    public int authPort = 16734;
    public String rewardTitle = "Призови кота";
    public String rewardId = "";
    public boolean refundRedemptions = true;
    public boolean fulfillRedemptions = true;

    // ---- Коты ----
    public int lifetimeMinutes = 5;
    public int maxCats = 10;
    public double maxDistance = 50.0;
    public boolean oneCatPerViewer = true;
    public boolean showNames = true;
    public boolean allowKittens = true;
    public boolean randomColorWhenUnspecified = true;
    public String defaultVariant = "tabby";
    public boolean autoSit = true;
    public boolean announceSpawns = true;
    public boolean localSounds = true;
    public boolean showHud = true;

    /** алиас -> id ванильного окраса (все 11 окрасов MC поддерживаются) */
    public Map<String, String> colorAliases = defaultColorAliases();
    public List<String> miniKeywords = new ArrayList<>(
            List.of("мини", "mini", "котён", "котен", "мал", "kitten", "baby"));

    public static Map<String, String> defaultColorAliases() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("чер", "black");
        m.put("black", "black");
        m.put("всечер", "all_black");
        m.put("угол", "all_black");
        m.put("allblack", "all_black");
        m.put("бел", "white");
        m.put("white", "white");
        m.put("рыж", "red");
        m.put("red", "red");
        m.put("orange", "red");
        m.put("сер", "tabby");
        m.put("grey", "tabby");
        m.put("gray", "tabby");
        m.put("таб", "tabby");
        m.put("табби", "tabby");
        m.put("полосат", "tabby");
        m.put("tabby", "tabby");
        m.put("сиам", "siamese");
        m.put("siam", "siamese");
        m.put("siamese", "siamese");
        m.put("брит", "british_shorthair");
        m.put("british", "british_shorthair");
        m.put("калико", "calico");
        m.put("трёхцвет", "calico");
        m.put("трехцвет", "calico");
        m.put("calico", "calico");
        m.put("перс", "persian");
        m.put("persian", "persian");
        m.put("рэг", "ragdoll");
        m.put("рэгд", "ragdoll");
        m.put("регд", "ragdoll");
        m.put("ragdoll", "ragdoll");
        m.put("джел", "jellie");
        m.put("джелли", "jellie");
        m.put("jellie", "jellie");
        return m;
    }

    public void normalize() {
        if (colorAliases == null) colorAliases = defaultColorAliases();
        Map<String, String> lower = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : colorAliases.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            lower.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue().trim().toLowerCase(Locale.ROOT));
        }
        colorAliases = lower;

        if (miniKeywords == null) miniKeywords = new ArrayList<>();
        List<String> mk = new ArrayList<>();
        for (String k : miniKeywords) {
            if (k != null && !k.isBlank()) mk.add(k.trim().toLowerCase(Locale.ROOT));
        }
        miniKeywords = mk;

        lifetimeMinutes = Math.max(1, Math.min(1440, lifetimeMinutes));
        maxCats = Math.max(1, Math.min(100, maxCats));
        maxDistance = Math.max(5.0, Math.min(256.0, maxDistance));
        authPort = Math.max(1024, Math.min(65535, authPort));

        clientId = trim(clientId);
        clientSecret = trim(clientSecret);
        rewardTitle = trim(rewardTitle);
        rewardId = trim(rewardId);
        if (defaultVariant == null || defaultVariant.isBlank()) {
            defaultVariant = "tabby";
        } else {
            defaultVariant = defaultVariant.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}