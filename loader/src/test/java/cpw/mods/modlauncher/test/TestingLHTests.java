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

import static org.junit.jupiter.api.Assertions.assertTrue;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.ServiceRunner;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TestingLHTests {
    boolean calledback;

    @Disabled
    @Test
    void testTestingLaunchHandler() {
        System.setProperty("test.harness", "build/classes/java/testJars");
        System.setProperty("test.harness.callable", "cpw.mods.modlauncher.test.TestingLHTests$TestCallback");
        calledback = false;
        TestCallback.callable = () -> {
            calledback = true;
            LogManager.getLogger().info("Hello", new Throwable());
        };
        Launcher.main("--version", "1.0", "--launchTarget", "testharness");
        assertTrue(calledback, "We got called back");
    }

    public static class TestCallback {
        private static ServiceRunner callable;

        public static ServiceRunner supplier() {
            return callable;
        }
    }
}
