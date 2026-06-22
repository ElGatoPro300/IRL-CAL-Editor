package elgatopro300.cal_lights.ui;

import net.minecraft.client.MinecraftClient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class L10n {
    private static final Map<String, String> strings = new HashMap<>();
    private static String currentLang = "";

    public static void init() {
        String lang = CalSettings.INSTANCE.language;
        if (lang.equals(currentLang)) return;

        strings.clear();
        loadLang("en_us"); // Fallback
        if (!lang.equals("en_us")) {
            loadLang(lang);
        }
        currentLang = lang;
    }

    private static void loadLang(String lang) {
        String path = "/assets/cal/assets/strings/" + lang + ".json";
        try (InputStream is = L10n.class.getResourceAsStream(path)) {
            if (is != null) {
                JsonObject json = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                for (String key : json.keySet()) {
                    strings.put(key, json.get(key).getAsString());
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String get(String key) {
        init();
        return strings.getOrDefault(key, key);
    }
}
