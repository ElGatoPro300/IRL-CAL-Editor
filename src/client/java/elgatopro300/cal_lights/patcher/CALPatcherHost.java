package elgatopro300.cal_lights.patcher;

import org.qualet.irl.patcher.PatcherHost;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import net.irisshaders.iris.Iris;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CALPatcherHost implements PatcherHost {
    private static final Logger LOG = LoggerFactory.getLogger("IRL CAL Editor Patcher");

    private static final List<String> BUNDLED = List.of(
        "bliss.irlights",
        "bsl.irlights",
        "complementaryreimagined.irlights",
        "photon.irlights",
        "rethinkingvoxels.irlights",
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
