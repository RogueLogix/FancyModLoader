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

package cpw.mods.modlauncher.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

class PluginTests {
    @Test
    void pluginTests() {
        ILaunchPluginService plugin = new ILaunchPluginService() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public void offerResource(final Path resource, final String name) {}

            @Override
            public boolean processClass(final Phase phase, final ClassNode classNode, final Type classType) {
                return false;
            }

            @Override
            public String getExtension() {
                return "CHEESE";
            }

            @Override
            public EnumSet<Phase> handlesClass(final Type classType, final boolean isEmpty) {
                return EnumSet.of(Phase.BEFORE);
            }
        };

        String s = plugin.getExtension();
        assertEquals("CHEESE", s);
    }
}
