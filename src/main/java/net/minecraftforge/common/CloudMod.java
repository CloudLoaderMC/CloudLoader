package net.minecraftforge.common;

import ml.darubyminer360.cloudloader.CloudVersion;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.minecraftforge.versions.mcp.MCPVersion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.*;

@Mod("cloudloader")
public class CloudMod
{
    public static final String VERSION_CHECK_CAT = "version_checking";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker CLOUDMOD = MarkerManager.getMarker("CLOUDMOD");

    private static CloudMod INSTANCE;
    public static CloudMod getInstance()
    {
        return INSTANCE;
    }

    public CloudMod()
    {
        LOGGER.info(CLOUDMOD,"Cloud mod loading, version {}, for MC {} with Forge {} and Fabric {}", CloudVersion.getVersion(), MCPVersion.getMCVersion(), ForgeVersion.getVersion(), FabricLoaderImpl.VERSION);
        INSTANCE = this;
//        MinecraftForge.initialize();
        CrashReportCallables.registerCrashCallable("Crash Report UUID", ()-> {
            final UUID uuid = UUID.randomUUID();
            LOGGER.fatal("Preparing crash report with UUID {}", uuid);
            return uuid.toString();
        });

        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::loadComplete);
        modEventBus.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModIDAliasConfig.commonSpec);
        modEventBus.register(ModIDAliasConfig.class);
        // Forge does not display problems when the remote is not matching.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, ()->new IExtensionPoint.DisplayTest(()->"ANY", (remote, isServer)-> true));
        StartupMessageManager.addModMessage("Cloud version " + CloudVersion.getVersion());
    }

    public void preInit(FMLCommonSetupEvent evt)
    {
        VersionChecker.startVersionCheck();
    }

    public void loadComplete(FMLLoadCompleteEvent event)
    {
    }
}
