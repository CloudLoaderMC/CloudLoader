/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.metadata.MetadataVerifier;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;
import net.minecraftforge.fml.loading.StringSubstitutor;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.MavenVersionAdapter;
import net.minecraftforge.forgespi.language.ModLoaderType;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModInfo implements IModInfo, IConfigurable
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DefaultArtifactVersion DEFAULT_VERSION = new DefaultArtifactVersion("1");
    public static final Pattern VALID_MODID = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    public static final Pattern VALID_NAMESPACE = Pattern.compile("^[a-z][a-z0-9_.-]{1,63}$");
    public static final Pattern VALID_VERSION = Pattern.compile("^\\d+.*");

    private final ModFileInfo owningFile;
    private final String modId;
    private final String namespace;

    private final ArtifactVersion version;

    private final String displayName;
    private final String description;
    private final Optional<String> logoFile;
    private final boolean logoBlur;
    private final Optional<URL> updateJSONURL;
    private final List<? extends IModInfo.ModVersion> dependencies;

    private final List<ForgeFeature.Bound> features;
    private final Map<String,Object> properties;
    private final IConfigurable config;
    private final Optional<URL> modUrl;

    public ModInfo(final ModFileInfo owningFile, final IConfigurable config)
    {
        Optional<ModFileInfo> ownFile = Optional.ofNullable(owningFile);
        this.owningFile = owningFile;
        this.config = config;
        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.modId = config.<String>getConfigElement("modId")
                    .orElseThrow(() -> new InvalidModFileException("Missing modId", owningFile));
        }
        else {
            this.modId = config.<String>getConfigElement("id")
                    .orElseThrow(() -> new InvalidModFileException("Missing id", owningFile)).replace("-", "_");
        }

        if (owningFile.getNativeLoader() == ModLoaderType.FORGE && !VALID_MODID.matcher(this.modId).matches()) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Invalid modId found in file {} - {} does not match the standard: {}", this.owningFile.getFile().getFilePath(), this.modId, VALID_MODID.pattern());
            throw new InvalidModFileException("Invalid modId found : " + this.modId, owningFile);
        }
        else if (owningFile.getNativeLoader() == ModLoaderType.FABRIC && !MetadataVerifier.MOD_ID_PATTERN.matcher(this.modId).matches()) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Invalid modId found in file {} - {} does not match the standard: {}", this.owningFile.getFile().getFilePath(), this.modId, MetadataVerifier.MOD_ID_PATTERN.pattern());
            throw new InvalidModFileException("Invalid modId found : " + this.modId, owningFile);
        }

        this.namespace = config.<String>getConfigElement("namespace").orElse(this.modId);
        if (this.owningFile.getNativeLoader() == ModLoaderType.FORGE && !VALID_NAMESPACE.matcher(this.namespace).matches()) {
            LOGGER.error(LogUtils.FATAL_MARKER, "Invalid override namespace found in file {} - {} does not match the standard: {}", this.owningFile.getFile().getFilePath(), this.namespace, VALID_NAMESPACE.pattern());
            throw new InvalidModFileException("Invalid override namespace found : " + this.namespace, owningFile);
        }
        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.version = config.<String>getConfigElement("version")
                    .map(s -> StringSubstitutor.replace(s, ownFile.map(ModFileInfo::getFile).orElse(null)))
                    .map(DefaultArtifactVersion::new).orElse(DEFAULT_VERSION);
        }
        else {
            this.version = config.<String>getConfigElement("version")
                    .map(s -> StringSubstitutor.replace(s, ownFile.map(ModFileInfo::getFile).orElse(null)))
                    .map((version) -> {
                        ArtifactVersion result;
                        try {
                            result = Version.parse(version).toArtifactVersion();
                        } catch (VersionParsingException e) {
                            result = DEFAULT_VERSION;
                        }
                        return result;
                    }).orElseThrow();
        }
        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.displayName = config.<String>getConfigElement("displayName").orElse(this.modId);
        }
        else {
            this.displayName = config.<String>getConfigElement("name").orElse(this.modId);
        }
        this.description = config.<String>getConfigElement("description").orElse("MISSING DESCRIPTION");

        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.logoFile = Optional.ofNullable(config.<String>getConfigElement("logoFile")
                    .orElseGet(() -> ownFile.flatMap(mf -> mf.<String>getConfigElement("logoFile")).orElse(null)));
        }
        else {
            this.logoFile = Optional.ofNullable(config.<String>getConfigElement("icon")
                    .orElseGet(() -> ownFile.flatMap(mf -> mf.<String>getConfigElement("icon")).orElse(null)));
        }
        this.logoBlur = config.<Boolean>getConfigElement("logoBlur")
                .orElseGet(() -> ownFile.flatMap(f -> f.<Boolean>getConfigElement("logoBlur"))
                        .orElse(true));

        this.updateJSONURL = config.<String>getConfigElement("updateJSONURL")
                .map(StringUtils::toURL);

        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.dependencies = ownFile.map(mfi -> mfi.getConfigList("dependencies", this.modId)
                            .stream()
                            .map(dep -> new ModVersion(this, dep))
                            .collect(Collectors.toList()))
                    .orElse(Collections.emptyList());
        }
        else {
            this.dependencies = Stream.of(
                            ownFile.map(mfi -> mfi.<Map.Entry<String, Object>>getConfigElement("depends", this.modId)
                                            .stream()
                                            .map(dep -> new ModVersion(this, dep, true, true))
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList()),
                            ownFile.map(mfi -> mfi.<Map.Entry<String, Object>>getConfigElement("recommends", this.modId)
                                            .stream()
                                            .map(dep -> new ModVersion(this, dep, false, true))
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList()),
                            ownFile.map(mfi -> mfi.<Map.Entry<String, Object>>getConfigElement("suggests", this.modId)
                                            .stream()
                                            .map(dep -> new ModVersion(this, dep, false, true))
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList()),
                            ownFile.map(mfi -> mfi.<Map.Entry<String, Object>>getConfigElement("conflicts", this.modId)
                                            .stream()
                                            .map(dep -> new ModVersion(this, dep, false, true))
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList()),
                            ownFile.map(mfi -> mfi.<Map.Entry<String, Object>>getConfigElement("breaks", this.modId)
                                            .stream()
                                            .map(dep -> new ModVersion(this, dep, true, true))
                                            .collect(Collectors.toList()))
                                    .orElse(Collections.emptyList())
                    )
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        this.features = ownFile.map(mfi -> mfi.<Map<String, String>>getConfigElement("features", this.modId)
                .stream()
                .flatMap(m->m.entrySet().stream())
                .map(e->new ForgeFeature.Bound(e.getKey(), e.getValue(), this))
                .collect(Collectors.toList())).orElse(Collections.emptyList());

        this.properties = ownFile.map(mfi -> mfi.<Map<String, Object>>getConfigElement("modproperties", this.modId)
                .orElse(Collections.emptyMap()))
                .orElse(Collections.emptyMap());

        if (owningFile.getNativeLoader() == ModLoaderType.FORGE) {
            this.modUrl = config.<String>getConfigElement("modUrl")
                    .map(StringUtils::toURL);
        }
        else {
            if (config.<HashMap<String, String>>getConfigElement("contact").isPresent() && config.<HashMap<String, String>>getConfigElement("contact").get().containsKey("homepage")) {
                this.modUrl = Optional.ofNullable(StringUtils.toURL(config.<HashMap<String, String>>getConfigElement("contact").get().get("homepage")));
            }
            else {
                this.modUrl = Optional.empty();
            }
        }

        // verify we have a valid mod version otherwise throw an exception
        if (!VALID_VERSION.matcher(this.version.toString()).matches()) {
            throw new InvalidModFileException("Illegal version number specified "+this.version, this.getOwningFile());
        }
    }

    @Override
    public ModFileInfo getOwningFile() {
        return owningFile;
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public String getDisplayName()
    {
        return this.displayName;
    }

    @Override
    public String getDescription()
    {
        return this.description;
    }

    @Override
    public ArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<? extends IModInfo.ModVersion> getDependencies() {
        return this.dependencies;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public Map<String, Object> getModProperties() {
        return this.properties;
    }

    @Override
    public Optional<URL> getUpdateURL() {
        return this.updateJSONURL;
    }

    @Override
    public Optional<String> getLogoFile()
    {
        return this.logoFile;
    }

    @Override
    public boolean getLogoBlur()
    {
        return this.logoBlur;
    }

    @Override
    public IConfigurable getConfig() {
        return this;
    }

    @Override
    public List<? extends ForgeFeature.Bound> getForgeFeatures() {
        return this.features;
    }

    @Override
    public <T> Optional<T> getConfigElement(final String... key) {
        return this.config.getConfigElement(key);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(final String... key) {
        return null;
    }

    @Override
    public Optional<URL> getModURL() {
        return modUrl;
    }

    public class ModVersion implements net.minecraftforge.forgespi.language.IModInfo.ModVersion {
        private IModInfo owner;
        private final String modId;
        private final VersionRange versionRange;
        private final boolean positive;
        private final boolean mandatory;
        private final Ordering ordering;
        private final DependencySide side;
        private Optional<URL> referralUrl;

        public ModVersion(final IModInfo owner, final IConfigurable config) {
            this.owner = owner;
            this.modId = config.<String>getConfigElement("modId")
                    .orElseThrow(()->new InvalidModFileException("Missing required field modid in dependency", getOwningFile()));
            this.mandatory = config.<Boolean>getConfigElement("mandatory")
                    .orElseThrow(()->new InvalidModFileException("Missing required field mandatory in dependency", getOwningFile()));
            this.positive = true;
            this.versionRange = config.<String>getConfigElement("versionRange")
                    .map(MavenVersionAdapter::createFromVersionSpec)
                    .orElse(UNBOUNDED);
            this.ordering = config.<String>getConfigElement("ordering")
                    .map(Ordering::valueOf)
                    .orElse(Ordering.NONE);
            this.side = config.<String>getConfigElement("side")
                    .map(DependencySide::valueOf)
                    .orElse(DependencySide.BOTH);
            this.referralUrl = config.<String>getConfigElement("referralUrl")
                    .map(StringUtils::toURL);
        }

        public ModVersion(final IModInfo owner, final Map.Entry<String, Object> config, final boolean mandatory, final boolean positive) {
            this.owner = owner;
            this.modId = config.getKey();
            this.mandatory = mandatory;
            this.positive = positive;
            this.versionRange = Optional.of((String) config.getValue())
                    .map((spec) -> {
                        VersionRange result;
                        try {
                            result = VersionPredicateParser.parse(spec).toMavenVersionRange();
                        } catch (VersionParsingException e) {
                            result = null;
                        }
                        return result;
                    }).orElse(UNBOUNDED);
            this.ordering = Ordering.NONE;
            this.side = DependencySide.BOTH;
            this.referralUrl = Optional.empty();
        }

        public ModVersion(final IModInfo owner, final String modId, final VersionRange versionRange, final boolean mandatory, final boolean positive) {
            this.owner = owner;
            this.modId = modId;
            this.mandatory = mandatory;
            this.positive = positive;
            this.versionRange = Optional.of(versionRange).orElse(UNBOUNDED);
            this.ordering = Ordering.NONE;
            this.side = DependencySide.BOTH;
            this.referralUrl = Optional.empty();
        }

        public ModVersion(final IModInfo owner, final String modId, final VersionRange versionRange, final boolean mandatory, final boolean positive, final Ordering ordering, final DependencySide side, final URL referralUrl) {
            this.owner = owner;
            this.modId = modId;
            this.mandatory = mandatory;
            this.positive = positive;
            this.versionRange = Optional.of(versionRange).orElse(UNBOUNDED);
            this.ordering = ordering;
            this.side = side;
            this.referralUrl = Optional.ofNullable(referralUrl);
        }

        public ModVersion build(final IModInfo owner, final String modId, final VersionRange versionRange, final boolean mandatory, final boolean positive) {
            return new ModVersion(owner, modId, versionRange, mandatory, positive);
        }

        public ModVersion build(final IModInfo owner, final String modId, final VersionRange versionRange, final boolean mandatory, final boolean positive, final Ordering ordering, final DependencySide side, final URL referralUrl) {
            return new ModVersion(owner, modId, versionRange, mandatory, positive, ordering, side, referralUrl);
        }

        @Override
        public String getModId()
        {
            return modId;
        }

        @Override
        public VersionRange getVersionRange()
        {
            return versionRange;
        }

        @Override
        public boolean isMandatory()
        {
            return mandatory;
        }

        @Override
        public boolean isPositive()
        {
            return positive;
        }

        @Override
        public Ordering getOrdering()
        {
            return ordering;
        }

        @Override
        public DependencySide getSide()
        {
            return side;
        }

        @Override
        public void setOwner(final IModInfo owner)
        {
            this.owner = owner;
        }

        @Override
        public IModInfo getOwner()
        {
            return owner;
        }

        @Override
        public Optional<URL> getReferralURL() {
            return referralUrl;
        }
    }

}
