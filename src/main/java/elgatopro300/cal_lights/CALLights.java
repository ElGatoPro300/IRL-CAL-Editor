package elgatopro300.cal_lights;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLights implements ModInitializer {
    public static final String MOD_ID = "cal";
    public static final Logger LOGGER = LoggerFactory.getLogger("IRL CAL Editor");

    @Override
    public void onInitialize() {
        LOGGER.info("IRL CAL Editor mod initialized on Server/Common!");
    }
}
