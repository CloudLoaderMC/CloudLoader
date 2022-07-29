/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.*;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import ml.darubyminer360.cloud.loading.FMLClassLoaderInterface;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.entrypoint.EntrypointUtils;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.FabricMixinBootstrap;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.minecraftforge.fml.loading.moddiscovery.BackgroundScanHandler;
import net.minecraftforge.fml.loading.moddiscovery.ModDiscoverer;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModValidator;
import net.minecraftforge.accesstransformer.service.AccessTransformerService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.progress.EarlyProgressVisualization;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import net.minecraftforge.fml.loading.targets.CommonLaunchHandler;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.coremod.ICoreModProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.minecraftforge.fml.loading.LogMarkers.CORE;
import static net.minecraftforge.fml.loading.LogMarkers.SCAN;

public class FMLLoader extends FabricLauncherBase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static AccessTransformerService accessTransformer;
    private static ModDiscoverer modDiscoverer;
    private static ICoreModProvider coreModProvider;
    private static ILaunchPluginService eventBus;
    private static LanguageLoadingProvider languageLoadingProvider;
    private static Dist dist;
    private static String naming;
    private static LoadingModList loadingModList;
    private static RuntimeDistCleaner runtimeDistCleaner;
    private static Path gamePath;
    private static VersionInfo versionInfo;
    private static String launchHandlerName;
    private static CommonLaunchHandler commonLaunchHandler;
    public static Runnable progressWindowTick;
    private static ModValidator modValidator;
    public static BackgroundScanHandler backgroundScanHandler;
    private static boolean production;
    private static IModuleLayerManager moduleLayerManager;


    protected Map<String, Object> properties = new HashMap<>();

    private FMLClassLoaderInterface classLoader;
    private boolean isDevelopment;
    private EnvType envType;
    private final List<Path> classPath = new ArrayList<>();
    private GameProvider provider;
    private boolean unlocked;

    private FMLLoader(EnvType envType) {
        super();
        this.envType = envType;
    }

    static void onInitialLoad(ArgumentHandler argumentHandler, IEnvironment environment, Set<String> otherServices) throws IncompatibleEnvironmentException
    {
        FMLLoader loader = new FMLLoader(argumentHandler.getLaunchTarget().contains("client") ? EnvType.CLIENT : EnvType.SERVER);
        final String version = LauncherVersion.getVersion();
        LOGGER.debug(CORE,"FML {} loading", version);
        final Package modLauncherPackage = ITransformationService.class.getPackage();
        LOGGER.debug(CORE,"FML found ModLauncher version : {}", modLauncherPackage.getImplementationVersion());
        if (!modLauncherPackage.isCompatibleWith("4.0")) {
            LOGGER.error(CORE, "Found incompatible ModLauncher specification : {}, version {} from {}", modLauncherPackage.getSpecificationVersion(), modLauncherPackage.getImplementationVersion(), modLauncherPackage.getImplementationVendor());
            throw new IncompatibleEnvironmentException("Incompatible modlauncher found "+modLauncherPackage.getSpecificationVersion());
        }

        accessTransformer = (AccessTransformerService) environment.findLaunchPlugin("accesstransformer").orElseThrow(()-> {
            LOGGER.error(CORE, "Access Transformer library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing AccessTransformer, cannot run");
        });

        final Package atPackage = accessTransformer.getClass().getPackage();
        LOGGER.debug(CORE,"FML found AccessTransformer version : {}", atPackage.getImplementationVersion());
        if (!atPackage.isCompatibleWith("1.0")) {
            LOGGER.error(CORE, "Found incompatible AccessTransformer specification : {}, version {} from {}", atPackage.getSpecificationVersion(), atPackage.getImplementationVersion(), atPackage.getImplementationVendor());
            throw new IncompatibleEnvironmentException("Incompatible accesstransformer found "+atPackage.getSpecificationVersion());
        }

        eventBus = environment.findLaunchPlugin("eventbus").orElseThrow(()-> {
            LOGGER.error(CORE, "Event Bus library is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing EventBus, cannot run");
        });

        final Package eventBusPackage = eventBus.getClass().getPackage();
        LOGGER.debug(CORE,"FML found EventBus version : {}", eventBusPackage.getImplementationVersion());
        if (!eventBusPackage.isCompatibleWith("1.0")) {
            LOGGER.error(CORE, "Found incompatible EventBus specification : {}, version {} from {}", eventBusPackage.getSpecificationVersion(), eventBusPackage.getImplementationVersion(), eventBusPackage.getImplementationVendor());
            throw new IncompatibleEnvironmentException("Incompatible eventbus found "+eventBusPackage.getSpecificationVersion());
        }

        runtimeDistCleaner = (RuntimeDistCleaner)environment.findLaunchPlugin("runtimedistcleaner").orElseThrow(()-> {
            LOGGER.error(CORE, "Dist Cleaner is missing, we need this to run");
            return new IncompatibleEnvironmentException("Missing DistCleaner, cannot run!");
        });
        LOGGER.debug(CORE, "Found Runtime Dist Cleaner");

        var coreModProviders = ServiceLoaderUtils.streamWithErrorHandling(ServiceLoader.load(FMLLoader.class.getModule().getLayer(), ICoreModProvider.class), sce -> LOGGER.error(CORE, "Failed to load a coremod library, expect problems", sce)).toList();

        if (coreModProviders.isEmpty()) {
            LOGGER.error(CORE, "Found no coremod provider. Cannot run");
            throw new IncompatibleEnvironmentException("No coremod library found");
        } else if (coreModProviders.size() > 1) {
            LOGGER.error(CORE, "Found multiple coremod providers : {}. Cannot run", coreModProviders.stream().map(p -> p.getClass().getName()).collect(Collectors.toList()));
            throw new IncompatibleEnvironmentException("Multiple coremod libraries found");
        }

        coreModProvider = coreModProviders.get(0);
        final Package coremodPackage = coreModProvider.getClass().getPackage();
        LOGGER.debug(CORE,"FML found CoreMod version : {}", coremodPackage.getImplementationVersion());


        loader.isDevelopment = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));

        loader.classPath.clear();

        List<String> missing = null;
        List<String> unsupported = null;

        for (String cpEntry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (cpEntry.equals("*") || cpEntry.endsWith(File.separator + "*")) {
                if (unsupported == null) unsupported = new ArrayList<>();
                unsupported.add(cpEntry);
                continue;
            }

            Path path = Paths.get(cpEntry);

            if (!Files.exists(path)) {
                if (missing == null) missing = new ArrayList<>();
                missing.add(cpEntry);
                continue;
            }

            loader.classPath.add(LoaderUtil.normalizeExistingPath(path));
        }

        loader.provider = loader.createGameProvider(argumentHandler.buildArgumentList());

        // Setup classloader
        // TODO: Provide KnotCompatibilityClassLoader in non-exclusive-Fabric pre-1.13 environments?
        boolean useCompatibility = loader.provider.requiresUrlClassLoader() || Boolean.parseBoolean(System.getProperty("fabric.loader.useCompatibilityClassLoader", "false"));
        loader.classLoader = FMLClassLoaderInterface.create(useCompatibility, loader.isDevelopment(), loader.envType, loader.provider);
        ClassLoader cl = loader.classLoader.getClassLoader();

        loader.provider.initialize(loader);

        Thread.currentThread().setContextClassLoader(cl);

        FabricLoaderImpl fabricLoader = FabricLoaderImpl.INSTANCE;
        fabricLoader.setGameProvider(loader.provider);
        fabricLoader.load();
        fabricLoader.freeze();

        FabricLoaderImpl.INSTANCE.loadAccessWideners();

        FabricMixinBootstrap.init(fabricLoader.getEnvironmentType(), fabricLoader);
        FabricLauncherBase.finishMixinBootstrapping();

        loader.classLoader.initializeTransformers();

        loader.provider.unlockClassPath(loader);
        loader.unlocked = true;


        LOGGER.debug(CORE, "Found CloudSPI package implementation version {}", Environment.class.getPackage().getImplementationVersion());
        LOGGER.debug(CORE, "Found CloudSPI package specification {}", Environment.class.getPackage().getSpecificationVersion());
        if (Integer.parseInt(Environment.class.getPackage().getSpecificationVersion()) < 2) {
            LOGGER.error(CORE, "Found an out of date CloudSPI implementation: {}, loading cannot continue", Environment.class.getPackage().getSpecificationVersion());
            throw new IncompatibleEnvironmentException("CloudSPI is out of date, we cannot continue");
        }

        try {
            Class.forName("com.electronwill.nightconfig.core.Config", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.toml.TomlFormat", false, environment.getClass().getClassLoader());
            Class.forName("com.electronwill.nightconfig.json.JsonFormat", false, environment.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error(CORE, "Failed to load NightConfig");
            throw new IncompatibleEnvironmentException("Missing NightConfig");
        }


        try {
            EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
        } catch (RuntimeException e) {
            throw new FormattedException("A mod crashed on startup!", e);
        }
    }

    static void setupLaunchHandler(final IEnvironment environment, final Map<String, Object> arguments)
    {
        final String launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("MISSING");
        arguments.put("launchTarget", launchTarget);
        final Optional<ILaunchHandlerService> launchHandler = environment.findLaunchHandler(launchTarget);
        LOGGER.debug(CORE, "Using {} as launch service", launchTarget);
        if (launchHandler.isEmpty()) {
            LOGGER.error(CORE, "Missing LaunchHandler {}, cannot continue", launchTarget);
            throw new RuntimeException("Missing launch handler: " + launchTarget);
        }

        if (!(launchHandler.get() instanceof CommonLaunchHandler)) {
            LOGGER.error(CORE, "Incompatible Launch handler found - type {}, cannot continue", launchHandler.get().getClass().getName());
            throw new RuntimeException("Incompatible launch handler found");
        }
        commonLaunchHandler = (CommonLaunchHandler)launchHandler.get();
        launchHandlerName = launchHandler.get().name();
        gamePath = environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(Paths.get(".").toAbsolutePath());

        naming = commonLaunchHandler.getNaming();
        dist = commonLaunchHandler.getDist();
        production = commonLaunchHandler.isProduction();

        versionInfo = new VersionInfo(arguments);

        StartupMessageManager.modLoaderConsumer().ifPresent(c->c.accept("Early Loading!"));
        accessTransformer.getExtension().accept(Pair.of(naming, "srg"));

        LOGGER.debug(CORE,"Received command line version data  : {}", versionInfo);

        runtimeDistCleaner.getExtension().accept(dist);
    }
    public static List<ITransformationService.Resource> beginModScan(final Map<String,?> arguments)
    {
        LOGGER.debug(SCAN,"Scanning for Mod Locators");
        modDiscoverer = new ModDiscoverer(arguments);
        modValidator = modDiscoverer.discoverMods();
        var pluginResources = modValidator.getPluginResources();
        return List.of(pluginResources);
    }

    public static List<ITransformationService.Resource> completeScan(IModuleLayerManager layerManager) {
        progressWindowTick = EarlyProgressVisualization.INSTANCE.accept(dist, commonLaunchHandler.isData(), versionInfo.mcVersion());
        moduleLayerManager = layerManager;
        languageLoadingProvider = new LanguageLoadingProvider();
        backgroundScanHandler = modValidator.stage2Validation();
        loadingModList = backgroundScanHandler.getLoadingModList();
        return List.of(modValidator.getModResources());
    }

    public static ICoreModProvider getCoreModProvider() {
        return coreModProvider;
    }

    public static LanguageLoadingProvider getLanguageLoadingProvider()
    {
        return languageLoadingProvider;
    }

    static ModDiscoverer getModDiscoverer() {
        return modDiscoverer;
    }

    public static CommonLaunchHandler getLaunchHandler() {
        return commonLaunchHandler;
    }

    public static void addAccessTransformer(Path atPath, ModFile modName)
    {
        LOGGER.debug(SCAN, "Adding Access Transformer in {}", modName.getFilePath());
        accessTransformer.offerResource(atPath, modName.getFileName());
    }

    public static Dist getDist()
    {
        return dist;
    }

    public static void beforeStart(ClassLoader launchClassLoader)
    {
        StartupMessageManager.modLoaderConsumer().ifPresent(c->c.accept("Launching minecraft"));
        progressWindowTick.run();
    }

    public static LoadingModList getLoadingModList()
    {
        return loadingModList;
    }

    public static Path getGamePath()
    {
        return gamePath;
    }

    public static String getNaming() {
        return naming;
    }

    public static Optional<BiFunction<INameMappingService.Domain, String, String>> getNameFunction(final String naming) {
        return Launcher.INSTANCE.environment().findNameMapping(naming);
    }

    public static String getLauncherInfo() {
        return Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElse("MISSING");
    }

    public static List<Map<String, String>> modLauncherModList() {
        return Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MODLIST.get()).orElseGet(Collections::emptyList);
    }

    public static String launcherHandlerName() {
        return launchHandlerName;
    }

    public static boolean isProduction() {
        return production;
    }

    public static boolean isSecureJarEnabled() {
        return true;
    }

    public static ModuleLayer getGameLayer() {
        return moduleLayerManager.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();
    }

    public static VersionInfo versionInfo() {
        return versionInfo;
    }

    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {
        LOGGER.debug("Adding " + path + " to classpath.");

        classLoader.setAllowedPrefixes(path, allowedPrefixes);
        classLoader.addCodeSource(path);
    }

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {
        classLoader.setAllowedPrefixes(path, prefixes);
    }

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {
        classLoader.setValidParentClassPath(paths);
    }

    @Override
    public EnvType getEnvironmentType() {
        return envType;
    }

    @Override
    public boolean isClassLoaded(String name) {
        return classLoader.isClassLoaded(name);
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        return classLoader.loadIntoTarget(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return classLoader.getClassLoader().getResourceAsStream(name);
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        FMLClassLoaderInterface classLoader = this.classLoader;

        return classLoader != null ? classLoader.getClassLoader() : null;
    }

    @Override
    public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
        if (!unlocked) throw new IllegalStateException("early getClassByteArray access");

        if (runTransformers) {
            return classLoader.getPreMixinClassBytes(name);
        } else {
            return classLoader.getRawClassBytes(name);
        }
    }

    @Override
    public Manifest getManifest(Path originPath) {
        return classLoader.getManifest(originPath);
    }

    @Override
    public boolean isDevelopment() {
        return isDevelopment;
    }

    @Override
    public String getEntrypoint() {
        return provider.getEntrypoint();
    }

    @Override
    public String getTargetNamespace() {
        // TODO: Won't work outside of Yarn
        return isDevelopment ? "named" : "intermediary";
    }

    @Override
    public List<Path> getClassPath() {
        return classPath;
    }

    private GameProvider createGameProvider(String[] args) {
        // fast path with direct lookup

        GameProvider embeddedGameProvider = findEmbedddedGameProvider();

        if (embeddedGameProvider != null
                && embeddedGameProvider.isEnabled()
                && embeddedGameProvider.locateGame(this, args)) {
            return embeddedGameProvider;
        }

        // slow path with service loader

        List<GameProvider> failedProviders = new ArrayList<>();

        for (GameProvider provider : ServiceLoader.load(GameProvider.class)) {
            if (!provider.isEnabled())
                continue; // don't attempt disabled providers and don't include them in the error report

            if (provider != embeddedGameProvider) { // don't retry already failed provider
                if (provider.locateGame(this, args)) {
                    return provider;
                }
            }

            failedProviders.add(provider);
        }

        // nothing found

        String msg;

        if (failedProviders.isEmpty()) {
            msg = "No game providers present on the class path!";
        } else if (failedProviders.size() == 1) {
            msg = String.format("%s game provider couldn't locate the game! "
                            + "The game may be absent from the class path, lacks some expected files, suffers from jar "
                            + "corruption or is of an unsupported variety/version.",
                    failedProviders.get(0).getGameName());
        } else {
            msg = String.format("None of the game providers (%s) were able to locate their game!",
                    failedProviders.stream().map(GameProvider::getGameName).collect(Collectors.joining(", ")));
        }

        LOGGER.error(msg);

        throw new RuntimeException(msg);
    }

    /**
     * Find game provider embedded into the Fabric Loader jar, best effort.
     *
     * <p>This is faster than going through service loader because it only looks at a single jar.
     */
    private static GameProvider findEmbedddedGameProvider() {
        try {
            Path flPath = UrlUtil.getCodeSource(FMLLoader.class);
            if (flPath == null || !flPath.getFileName().toString().endsWith(".jar")) return null; // not a jar

            try (ZipFile zf = new ZipFile(flPath.toFile())) {
                ZipEntry entry = zf.getEntry("META-INF/services/net.fabricmc.loader.impl.game.GameProvider"); // same file as used by service loader
                if (entry == null) return null;

                try (InputStream is = zf.getInputStream(entry)) {
                    byte[] buffer = new byte[100];
                    int offset = 0;
                    int len;

                    while ((len = is.read(buffer, offset, buffer.length - offset)) >= 0) {
                        offset += len;
                        if (offset == buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
                    }

                    String content = new String(buffer, 0, offset, StandardCharsets.UTF_8).trim();
                    if (content.indexOf('\n') >= 0) return null; // potentially more than one entry -> bail out

                    int pos = content.indexOf('#');
                    if (pos >= 0) content = content.substring(0, pos).trim();

                    if (!content.isEmpty()) {
                        return (GameProvider) Class.forName(content).getConstructor().newInstance();
                    }
                }
            }

            return null;
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
