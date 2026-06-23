package elgatopro300.cal_lights.ui;

import net.minecraft.client.MinecraftClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class CalSettings {
    public static final CalSettings INSTANCE = new CalSettings();

    public int guiScale = 2;
    public boolean englishSelected = true; // false = Español, true = English
    public String language = "en_us";
    public float gizmoSize = 10.0f; // default is 1.0f, ranges from 0.0f to 10.0f
    public boolean simplifyAnimations = false; // true = simple/no animation, false = premium visual slide transitions
    
    // Premium BBS-style Timeline preferences
    public String durationMode = "ticks"; // "ticks" or "seconds"
    
    // Remappable hotkeys (stored as GLFW key codes)
    public int keyPlayPause = GLFW.GLFW_KEY_SPACE;       // Space
    public int keyAddKeyframe = GLFW.GLFW_KEY_V;         // V
    public int keyDeleteKeyframe = GLFW.GLFW_KEY_DELETE; // Delete
    
    // Easing curves remappable hotkeys
    public int keyInterpLinear = GLFW.GLFW_KEY_L;
    public int keyInterpStep = GLFW.GLFW_KEY_P;
    public int keyInterpConstant = GLFW.GLFW_KEY_T;
    public int keyInterpSine = GLFW.GLFW_KEY_I;
    public int keyInterpQuad = GLFW.GLFW_KEY_Q;
    public int keyInterpCubic = GLFW.GLFW_KEY_C;
    public int keyInterpExp = GLFW.GLFW_KEY_E;
    public int keyInterpCircle = GLFW.GLFW_KEY_R;
    public int keyInterpBack = GLFW.GLFW_KEY_B;
    public int keyInterpElastic = GLFW.GLFW_KEY_S;
    public int keyInterpBounce = GLFW.GLFW_KEY_O;

    private File getConfigFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "config/cal_lights_settings.json");
    }

    public void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            if (json != null) {
                if (json.has("guiScale")) {
                    this.guiScale = json.get("guiScale").getAsInt();
                }
                if (json.has("language")) {
                    this.language = json.get("language").getAsString();
                    this.englishSelected = this.language.equals("en_us");
                } else if (json.has("englishSelected")) {
                    this.englishSelected = json.get("englishSelected").getAsBoolean();
                    this.language = this.englishSelected ? "en_us" : "es_es";
                }
                if (json.has("gizmoSize")) {
                    this.gizmoSize = json.get("gizmoSize").getAsFloat();
                }
                if (json.has("durationMode")) {
                    this.durationMode = json.get("durationMode").getAsString();
                }
                if (json.has("simplifyAnimations")) {
                    this.simplifyAnimations = json.get("simplifyAnimations").getAsBoolean();
                }
                if (json.has("keyPlayPause")) this.keyPlayPause = json.get("keyPlayPause").getAsInt();
                if (json.has("keyAddKeyframe")) this.keyAddKeyframe = json.get("keyAddKeyframe").getAsInt();
                if (json.has("keyDeleteKeyframe")) this.keyDeleteKeyframe = json.get("keyDeleteKeyframe").getAsInt();
                if (json.has("keyInterpLinear")) this.keyInterpLinear = json.get("keyInterpLinear").getAsInt();
                if (json.has("keyInterpStep")) this.keyInterpStep = json.get("keyInterpStep").getAsInt();
                if (json.has("keyInterpConstant")) this.keyInterpConstant = json.get("keyInterpConstant").getAsInt();
                if (json.has("keyInterpSine")) this.keyInterpSine = json.get("keyInterpSine").getAsInt();
                if (json.has("keyInterpQuad")) this.keyInterpQuad = json.get("keyInterpQuad").getAsInt();
                if (json.has("keyInterpCubic")) this.keyInterpCubic = json.get("keyInterpCubic").getAsInt();
                if (json.has("keyInterpExp")) this.keyInterpExp = json.get("keyInterpExp").getAsInt();
                if (json.has("keyInterpCircle")) this.keyInterpCircle = json.get("keyInterpCircle").getAsInt();
                if (json.has("keyInterpBack")) this.keyInterpBack = json.get("keyInterpBack").getAsInt();
                if (json.has("keyInterpElastic")) this.keyInterpElastic = json.get("keyInterpElastic").getAsInt();
                if (json.has("keyInterpBounce")) this.keyInterpBounce = json.get("keyInterpBounce").getAsInt();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void save() {
        File file = getConfigFile();
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject json = new JsonObject();
            json.addProperty("guiScale", this.guiScale);
            json.addProperty("englishSelected", this.englishSelected);
            json.addProperty("language", this.language);
            json.addProperty("gizmoSize", this.gizmoSize);
            json.addProperty("durationMode", this.durationMode);
            json.addProperty("simplifyAnimations", this.simplifyAnimations);
            
            json.addProperty("keyPlayPause", this.keyPlayPause);
            json.addProperty("keyAddKeyframe", this.keyAddKeyframe);
            json.addProperty("keyDeleteKeyframe", this.keyDeleteKeyframe);
            json.addProperty("keyInterpLinear", this.keyInterpLinear);
            json.addProperty("keyInterpStep", this.keyInterpStep);
            json.addProperty("keyInterpConstant", this.keyInterpConstant);
            json.addProperty("keyInterpSine", this.keyInterpSine);
            json.addProperty("keyInterpQuad", this.keyInterpQuad);
            json.addProperty("keyInterpCubic", this.keyInterpCubic);
            json.addProperty("keyInterpExp", this.keyInterpExp);
            json.addProperty("keyInterpCircle", this.keyInterpCircle);
            json.addProperty("keyInterpBack", this.keyInterpBack);
            json.addProperty("keyInterpElastic", this.keyInterpElastic);
            json.addProperty("keyInterpBounce", this.keyInterpBounce);
            
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
