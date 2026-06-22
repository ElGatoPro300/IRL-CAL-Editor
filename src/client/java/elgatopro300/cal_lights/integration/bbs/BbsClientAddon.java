package elgatopro300.cal_lights.integration.bbs;

import mchorse.bbs_mod.addons.BBSClientAddon;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterFormsRenderersEvent;
import mchorse.bbs_mod.events.register.RegisterIconsEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

public class BbsClientAddon extends BBSClientAddon {
    @Override
    @Subscribe
    public void registerFormsRenderers(RegisterFormsRenderersEvent event) {
        event.registerRenderer(CALBbsForm.class, CALLightsBbsIntegration.CALBbsFormRenderer::new);
        event.registerPanel(CALBbsForm.class, CALLightsBbsIntegration.UICALBbsForm::new);

        // Register custom track colors for CAL Light Form properties in Timeline
        try {
            String[] properties = {
                "spot", "show_indicator", "intensity", "radius", "inner_angle", "outer_angle", "distance",
                "fog_enabled", "fog_dispersion", "fog_density", "fog_anisotropy",
                "shadow_enabled", "shadow_softness", "shadow_intensity",
                "flare_enabled", "flare_size", "flare_glow_size", "flare_glow_intensity",
                "flare_ray_length", "flare_ray_thickness", "flare_ray_length2", "flare_ray_thickness2",
                "flare_ray_length3", "flare_ray_thickness3", "flare_rotation", "flare_start_angle", "flare_end_angle",
                "rim_enabled", "rim_intensity", "rim_power", "rim_hardness", "rim_direction",
                "outline_enabled", "outline_intensity", "outline_thickness"
            };

            for (int i = 0; i < properties.length; i++) {
                // Distribute hue evenly around the 360° color wheel (0.0 to 1.0)
                float hue = (float) i / properties.length;
                // Saturation = 0.8f, Brightness = 0.9f for vibrant and highly readable colors on dark background
                int color = java.awt.Color.HSBtoRGB(hue, 0.8f, 0.9f) & 0xFFFFFF;

                UIReplaysEditor.registerColor(properties[i], color);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
 
        // Register custom track icons for CAL Light Form properties in Timeline
        UIReplaysEditor.registerIcon("spot", Icons.FRUSTUM);
        UIReplaysEditor.registerIcon("show_indicator", Icons.HELP);
        UIReplaysEditor.registerIcon("intensity", Icons.SUN);
        UIReplaysEditor.registerIcon("radius", Icons.SPHERE);
        UIReplaysEditor.registerIcon("inner_angle", Icons.IN);
        UIReplaysEditor.registerIcon("outer_angle", Icons.OUT);
        UIReplaysEditor.registerIcon("distance", Icons.SCALE);
        UIReplaysEditor.registerIcon("fog_enabled", Icons.LIGHT);
        UIReplaysEditor.registerIcon("fog_dispersion", Icons.SPRAY);
        UIReplaysEditor.registerIcon("fog_density", Icons.FADING);
        UIReplaysEditor.registerIcon("fog_anisotropy", Icons.EXCHANGE);
        UIReplaysEditor.registerIcon("shadow_enabled", Icons.INVISIBLE);
        UIReplaysEditor.registerIcon("shadow_softness", Icons.FADING);
        UIReplaysEditor.registerIcon("shadow_intensity", Icons.SHARD);
        UIReplaysEditor.registerIcon("flare_enabled", Icons.SUN);
        UIReplaysEditor.registerIcon("flare_size", Icons.SCALE);
        UIReplaysEditor.registerIcon("flare_glow_size", Icons.SPHERE);
        UIReplaysEditor.registerIcon("flare_glow_intensity", Icons.FADING);
        UIReplaysEditor.registerIcon("flare_ray_length", Icons.IN);
        UIReplaysEditor.registerIcon("flare_ray_thickness", Icons.SCALE);
        UIReplaysEditor.registerIcon("flare_ray_length2", Icons.OUT);
        UIReplaysEditor.registerIcon("flare_ray_thickness2", Icons.SCALE);
        UIReplaysEditor.registerIcon("flare_ray_length3", Icons.EXCHANGE);
        UIReplaysEditor.registerIcon("flare_ray_thickness3", Icons.SCALE);
        UIReplaysEditor.registerIcon("flare_rotation", Icons.GEAR);
        UIReplaysEditor.registerIcon("flare_start_angle", Icons.IN);
        UIReplaysEditor.registerIcon("flare_end_angle", Icons.OUT);
        UIReplaysEditor.registerIcon("rim_enabled", Icons.LIGHT);
        UIReplaysEditor.registerIcon("rim_intensity", Icons.SUN);
        UIReplaysEditor.registerIcon("rim_power", Icons.IN);
        UIReplaysEditor.registerIcon("rim_hardness", Icons.SHARD);
        UIReplaysEditor.registerIcon("rim_direction", Icons.EXCHANGE);
        UIReplaysEditor.registerIcon("outline_enabled", Icons.LIGHT);
        UIReplaysEditor.registerIcon("outline_intensity", Icons.SUN);
        UIReplaysEditor.registerIcon("outline_thickness", Icons.SCALE);
    }

    @Override
    @Subscribe
    public void registerIcons(RegisterIconsEvent event) {
        // Register form select icon matching the "bbs:cal_light" form type link using the CAL-Lights bulb texture asset
        event.register(new Icon(new Link("cal", "assets/textures/icon.png"), "bbs:cal_light", 0, 0, 16, 16, 16, 16));
    }

    @Override
    @Subscribe
    public void registerL10n(RegisterL10nEvent event) {
        // Register custom BBS translation files for English and Spanish
        event.l10n.register((lang) -> List.of(
            new Link("cal", "assets/strings/" + L10n.DEFAULT_LANGUAGE + ".json"),
            new Link("cal", "assets/strings/" + lang + ".json")
        ));
    }
}
