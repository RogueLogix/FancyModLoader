/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.nio.file.Path;

import net.neoforged.fml.earlydisplay.theme.*;
import org.jetbrains.annotations.Nullable;

public record Texture(UncompressedImage textureData, String debugName,
                      int physicalWidth, int physicalHeight,
        TextureScaling scaling,
        @Nullable AnimationMetadata animationMetadata) implements AutoCloseable {
    public int width() {
        return scaling.width();
    }

    public int height() {
        return scaling.height();
    }

    public static Texture create(ThemeTexture themeTexture, @Nullable Path externalThemeDirectory) {
        return create(themeTexture.resource().loadAsImage(externalThemeDirectory), "EarlyDisplay " + themeTexture, themeTexture.scaling(), themeTexture.animation());
    }

    public static Texture create(
            UncompressedImage image,
            String debugName,
            TextureScaling scaling,
            @Nullable AnimationMetadata animation) {
        return new Texture(image, debugName, image.width(), image.height(), scaling, animation);
    }
    
    @Override
    public void close() {
        textureData.close();
    }
}
