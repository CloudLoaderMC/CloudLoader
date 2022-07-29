/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.loading.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

public class ModConfig
{
    private final Type type;
    private final IConfigSpec<?> spec;
    private final String fileName;
    private final ModContainer container;
    private final ConfigFileTypeHandler configHandler;
    private Config configData;
    private Callable<Void> saveHandler;

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer container, final String fileName, ConfigFileTypeHandler configHandler) {
        this.type = type;
        this.spec = spec;
        this.fileName = fileName;
        this.container = container;
        this.configHandler = configHandler;
        ConfigTracker.INSTANCE.trackConfig(this);
    }

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer container, final String fileName) {
        this(type, spec, container, fileName, ConfigFileTypeHandler.TOML);
    }

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer activeContainer, ConfigFileTypeHandler configHandler) {
        this(type, spec, activeContainer, defaultConfigName(type, activeContainer.getModId(), configHandler.extension), configHandler);
    }

    public ModConfig(final Type type, final IConfigSpec<?> spec, final ModContainer activeContainer) {
        this(type, spec, activeContainer, defaultConfigName(type, activeContainer.getModId(), ConfigFileTypeHandler.TOML.extension));
    }

    private static String defaultConfigName(Type type, String modId, String extension) {
        // config file name would be "forge-client.toml" and "forge-server.toml"
        return String.format(Locale.ROOT, "%s-%s.%s", modId, type.extension(), extension);
    }
    public Type getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public ConfigFileTypeHandler getHandler() {
        return configHandler;
    }

    @SuppressWarnings("unchecked")
    public <T extends IConfigSpec<T>> IConfigSpec<T> getSpec() {
        return (IConfigSpec<T>) spec;
    }

    public String getModId() {
        return container.getModId();
    }

    public Config getConfigData() {
        return this.configData;
    }

    void setConfigData(final Config configData) {
        this.configData = configData;
        this.spec.acceptConfig(this.configData);
    }

    void fireEvent(final IConfigEvent configEvent) {
        this.container.dispatchConfigEvent(configEvent);
    }

    public void save() {
        ((FileConfig) this.configData).save();
    }

    public Path getFullPath() {
        return ((FileConfig)this.configData).getNioPath();
    }

    public void acceptSyncedConfig(byte[] bytes) {
        if (fileName.endsWith(".json")) {
            setConfigData(JsonFormat.fancyInstance().createParser().parse(new ByteArrayInputStream(bytes)));
        }
        else {
            setConfigData(TomlFormat.instance().createParser().parse(new ByteArrayInputStream(bytes)));
        }
        fireEvent(IConfigEvent.reloading(this));
    }

    public enum Type {
        /**
         * Common mod config for configuration that needs to be loaded on both environments.
         * Loaded on both servers and clients.
         * Stored in the global config directory.
         * Not synced.
         * Suffix is "-common" by default.
         */
        COMMON,
        /**
         * Client config is for configuration affecting the ONLY client state such as graphical options.
         * Only loaded on the client side.
         * Stored in the global config directory.
         * Not synced.
         * Suffix is "-client" by default.
         */
        CLIENT,
//        /**
//         * Player type config is configuration that is associated with a player.
//         * Preferences around machine states, for example.
//         */
//        PLAYER,
        /**
         * Server type config is configuration that is associated with a server instance.
         * Only loaded during server startup.
         * Stored in a server/save specific "serverconfig" directory.
         * Synced to clients during connection.
         * Suffix is "-server" by default.
         */
        SERVER;

        public String extension() {
            return StringUtils.toLowerCase(name());
        }
    }
}
