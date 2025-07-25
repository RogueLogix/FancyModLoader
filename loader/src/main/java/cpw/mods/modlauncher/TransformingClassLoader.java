/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
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

import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformerActivity;
import java.lang.module.Configuration;
import java.util.List;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Module transforming class loader
 */
public class TransformingClassLoader extends ModuleClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }
    private final ClassTransformer classTransformer;

    public TransformingClassLoader(TransformStore transformStore, LaunchPluginHandler pluginHandler, IModuleLayerManager moduleLayerHandler) {
        super("TRANSFORMER", moduleLayerHandler.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow().configuration(), List.of(moduleLayerHandler.getLayer(IModuleLayerManager.Layer.SERVICE).orElseThrow()));
        this.classTransformer = new ClassTransformer(transformStore, pluginHandler, this);
    }

    @VisibleForTesting
    public TransformingClassLoader(TransformStore transformStore, LaunchPluginHandler pluginHandler, final Environment environment, final Configuration configuration, List<ModuleLayer> parentLayers) {
        this(transformStore, pluginHandler, environment, configuration, parentLayers, null);
    }

    @VisibleForTesting
    public TransformingClassLoader(TransformStore transformStore, LaunchPluginHandler pluginHandler, final Environment environment, final Configuration configuration, List<ModuleLayer> parentLayers, ClassLoader parentClassLoader) {
        super("TRANSFORMER", configuration, parentLayers, parentClassLoader);
        TransformerAuditTrail tat = new TransformerAuditTrail();
        environment.computePropertyIfAbsent(IEnvironment.Keys.AUDITTRAIL.get(), v -> tat);
        this.classTransformer = new ClassTransformer(transformStore, pluginHandler, this, tat);
    }

    @Override
    protected byte[] maybeTransformClassBytes(final byte[] bytes, final String name, final String context) {
        return classTransformer.transform(bytes, name, context != null ? context : ITransformerActivity.CLASSLOADING_REASON);
    }

    public Class<?> getLoadedClass(String name) {
        return findLoadedClass(name);
    }

    byte[] buildTransformedClassNodeFor(final String className, final String reason) throws ClassNotFoundException {
        return super.getMaybeTransformedClassBytes(className, reason);
    }
}
