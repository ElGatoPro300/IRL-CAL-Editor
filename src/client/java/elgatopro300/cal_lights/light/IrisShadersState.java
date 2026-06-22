package elgatopro300.cal_lights.light;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

public final class IrisShadersState {
    private static boolean broken;

    private IrisShadersState() {}

    public static boolean shadersDisabled() {
        if (broken) {
            return false;
        }

        try {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            return pipeline instanceof VanillaRenderingPipeline;
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }
}
