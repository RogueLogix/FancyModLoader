/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.util.function.Consumer;

public interface APIWindow extends AutoCloseable {
    @Override
    void close();

    long windowHandle();

    void doDraw(Consumer<APIRenderer> drawFunc, boolean b);
}
