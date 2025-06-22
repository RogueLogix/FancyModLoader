/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import net.neoforged.fml.earlydisplay.util.IntSize;

public interface APIRenderer {
    void beginDrawing(MaterializedTheme theme);

    void endDrawing();

    void pushDebugGroup(String name);

    void popDebugGroup();

    IntSize framebufferSize();

    void setLayoutSize(IntSize layoutSize);

    void setViewport(int x, int y, int width, int height);

    void setScissor(boolean enabled, int x, int y, int width, int height);

    void draw(SimpleBufferBuilder builder, String shader, Texture texture);
}
