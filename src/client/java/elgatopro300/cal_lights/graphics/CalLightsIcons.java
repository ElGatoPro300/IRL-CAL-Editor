package elgatopro300.cal_lights.graphics;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CalLightsIcons {
    public static CLTexture ICONS_TEXTURE;
    public static CLTexture FLARE_TEXTURE;
    public static CLTexture FLARE_GLOW_TEXTURE;
    public static CLTexture FLARE_RAY_TEXTURE;
    public static CLTexture POINT_LIGHT_STATIC_TEXTURE;
    public static CLTexture POINT_LIGHT_TINT_TEXTURE;
    public static CLTexture SPOT_LIGHT_STATIC_TEXTURE;
    public static CLTexture SPOT_LIGHT_TINT_TEXTURE;
    public static final Map<String, CLIcon> ALL_ICONS = new HashMap<>();

    // Standard mappings
    public static CLIcon POINT_LIGHT;
    public static CLIcon SPOT_LIGHT;
    public static CLIcon GIZMO;

    // Remapped Atlas Icons
    public static CLIcon GEAR;
    public static CLIcon MORE;
    public static CLIcon SAVED;
    public static CLIcon SAVE;
    public static CLIcon ADD;
    public static CLIcon DUPE;
    public static CLIcon REMOVE;
    public static CLIcon POSE;
    public static CLIcon FILTER;
    public static CLIcon MOVE_UP;
    public static CLIcon MOVE_DOWN;
    public static CLIcon LOCKED;
    public static CLIcon UNLOCKED;
    public static CLIcon COPY;
    public static CLIcon PASTE;
    public static CLIcon CUT;
    public static CLIcon REFRESH;

    public static CLIcon DOWNLOAD;
    public static CLIcon UPLOAD;
    public static CLIcon SERVER;
    public static CLIcon FOLDER;
    public static CLIcon IMAGE;
    public static CLIcon EDIT;
    public static CLIcon MATERIAL;
    public static CLIcon CLOSE;
    public static CLIcon LIMB;
    public static CLIcon CODE;
    public static CLIcon MOVE_LEFT;
    public static CLIcon MOVE_RIGHT;
    public static CLIcon HELP;
    public static CLIcon LEFT_HANDLE;
    public static CLIcon MAIN_HANDLE;
    public static CLIcon RIGHT_HANDLE;
    public static CLIcon REVERSE;
    public static CLIcon BLOCK;

    public static CLIcon FAVORITE;
    public static CLIcon VISIBLE;
    public static CLIcon INVISIBLE;
    public static CLIcon PLAY;
    public static CLIcon PAUSE;
    public static CLIcon MAXIMIZE;
    public static CLIcon MINIMIZE;
    public static CLIcon STOP;
    public static CLIcon FULLSCREEN;
    public static CLIcon ALL_DIRECTIONS;
    public static CLIcon SPHERE;
    public static CLIcon SHIFT_TO;
    public static CLIcon SHIFT_FORWARD;
    public static CLIcon SHIFT_BACKWARD;
    public static CLIcon MOVE_TO;
    public static CLIcon GRAPH;

    public static CLIcon WRENCH;
    public static CLIcon EXCLAMATION;
    public static CLIcon LEFTLOAD;
    public static CLIcon RIGHTLOAD;
    public static CLIcon BUBBLE;
    public static CLIcon FILE;
    public static CLIcon PROCESSOR;
    public static CLIcon MAZE;
    public static CLIcon BOOKMARK;
    public static CLIcon SOUND;
    public static CLIcon SEARCH;
    public static CLIcon CYLINDER;
    public static CLIcon LINE;
    public static CLIcon REDO;
    public static CLIcon UNDO;
    public static CLIcon CONSOLE;

    public static CLIcon IN;
    public static CLIcon OUT;
    public static CLIcon PROPERTIES;
    public static CLIcon FONT;
    public static CLIcon FRUSTUM;
    public static CLIcon FRAME_NEXT;
    public static CLIcon FRAME_PREV;
    public static CLIcon FORWARD;
    public static CLIcon BACKWARD;
    public static CLIcon PLANE;
    public static CLIcon HELICOPTER;
    public static CLIcon ORBIT;
    public static CLIcon CURVES;
    public static CLIcon ENVELOPE;
    public static CLIcon PLAYER;
    public static CLIcon TRASH;

    public static CLIcon YOUTUBE;
    public static CLIcon TWITTER;
    public static CLIcon CHICKEN;
    public static CLIcon SPRAY;
    public static CLIcon BUCKET;
    public static CLIcon TREE;
    public static CLIcon CROPS;
    public static CLIcon SLAB;
    public static CLIcon STAIR;
    public static CLIcon GLOBE;
    public static CLIcon BULLET;
    public static CLIcon PARTICLE;
    public static CLIcon SCENE;
    public static CLIcon EDITOR;
    public static CLIcon LOOKING;
    public static CLIcon EXTERNAL;

    public static CLIcon FILM;
    public static CLIcon OUTLINE;
    public static CLIcon BRICKS;
    public static CLIcon CONVERT;
    public static CLIcon JOYSTICK;
    public static CLIcon CUP;
    public static CLIcon CHECKMARK;
    public static CLIcon STRUCTURE;
    public static CLIcon ARC;
    public static CLIcon LIST;
    public static CLIcon SETTINGS;
    public static CLIcon GALLERY;
    public static CLIcon EXCHANGE;
    public static CLIcon ARROW_UP;
    public static CLIcon ARROW_DOWN;
    public static CLIcon ARROW_RIGHT;

    public static CLIcon ARROW_LEFT;
    public static CLIcon HEART;
    public static CLIcon SHARD;
    public static CLIcon POINTER;
    public static CLIcon SNOWFLAKE;
    public static CLIcon OUTLINE_SPHERE;
    public static CLIcon CAMERA;
    public static CLIcon FADING;
    public static CLIcon TIME;
    public static CLIcon LIGHT;
    public static CLIcon KEY_CAP;
    public static CLIcon LEFT_STICK;
    public static CLIcon RIGHT_STICK;
    public static CLIcon TRIGGER;
    public static CLIcon KEY;
    public static CLIcon VOICE;
    public static CLIcon COLOR;

    // Easing / Interpolations icons
    public static CLIcon INTERP_LINEAR;
    public static CLIcon INTERP_CONST;
    public static CLIcon INTERP_STEP;
    public static CLIcon INTERP_QUAD_INOUT;
    public static CLIcon INTERP_CUBIC_INOUT;
    public static CLIcon INTERP_QUART_INOUT;
    public static CLIcon INTERP_QUINT_INOUT;
    public static CLIcon INTERP_EXP_INOUT;
    public static CLIcon INTERP_BACK_INOUT;
    public static CLIcon INTERP_ELASTIC_INOUT;
    public static CLIcon INTERP_BOUNCE_INOUT;
    public static CLIcon INTERP_SINE_INOUT;
    public static CLIcon INTERP_CIRCLE_INOUT;

    public static CLIcon INTERP_QUAD_OUT;
    public static CLIcon INTERP_CUBIC_OUT;
    public static CLIcon INTERP_QUART_OUT;
    public static CLIcon INTERP_QUINT_OUT;
    public static CLIcon INTERP_EXP_OUT;
    public static CLIcon INTERP_BACK_OUT;
    public static CLIcon INTERP_ELASTIC_OUT;
    public static CLIcon INTERP_BOUNCE_OUT;
    public static CLIcon INTERP_SINE_OUT;
    public static CLIcon INTERP_CIRCLE_OUT;

    public static CLIcon INTERP_QUAD_IN;
    public static CLIcon INTERP_CUBIC_IN;
    public static CLIcon INTERP_QUART_IN;
    public static CLIcon INTERP_QUINT_IN;
    public static CLIcon INTERP_EXP_IN;
    public static CLIcon INTERP_BACK_IN;
    public static CLIcon INTERP_ELASTIC_IN;
    public static CLIcon INTERP_BOUNCE_IN;
    public static CLIcon INTERP_SINE_IN;
    public static CLIcon INTERP_CIRCLE_IN;

    public static void init() {
        InputStream stream = CalLightsIcons.class
            .getResourceAsStream("/assets/cal/assets/textures/icons.png");
        if (stream == null) {
            stream = CalLightsIcons.class
                .getResourceAsStream("/assets/cal/textures/icons.png");
        }
        if (stream == null) {
            throw new RuntimeException("Could not find icons.png resource in classpath!");
        }
        ICONS_TEXTURE = new CLTexture(stream, true);

        InputStream flareStream = CalLightsIcons.class
            .getResourceAsStream("/assets/cal/assets/textures/flare.png");
        if (flareStream == null) {
            flareStream = CalLightsIcons.class
                .getResourceAsStream("/assets/cal/textures/flare.png");
        }
        if (flareStream != null) {
            FLARE_TEXTURE = new CLTexture(flareStream);
        }

        InputStream flareGlowStream = CalLightsIcons.class
            .getResourceAsStream("/assets/cal/assets/textures/flare_glow.png");
        if (flareGlowStream == null) {
            flareGlowStream = CalLightsIcons.class
                .getResourceAsStream("/assets/cal/textures/flare_glow.png");
        }
        if (flareGlowStream != null) {
            FLARE_GLOW_TEXTURE = new CLTexture(flareGlowStream);
        }

        InputStream flareRayStream = CalLightsIcons.class
            .getResourceAsStream("/assets/cal/assets/textures/flare_ray.png");
        if (flareRayStream == null) {
            flareRayStream = CalLightsIcons.class
                .getResourceAsStream("/assets/cal/textures/flare_ray.png");
        }
        if (flareRayStream != null) {
            FLARE_RAY_TEXTURE = new CLTexture(flareRayStream);
        }

        InputStream pointStaticStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/assets/textures/point_texture_static.png");
        if (pointStaticStream == null) pointStaticStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/textures/point_texture_static.png");
        POINT_LIGHT_STATIC_TEXTURE = new CLTexture(pointStaticStream, true);

        InputStream pointTintStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/assets/textures/point_texture_tint.png");
        if (pointTintStream == null) pointTintStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/textures/point_texture_tint.png");
        POINT_LIGHT_TINT_TEXTURE = new CLTexture(pointTintStream, true);

        InputStream spotStaticStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/assets/textures/spot_texture_static.png");
        if (spotStaticStream == null) spotStaticStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/textures/spot_texture_static.png");
        SPOT_LIGHT_STATIC_TEXTURE = new CLTexture(spotStaticStream, true);

        InputStream spotTintStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/assets/textures/spot_texture_tint.png");
        if (spotTintStream == null) spotTintStream = CalLightsIcons.class.getResourceAsStream("/assets/cal/textures/spot_texture_tint.png");
        SPOT_LIGHT_TINT_TEXTURE = new CLTexture(spotTintStream, true);

        // Core lights
        POINT_LIGHT   = register("point_light", new CLIcon(POINT_LIGHT_STATIC_TEXTURE, POINT_LIGHT_TINT_TEXTURE, 0, 0, 16, 16));
        SPOT_LIGHT    = register("spot_light", new CLIcon(SPOT_LIGHT_STATIC_TEXTURE, SPOT_LIGHT_TINT_TEXTURE, 0, 0, 16, 16));
        GIZMO         = register("gizmo", new CLIcon(ICONS_TEXTURE, 0, 32, 16, 16));

        // Remap entire Atlas
        GEAR          = register("gear", new CLIcon(ICONS_TEXTURE, 0, 0, 16, 16));
        MORE          = register("more", new CLIcon(ICONS_TEXTURE, 16, 0, 16, 16));
        SAVED         = register("saved", new CLIcon(ICONS_TEXTURE, 32, 0, 16, 16));
        SAVE          = register("save", new CLIcon(ICONS_TEXTURE, 48, 0, 16, 16));
        ADD           = register("add", new CLIcon(ICONS_TEXTURE, 64, 0, 16, 16));
        DUPE          = register("dupe", new CLIcon(ICONS_TEXTURE, 80, 0, 16, 16));
        REMOVE        = register("remove", new CLIcon(ICONS_TEXTURE, 96, 0, 16, 16));
        POSE          = register("pose", new CLIcon(ICONS_TEXTURE, 112, 0, 16, 16));
        FILTER        = register("filter", new CLIcon(ICONS_TEXTURE, 128, 0, 16, 16));
        MOVE_UP       = register("move_up", new CLIcon(ICONS_TEXTURE, 144, 0, 16, 8));
        MOVE_DOWN     = register("move_down", new CLIcon(ICONS_TEXTURE, 144, 8, 16, 8));
        LOCKED        = register("locked", new CLIcon(ICONS_TEXTURE, 160, 0, 16, 16));
        UNLOCKED      = register("unlocked", new CLIcon(ICONS_TEXTURE, 176, 0, 16, 16));
        COPY          = register("copy", new CLIcon(ICONS_TEXTURE, 192, 0, 16, 16));
        PASTE         = register("paste", new CLIcon(ICONS_TEXTURE, 208, 0, 16, 16));
        CUT           = register("cut", new CLIcon(ICONS_TEXTURE, 224, 0, 16, 16));
        REFRESH       = register("refresh", new CLIcon(ICONS_TEXTURE, 240, 0, 16, 16));

        DOWNLOAD      = register("download", new CLIcon(ICONS_TEXTURE, 0, 16, 16, 16));
        UPLOAD        = register("upload", new CLIcon(ICONS_TEXTURE, 16, 16, 16, 16));
        SERVER        = register("server", new CLIcon(ICONS_TEXTURE, 32, 16, 16, 16));
        FOLDER        = register("folder", new CLIcon(ICONS_TEXTURE, 48, 16, 16, 16));
        IMAGE         = register("image", new CLIcon(ICONS_TEXTURE, 64, 16, 16, 16));
        EDIT          = register("edit", new CLIcon(ICONS_TEXTURE, 80, 16, 16, 16));
        MATERIAL      = register("material", new CLIcon(ICONS_TEXTURE, 96, 16, 16, 16));
        CLOSE         = register("close", new CLIcon(ICONS_TEXTURE, 112, 16, 16, 16));
        LIMB          = register("limb", new CLIcon(ICONS_TEXTURE, 128, 16, 16, 16));
        CODE          = register("code", new CLIcon(ICONS_TEXTURE, 144, 16, 16, 16));
        MOVE_LEFT     = register("move_left", new CLIcon(ICONS_TEXTURE, 146, 16, 6, 16));
        MOVE_RIGHT    = register("move_right", new CLIcon(ICONS_TEXTURE, 152, 16, 6, 16));
        HELP          = register("help", new CLIcon(ICONS_TEXTURE, 160, 16, 16, 16));
        LEFT_HANDLE   = register("left_handle", new CLIcon(ICONS_TEXTURE, 176, 16, 16, 16));
        MAIN_HANDLE   = register("main_handle", new CLIcon(ICONS_TEXTURE, 192, 16, 16, 16));
        RIGHT_HANDLE  = register("right_handle", new CLIcon(ICONS_TEXTURE, 208, 16, 16, 16));
        REVERSE       = register("reverse", new CLIcon(ICONS_TEXTURE, 224, 16, 16, 16));
        BLOCK         = register("block", new CLIcon(ICONS_TEXTURE, 240, 16, 16, 16));

        FAVORITE      = register("favorite", new CLIcon(ICONS_TEXTURE, 0, 32, 16, 16));
        VISIBLE       = register("visible", new CLIcon(ICONS_TEXTURE, 16, 32, 16, 16));
        INVISIBLE     = register("invisible", new CLIcon(ICONS_TEXTURE, 32, 32, 16, 16));
        PLAY          = register("play", new CLIcon(ICONS_TEXTURE, 48, 32, 16, 16));
        PAUSE         = register("pause", new CLIcon(ICONS_TEXTURE, 64, 32, 16, 16));
        MAXIMIZE      = register("maximize", new CLIcon(ICONS_TEXTURE, 80, 32, 16, 16));
        MINIMIZE      = register("minimize", new CLIcon(ICONS_TEXTURE, 96, 32, 16, 16));
        STOP          = register("stop", new CLIcon(ICONS_TEXTURE, 112, 32, 16, 16));
        FULLSCREEN    = register("fullscreen", new CLIcon(ICONS_TEXTURE, 128, 32, 16, 16));
        ALL_DIRECTIONS= register("all_directions", new CLIcon(ICONS_TEXTURE, 144, 32, 16, 16));
        SPHERE        = register("sphere", new CLIcon(ICONS_TEXTURE, 160, 32, 16, 16));
        SHIFT_TO      = register("shift_to", new CLIcon(ICONS_TEXTURE, 176, 32, 16, 16));
        SHIFT_FORWARD = register("shift_forward", new CLIcon(ICONS_TEXTURE, 192, 32, 16, 16));
        SHIFT_BACKWARD= register("shift_backward", new CLIcon(ICONS_TEXTURE, 208, 32, 16, 16));
        MOVE_TO       = register("move_to", new CLIcon(ICONS_TEXTURE, 224, 32, 16, 16));
        GRAPH         = register("graph", new CLIcon(ICONS_TEXTURE, 240, 32, 16, 16));

        WRENCH        = register("wrench", new CLIcon(ICONS_TEXTURE, 0, 48, 16, 16));
        EXCLAMATION   = register("exclamation", new CLIcon(ICONS_TEXTURE, 16, 48, 16, 16));
        LEFTLOAD      = register("leftload", new CLIcon(ICONS_TEXTURE, 32, 48, 16, 16));
        RIGHTLOAD     = register("rightload", new CLIcon(ICONS_TEXTURE, 48, 48, 16, 16));
        BUBBLE        = register("bubble", new CLIcon(ICONS_TEXTURE, 64, 48, 16, 16));
        FILE          = register("file", new CLIcon(ICONS_TEXTURE, 80, 48, 16, 16));
        PROCESSOR     = register("processor", new CLIcon(ICONS_TEXTURE, 96, 48, 16, 16));
        MAZE          = register("maze", new CLIcon(ICONS_TEXTURE, 112, 48, 16, 16));
        BOOKMARK      = register("bookmark", new CLIcon(ICONS_TEXTURE, 128, 48, 16, 16));
        SOUND         = register("sound", new CLIcon(ICONS_TEXTURE, 144, 48, 16, 16));
        SEARCH        = register("search", new CLIcon(ICONS_TEXTURE, 160, 48, 16, 16));
        CYLINDER      = register("cylinder", new CLIcon(ICONS_TEXTURE, 176, 48, 16, 16));
        LINE          = register("line", new CLIcon(ICONS_TEXTURE, 192, 48, 16, 16));
        REDO          = register("redo", new CLIcon(ICONS_TEXTURE, 208, 48, 16, 16));
        UNDO          = register("undo", new CLIcon(ICONS_TEXTURE, 224, 48, 16, 16));
        CONSOLE       = register("console", new CLIcon(ICONS_TEXTURE, 240, 48, 16, 16));

        IN            = register("in", new CLIcon(ICONS_TEXTURE, 0, 64, 16, 16));
        OUT           = register("out", new CLIcon(ICONS_TEXTURE, 16, 64, 16, 16));
        PROPERTIES    = register("properties", new CLIcon(ICONS_TEXTURE, 32, 64, 16, 16));
        FONT          = register("font", new CLIcon(ICONS_TEXTURE, 48, 64, 16, 16));
        FRUSTUM       = register("frustum", new CLIcon(ICONS_TEXTURE, 64, 64, 16, 16));
        FRAME_NEXT    = register("frame_next", new CLIcon(ICONS_TEXTURE, 80, 64, 16, 16));
        FRAME_PREV    = register("frame_prev", new CLIcon(ICONS_TEXTURE, 96, 64, 16, 16));
        FORWARD       = register("forward", new CLIcon(ICONS_TEXTURE, 112, 64, 16, 16));
        BACKWARD      = register("backward", new CLIcon(ICONS_TEXTURE, 128, 64, 16, 16));
        PLANE         = register("plane", new CLIcon(ICONS_TEXTURE, 144, 64, 16, 16));
        HELICOPTER    = register("helicopter", new CLIcon(ICONS_TEXTURE, 160, 64, 16, 16));
        ORBIT         = register("orbit", new CLIcon(ICONS_TEXTURE, 176, 64, 16, 16));
        CURVES        = register("curves", new CLIcon(ICONS_TEXTURE, 192, 64, 16, 16));
        ENVELOPE      = register("envelope", new CLIcon(ICONS_TEXTURE, 208, 64, 16, 16));
        PLAYER        = register("player", new CLIcon(ICONS_TEXTURE, 224, 64, 16, 16));
        TRASH         = register("trash", new CLIcon(ICONS_TEXTURE, 240, 64, 16, 16));

        YOUTUBE       = register("youtube", new CLIcon(ICONS_TEXTURE, 0, 80, 16, 16));
        TWITTER       = register("twitter", new CLIcon(ICONS_TEXTURE, 16, 80, 16, 16));
        CHICKEN       = register("chicken", new CLIcon(ICONS_TEXTURE, 32, 80, 16, 16));
        SPRAY         = register("spray", new CLIcon(ICONS_TEXTURE, 48, 80, 16, 16));
        BUCKET        = register("bucket", new CLIcon(ICONS_TEXTURE, 64, 80, 16, 16));
        TREE          = register("tree", new CLIcon(ICONS_TEXTURE, 80, 80, 16, 16));
        CROPS         = register("crops", new CLIcon(ICONS_TEXTURE, 96, 80, 16, 16));
        SLAB          = register("slab", new CLIcon(ICONS_TEXTURE, 112, 80, 16, 16));
        STAIR         = register("stair", new CLIcon(ICONS_TEXTURE, 128, 80, 16, 16));
        GLOBE         = register("globe", new CLIcon(ICONS_TEXTURE, 144, 80, 16, 16));
        BULLET        = register("bullet", new CLIcon(ICONS_TEXTURE, 160, 80, 16, 16));
        PARTICLE      = register("particle", new CLIcon(ICONS_TEXTURE, 176, 80, 16, 16));
        SCENE         = register("scene", new CLIcon(ICONS_TEXTURE, 192, 80, 16, 16));
        EDITOR        = register("editor", new CLIcon(ICONS_TEXTURE, 208, 80, 16, 16));
        LOOKING       = register("looking", new CLIcon(ICONS_TEXTURE, 224, 80, 16, 16));
        EXTERNAL      = register("external", new CLIcon(ICONS_TEXTURE, 240, 80, 16, 16));

        FILM          = register("film", new CLIcon(ICONS_TEXTURE, 0, 96, 16, 16));
        OUTLINE       = register("outline", new CLIcon(ICONS_TEXTURE, 16, 96, 16, 16));
        BRICKS        = register("bricks", new CLIcon(ICONS_TEXTURE, 32, 96, 16, 16));
        CONVERT       = register("convert", new CLIcon(ICONS_TEXTURE, 48, 96, 16, 16));
        JOYSTICK      = register("joystick", new CLIcon(ICONS_TEXTURE, 64, 96, 16, 16));
        CUP           = register("cup", new CLIcon(ICONS_TEXTURE, 80, 96, 16, 16));
        CHECKMARK     = register("checkmark", new CLIcon(ICONS_TEXTURE, 96, 96, 16, 16));
        STRUCTURE     = register("structure", new CLIcon(ICONS_TEXTURE, 112, 96, 16, 16));
        ARC           = register("arc", new CLIcon(ICONS_TEXTURE, 128, 96, 16, 16));
        LIST          = register("list", new CLIcon(ICONS_TEXTURE, 144, 96, 16, 16));
        SETTINGS      = register("settings", new CLIcon(ICONS_TEXTURE, 160, 96, 16, 16));
        GALLERY       = register("gallery", new CLIcon(ICONS_TEXTURE, 176, 96, 16, 16));
        EXCHANGE      = register("exchange", new CLIcon(ICONS_TEXTURE, 192, 96, 16, 16));
        ARROW_UP      = register("arrow_up", new CLIcon(ICONS_TEXTURE, 208, 96, 16, 16));
        ARROW_DOWN    = register("arrow_down", new CLIcon(ICONS_TEXTURE, 224, 96, 16, 16));
        ARROW_RIGHT   = register("arrow_right", new CLIcon(ICONS_TEXTURE, 240, 96, 16, 16));

        ARROW_LEFT    = register("arrow_left", new CLIcon(ICONS_TEXTURE, 0, 112, 16, 16));
        HEART         = register("heart", new CLIcon(ICONS_TEXTURE, 16, 112, 16, 16));
        SHARD         = register("shard", new CLIcon(ICONS_TEXTURE, 32, 112, 16, 16));
        POINTER       = register("pointer", new CLIcon(ICONS_TEXTURE, 48, 112, 16, 16));
        SNOWFLAKE     = register("snowflake", new CLIcon(ICONS_TEXTURE, 64, 112, 16, 16));
        OUTLINE_SPHERE= register("outline_sphere", new CLIcon(ICONS_TEXTURE, 80, 112, 16, 16));
        CAMERA        = register("camera", new CLIcon(ICONS_TEXTURE, 96, 112, 16, 16));
        FADING        = register("fading", new CLIcon(ICONS_TEXTURE, 112, 112, 16, 16));
        TIME          = register("time", new CLIcon(ICONS_TEXTURE, 128, 112, 16, 16));
        LIGHT         = register("light", new CLIcon(ICONS_TEXTURE, 144, 112, 16, 16));
        KEY_CAP       = register("key_cap", new CLIcon(ICONS_TEXTURE, 160, 112, 16, 16));
        LEFT_STICK    = register("left_stick", new CLIcon(ICONS_TEXTURE, 176, 112, 16, 16));
        RIGHT_STICK   = register("right_stick", new CLIcon(ICONS_TEXTURE, 192, 112, 16, 16));
        TRIGGER       = register("trigger", new CLIcon(ICONS_TEXTURE, 208, 112, 16, 16));
        KEY           = register("key", new CLIcon(ICONS_TEXTURE, 224, 112, 16, 16));
        VOICE         = register("voice", new CLIcon(ICONS_TEXTURE, 240, 112, 16, 16));

        // Register In/Out/InOut curves
        INTERP_LINEAR       = register("interp_linear", new CLIcon(ICONS_TEXTURE, 0, 192, 16, 16));
        INTERP_CONST        = register("interp_const", new CLIcon(ICONS_TEXTURE, 16, 192, 16, 16));
        INTERP_STEP         = register("interp_step", new CLIcon(ICONS_TEXTURE, 32, 192, 16, 16));
        INTERP_QUAD_INOUT   = register("interp_quad_inout", new CLIcon(ICONS_TEXTURE, 48, 192, 16, 16));
        INTERP_CUBIC_INOUT  = register("interp_cubic_inout", new CLIcon(ICONS_TEXTURE, 64, 192, 16, 16));
        INTERP_QUART_INOUT  = register("interp_quart_inout", new CLIcon(ICONS_TEXTURE, 80, 192, 16, 16));
        INTERP_QUINT_INOUT  = register("interp_quint_inout", new CLIcon(ICONS_TEXTURE, 96, 192, 16, 16));
        INTERP_EXP_INOUT    = register("interp_exp_inout", new CLIcon(ICONS_TEXTURE, 112, 192, 16, 16));
        INTERP_BACK_INOUT   = register("interp_back_inout", new CLIcon(ICONS_TEXTURE, 128, 192, 16, 16));
        INTERP_ELASTIC_INOUT= register("interp_elastic_inout", new CLIcon(ICONS_TEXTURE, 144, 192, 16, 16));
        INTERP_BOUNCE_INOUT = register("interp_bounce_inout", new CLIcon(ICONS_TEXTURE, 160, 192, 16, 16));
        INTERP_SINE_INOUT   = register("interp_sine_inout", new CLIcon(ICONS_TEXTURE, 176, 192, 16, 16));
        INTERP_CIRCLE_INOUT = register("interp_circle_inout", new CLIcon(ICONS_TEXTURE, 192, 192, 16, 16));

        INTERP_QUAD_OUT     = register("interp_quad_out", new CLIcon(ICONS_TEXTURE, 48, 208, 16, 16));
        INTERP_CUBIC_OUT    = register("interp_cubic_out", new CLIcon(ICONS_TEXTURE, 64, 208, 16, 16));
        INTERP_QUART_OUT    = register("interp_quart_out", new CLIcon(ICONS_TEXTURE, 80, 208, 16, 16));
        INTERP_QUINT_OUT    = register("interp_quint_out", new CLIcon(ICONS_TEXTURE, 96, 208, 16, 16));
        INTERP_EXP_OUT      = register("interp_exp_out", new CLIcon(ICONS_TEXTURE, 112, 208, 16, 16));
        INTERP_BACK_OUT     = register("interp_back_out", new CLIcon(ICONS_TEXTURE, 128, 208, 16, 16));
        INTERP_ELASTIC_OUT  = register("interp_elastic_out", new CLIcon(ICONS_TEXTURE, 144, 208, 16, 16));
        INTERP_BOUNCE_OUT   = register("interp_bounce_out", new CLIcon(ICONS_TEXTURE, 160, 208, 16, 16));
        INTERP_SINE_OUT     = register("interp_sine_out", new CLIcon(ICONS_TEXTURE, 176, 208, 16, 16));
        INTERP_CIRCLE_OUT   = register("interp_circle_out", new CLIcon(ICONS_TEXTURE, 192, 208, 16, 16));

        INTERP_QUAD_IN      = register("interp_quad_in", new CLIcon(ICONS_TEXTURE, 48, 224, 16, 16));
        INTERP_CUBIC_IN     = register("interp_cubic_in", new CLIcon(ICONS_TEXTURE, 64, 224, 16, 16));
        INTERP_QUART_IN     = register("interp_quart_in", new CLIcon(ICONS_TEXTURE, 80, 224, 16, 16));
        INTERP_QUINT_IN     = register("interp_quint_in", new CLIcon(ICONS_TEXTURE, 96, 224, 16, 16));
        INTERP_EXP_IN       = register("interp_exp_in", new CLIcon(ICONS_TEXTURE, 112, 224, 16, 16));
        INTERP_BACK_IN      = register("interp_back_in", new CLIcon(ICONS_TEXTURE, 128, 224, 16, 16));
        INTERP_ELASTIC_IN   = register("interp_elastic_in", new CLIcon(ICONS_TEXTURE, 144, 224, 16, 16));
        INTERP_BOUNCE_IN    = register("interp_bounce_in", new CLIcon(ICONS_TEXTURE, 160, 224, 16, 16));
        INTERP_SINE_IN      = register("interp_sine_in", new CLIcon(ICONS_TEXTURE, 176, 224, 16, 16));
        INTERP_CIRCLE_IN    = register("interp_circle_in", new CLIcon(ICONS_TEXTURE, 192, 224, 16, 16));

        COLOR               = register("color", new CLIcon(ICONS_TEXTURE, 176, 128, 16, 16));
    }

    private static CLIcon register(String id, CLIcon icon) {
        ALL_ICONS.put(id, icon);
        return icon;
    }
}
