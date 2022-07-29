package ml.darubyminer360.cloudloader;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import static net.minecraftforge.fml.Logging.CORE;

public class CloudVersion {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "cloudloader";

    private static final String cloudVersion;

    static {
        // TODO: Automate this?
        cloudVersion = "0.0.1";
        LOGGER.debug(CORE, "Found Cloud version {}", cloudVersion);
    }

    public static String getVersion() {
        return cloudVersion;
    }

    public static VersionChecker.Status getStatus() {
        return VersionChecker.getResult(ModList.get().getModFileById(MOD_ID).getMods().get(0)).status();
    }

    @Nullable
    public static String getTarget() {
        // Forge is 0, and Cloud is 1. Will try to change later.
        VersionChecker.CheckResult res = VersionChecker.getResult(ModList.get().getModFileById(MOD_ID).getMods().get(1));
        return res.target() == null ? "" : res.target().toString();
    }
}
