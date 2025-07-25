/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.modscan.Scanner;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@ApiStatus.Internal
public class ModFile implements IModFile {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String jarVersion;
    private final ModFileInfoParser parser;
    private ModFileDiscoveryAttributes discoveryAttributes;
    private Map<String, Object> fileProperties;
    private List<IModLanguageLoader> loaders;
    private final SecureJar jar;
    private final Type modFileType;
    private final Manifest manifest;
    private IModFileInfo modFileInfo;
    @Nullable
    private CompletableFuture<ModFileScanData> futureScanResult;
    private List<ModFileParser.MixinConfig> mixinConfigs;
    private List<Path> accessTransformers;

    public static final Attributes.Name TYPE = new Attributes.Name("FMLModType");

    public ModFile(SecureJar jar, final ModFileInfoParser parser, ModFileDiscoveryAttributes attributes) {
        this(jar, parser, parseType(jar), attributes);
    }

    public ModFile(SecureJar jar, ModFileInfoParser parser, Type type, ModFileDiscoveryAttributes discoveryAttributes) {
        this.jar = Objects.requireNonNull(jar, "jar");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.discoveryAttributes = Objects.requireNonNull(discoveryAttributes, "discoveryAttributes");

        manifest = this.jar.moduleDataProvider().getManifest();
        modFileType = Objects.requireNonNull(type, "type");
        jarVersion = Optional.ofNullable(manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION)).orElse("0.0NONE");
        this.modFileInfo = ModFileParser.readModList(this, this.parser);
    }

    @Override
    public Supplier<Map<String, Object>> getSubstitutionMap() {
        return () -> ImmutableMap.<String, Object>builder().put("jarVersion", jarVersion).putAll(fileProperties).build();
    }

    public List<IModLanguageLoader> getLoaders() {
        return loaders;
    }

    @Override
    public Type getType() {
        return modFileType;
    }

    @Override
    public Path getFilePath() {
        return jar.getPrimaryPath();
    }

    @Override
    public SecureJar getSecureJar() {
        return this.jar;
    }

    @Override
    public List<IModInfo> getModInfos() {
        return modFileInfo.getMods();
    }

    public List<Path> getAccessTransformers() {
        return accessTransformers;
    }

    public boolean identifyMods() {
        this.modFileInfo = ModFileParser.readModList(this, this.parser);
        if (this.modFileInfo == null) return this.getType() != Type.MOD;
        LOGGER.debug(LogMarkers.LOADING, "Loading mod file {} with languages {}", this.getFilePath(), this.modFileInfo.requiredLanguageLoaders());
        this.mixinConfigs = ModFileParser.getMixinConfigs(this.modFileInfo);
        this.mixinConfigs.forEach(mc -> LOGGER.debug(LogMarkers.LOADING, "Found mixin config {}", mc));
        this.accessTransformers = ModFileParser.getAccessTransformers(this.modFileInfo)
                .map(list -> list.stream().map(this::findResource).filter(path -> {
                    if (Files.notExists(path)) {
                        LOGGER.error(LogMarkers.LOADING, "Access transformer file {} provided by mod {} does not exist!", path, modFileInfo.moduleName());
                        return false;
                    }
                    return true;
                }))
                .orElseGet(() -> Stream.of(findResource("META-INF", "accesstransformer.cfg"))
                        .filter(Files::exists))
                .toList();
        return true;
    }

    public List<ModFileParser.MixinConfig> getMixinConfigs() {
        return mixinConfigs;
    }

    public void scanFile(Consumer<Path> pathConsumer) {
        var rootPath = getSecureJar().getRootPath();
        try (Stream<Path> files = Files.find(rootPath, Integer.MAX_VALUE, (p, a) -> p.getNameCount() > 0 && p.getFileName().toString().endsWith(".class"))) {
            files.forEach(pathConsumer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan " + rootPath, e);
        }
    }

    public CompletionStage<ModFileScanData> startScan(Executor executor) {
        if (this.futureScanResult != null) {
            throw new IllegalStateException("The mod file scan was already started.");
        }

        this.futureScanResult = CompletableFuture.supplyAsync(() -> new Scanner(this).scan(), executor);
        return this.futureScanResult;
    }

    @Override
    public ModFileScanData getScanResult() {
        if (this.futureScanResult == null) {
            throw new IllegalStateException("Scanning of this mod file has not started yet.");
        }
        try {
            return this.futureScanResult.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for mod file scan to complete.");
        }
    }

    public void setFileProperties(Map<String, Object> fileProperties) {
        this.fileProperties = fileProperties;
    }

    @Override
    public Path findResource(String... path) {
        if (path.length < 1) {
            throw new IllegalArgumentException("Missing path");
        }
        return getSecureJar().getPath(String.join("/", path));
    }

    public void identifyLanguage() {
        this.loaders = this.modFileInfo.requiredLanguageLoaders().stream()
                .map(spec -> FMLLoader.getLanguageLoadingProvider().findLanguage(this, spec.languageName(), spec.acceptedVersions()))
                .toList();
    }

    @Override
    public String toString() {
        if (discoveryAttributes.parent() != null) {
            return "Nested Mod File " + this.jar.getPrimaryPath() + " in " + discoveryAttributes.parent();
        } else {
            return "Mod File: " + this.jar.getPrimaryPath();
        }
    }

    @Override
    public String getFileName() {
        return getFilePath().getFileName().toString();
    }

    @Override
    public ModFileDiscoveryAttributes getDiscoveryAttributes() {
        return discoveryAttributes;
    }

    public void setDiscoveryAttributes(ModFileDiscoveryAttributes discoveryAttributes) {
        this.discoveryAttributes = discoveryAttributes;
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return modFileInfo;
    }

    public ArtifactVersion getJarVersion() {
        return new DefaultArtifactVersion(this.jarVersion);
    }

    private static Type parseType(final SecureJar jar) {
        final Manifest m = jar.moduleDataProvider().getManifest();
        final Optional<String> value = Optional.ofNullable(m.getMainAttributes().getValue(TYPE));
        return value.map(Type::valueOf).orElse(Type.MOD);
    }
}
