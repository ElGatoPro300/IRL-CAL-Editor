package elgatopro300.cal_lights.patcher;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.minecraft.util.Util;
import org.qualet.irl.patcher.PatcherHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public final class CALPatcherHost implements PatcherHost {
    private static final Logger LOG = LoggerFactory.getLogger("CAL Lights Patcher");

    private static final List<String> BUNDLED = List.of(
        "bliss.irlights",
        "bsl.irlights",
        "complementaryreimagined.irlights",
        "photon.irlights",
        "solas.irlights"
    );

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path shaderpacksDir() {
        try {
            return Iris.getShaderpacksDirectory();
        } catch (Throwable t) {
            LOG.warn("Iris.getShaderpacksDirectory failed: {}", t.toString());
            return null;
        }
    }

    @Override
    public List<String> listShaderpacks() {
        try {
            return List.copyOf(Iris.getShaderpacksDirectoryManager().enumerate());
        } catch (Throwable t) {
            LOG.warn("Iris enumerate failed: {}", t.toString());
            return List.of();
        }
    }

    @Override
    public void openFolder(Path dir) {
        Util.getOperatingSystem().open(dir.toFile());
    }

    @Override
    public String patchesDirName() {
        return "cal_lights";
    }

    @Override
    public List<String> bundledPatches() {
        return BUNDLED;
    }

    @Override
    public InputStream openBundledPatch(String name) {
        return CALPatcherHost.class.getResourceAsStream("/assets/cal/assets/patches/" + name);
    }
}
