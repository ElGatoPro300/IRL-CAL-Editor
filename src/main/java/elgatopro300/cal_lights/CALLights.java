package elgatopro300.cal_lights;

import elgatopro300.cal_lights.integration.bbs.BbsAddon;
import elgatopro300.cal_lights.integration.bbs.CALBbsForm;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CALLights implements ModInitializer {
    public static final String MOD_ID = "cal";
    public static final Logger LOGGER = LoggerFactory.getLogger("CAL Lights");

    @Override
    public void onInitialize() {
        LOGGER.info("CAL Lights mod initialized on Server/Common!");

        try {
            Class.forName("mchorse.bbs_mod.BBS");
            mchorse.bbs_mod.BBS.getEvents().register(new BbsAddon());
            if (mchorse.bbs_mod.BBS.getForms() != null) {
                mchorse.bbs_mod.BBS.getForms().register(BbsAddon.FORM_LINK, CALBbsForm.class);
            }
            LOGGER.info("Successfully registered BBS Server/Common integration!");
        } catch (ClassNotFoundException ignored) {
            LOGGER.info("BBS Server/Common not detected, continuing standalone.");
        }
    }
}
