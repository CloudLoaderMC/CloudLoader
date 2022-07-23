/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.discovery.ModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModDiscoverer;
import net.fabricmc.loader.impl.metadata.*;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.ModFileFactory;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModFileParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo readModList(final ModFile modFile, final ModFileFactory.ModFileInfoParser parser) {
        return parser.build(modFile);
    }

    public static IModFileInfo fabricModJsonParser(final IModFile imodFile) {
        ModFile modFile = (ModFile) imodFile;
        LOGGER.debug(LogMarkers.LOADING,"Considering mod file candidate {}", modFile.getFilePath());
        final Path modsjson = modFile.findResource("fabric.mod.json");
        if (!Files.exists(modsjson)) {
            LOGGER.warn(LogMarkers.LOADING, "Mod file {} is missing fabric.mod.json file", modFile.getFilePath());
            return null;
        }

        final FileConfig fileConfig = FileConfig.builder(modsjson).build();
        fileConfig.load();
        fileConfig.close();
        if (!fileConfig.contains("modLoader")) {
            fileConfig.set("modLoader", "lowcodefml");
        }
        if (!fileConfig.contains("loaderVersion")) {
            fileConfig.set("loaderVersion", "[41,)");
        }
        final NightConfigWrapper configWrapper = new NightConfigWrapper(fileConfig);

        VersionOverrides versionOverrides = new VersionOverrides();
        DependencyOverrides depOverrides = new DependencyOverrides(FMLPaths.CONFIGDIR.get());
        ModFileInfo modFileInfo = null; // Shouldn't ever catch either of the exceptions but the try/catch statement is required.
        try (ZipFile zf = new ZipFile(modFile.getFilePath().toFile())) {
            ZipEntry entry = zf.getEntry("fabric.mod.json");
            if (entry == null)
                return null;

            try (InputStream is = zf.getInputStream(entry)) {
                modFileInfo = new ModFileInfo(modFile, configWrapper, ModMetadataParser.parseMetadata(is, versionOverrides, depOverrides));
                configWrapper.setFile(modFileInfo);
            }
        } catch (IOException | ParseMetadataException e) {
            e.printStackTrace();
        }
        return modFileInfo;
    }

    public static IModFileInfo modsTomlParser(final IModFile imodFile) {
        ModFile modFile = (ModFile) imodFile;
        LOGGER.debug(LogMarkers.LOADING,"Considering mod file candidate {}", modFile.getFilePath());
        final Path modsjson = modFile.findResource("META-INF", "mods.toml");
        if (!Files.exists(modsjson)) {
            LOGGER.warn(LogMarkers.LOADING, "Mod file {} is missing mods.toml file", modFile.getFilePath());
            return null;
        }

        final FileConfig fileConfig = FileConfig.builder(modsjson).build();
        fileConfig.load();
        fileConfig.close();
        final NightConfigWrapper configWrapper = new NightConfigWrapper(fileConfig);
        final ModFileInfo modFileInfo = new ModFileInfo(modFile, configWrapper);
        configWrapper.setFile(modFileInfo);
        return modFileInfo;
    }

    protected static List<CoreModFile> getCoreMods(final ModFile modFile) {
        Map<String,String> coreModPaths;
        try {
            final Path coremodsjson = modFile.findResource("META-INF", "coremods.json");
            if (!Files.exists(coremodsjson)) {
                return Collections.emptyList();
            }
            final Type type = new TypeToken<Map<String, String>>() {}.getType();
            final Gson gson = new Gson();
            coreModPaths = gson.fromJson(Files.newBufferedReader(coremodsjson), type);
        } catch (IOException e) {
            LOGGER.debug(LogMarkers.LOADING,"Failed to read coremod list coremods.json", e);
            return Collections.emptyList();
        }

        return coreModPaths.entrySet().stream()
                .peek(e-> LOGGER.debug(LogMarkers.LOADING,"Found coremod {} with Javascript path {}", e.getKey(), e.getValue()))
                .map(e -> new CoreModFile(e.getKey(), modFile.findResource(e.getValue()),modFile))
                .collect(Collectors.toList());
    }
}
