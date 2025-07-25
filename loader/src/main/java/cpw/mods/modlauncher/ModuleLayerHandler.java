/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2021 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.NamedPath;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ModuleLayerHandler implements IModuleLayerManager {
    private static final Logger LOGGER = LogManager.getLogger();

    record LayerInfo(ModuleLayer layer, ModuleClassLoader cl) {}

    private record PathOrJar(NamedPath path, SecureJar jar) {
        static PathOrJar from(SecureJar jar) {
            return new PathOrJar(null, jar);
        }

        static PathOrJar from(NamedPath path) {
            return new PathOrJar(path, null);
        }

        SecureJar build() {
            return jar != null ? jar : SecureJar.from(path.paths());
        }
    }

    private final EnumMap<Layer, List<PathOrJar>> layers = new EnumMap<>(Layer.class);
    private final EnumMap<Layer, LayerInfo> completedLayers = new EnumMap<>(Layer.class);

    ModuleLayerHandler() {
        ClassLoader classLoader = getClass().getClassLoader();
        // Create a new ModuleClassLoader from the boot module layer if it doesn't exist already.
        // This allows us to launch without BootstrapLauncher.
        ModuleClassLoader cl = classLoader instanceof ModuleClassLoader moduleCl ? moduleCl
                : new ModuleClassLoader("BOOT", ModuleLayer.boot().configuration(), List.of());
        completedLayers.put(Layer.BOOT, new LayerInfo(getClass().getModule().getLayer(), cl));
    }

    void addToLayer(final Layer layer, final SecureJar jar) {
        if (completedLayers.containsKey(layer)) throw new IllegalStateException("Layer already populated");
        layers.computeIfAbsent(layer, l -> new ArrayList<>()).add(PathOrJar.from(jar));
    }

    void addToLayer(final Layer layer, final NamedPath namedPath) {
        if (completedLayers.containsKey(layer)) throw new IllegalStateException("Layer already populated");
        layers.computeIfAbsent(layer, l -> new ArrayList<>()).add(PathOrJar.from(namedPath));
    }

    public LayerInfo buildLayer(final Layer layer, BiFunction<Configuration, List<ModuleLayer>, ModuleClassLoader> classLoaderSupplier) {
        final var finder = layers.getOrDefault(layer, List.of()).stream()
                .map(PathOrJar::build)
                .toArray(SecureJar[]::new);
        final var targets = Arrays.stream(finder).map(SecureJar::name).toList();
        final Configuration newConf;
        try {
            newConf = Configuration.resolveAndBind(JarModuleFinder.of(finder), Arrays.stream(layer.getParent()).map(completedLayers::get).map(li -> li.layer().configuration()).toList(), ModuleFinder.of(), targets);
        } catch (ResolutionException e) {
            // Catch and log errors when two mods have the same package name, or other module loading errors
            // This ensures they make it to the log file.
            LOGGER.error("Error while resolving modules.", e);
            throw e;
        }
        final var allParents = Arrays.stream(layer.getParent()).map(completedLayers::get).map(LayerInfo::layer).<ModuleLayer>mapMulti((moduleLayer, comp) -> {
            comp.accept(moduleLayer);
            moduleLayer.parents().forEach(comp);
        }).toList();
        final var classLoader = classLoaderSupplier.apply(newConf, allParents);
        final var modController = ModuleLayer.defineModules(newConf, Arrays.stream(layer.getParent()).map(completedLayers::get).map(LayerInfo::layer).toList(), f -> classLoader);
        completedLayers.put(layer, new LayerInfo(modController.layer(), classLoader));
        classLoader.setFallbackClassLoader(completedLayers.get(Layer.BOOT).cl());
        return new LayerInfo(modController.layer(), classLoader);
    }

    public LayerInfo buildLayer(final Layer layer) {
        return buildLayer(layer, (cf, p) -> new ModuleClassLoader("LAYER " + layer.name(), cf, p));
    }

    @Override
    public Optional<ModuleLayer> getLayer(final Layer layer) {
        return Optional.ofNullable(completedLayers.get(layer)).map(LayerInfo::layer);
    }

    public void updateLayer(Layer layer, Consumer<LayerInfo> action) {
        action.accept(completedLayers.get(layer));
    }
}
