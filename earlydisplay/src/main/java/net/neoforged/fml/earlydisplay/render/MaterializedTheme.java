/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import net.neoforged.fml.earlydisplay.theme.ThemeShader;
import net.neoforged.fml.earlydisplay.theme.ThemeSprites;
import org.jetbrains.annotations.Nullable;

/**
 * A themes resources loaded for rendering at runtime.
 */
public record MaterializedTheme(
        Theme theme,
        @Nullable Path externalThemeDirectory,
        Map<String, SimpleFont> fonts,
        Map<String, ThemeShader> shaders,
        MaterializedThemeSprites sprites) {
    public static MaterializedTheme materialize(Theme theme, @Nullable Path externalThemeDirectory) {
        return new MaterializedTheme(
                theme,
                externalThemeDirectory,
                loadFonts(theme.fonts(), externalThemeDirectory),
                Collections.unmodifiableMap(new HashMap<>(theme.shaders())),
                loadSprites(theme.sprites(), externalThemeDirectory));
    }

    private static Map<String, SimpleFont> loadFonts(Map<String, ThemeResource> themeFonts, @Nullable Path externalThemeDirectory) {
        var fonts = new HashMap<String, SimpleFont>(themeFonts.size());
        for (var entry : themeFonts.entrySet()) {
            try {
                fonts.put(entry.getKey(), new SimpleFont(entry.getValue(), externalThemeDirectory));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load font " + entry.getKey(), e);
            }
        }
        return fonts;
    }

    private static MaterializedThemeSprites loadSprites(ThemeSprites sprites, @Nullable Path externalThemeDirectory) {
        return new MaterializedThemeSprites(
                Texture.create(sprites.progressBarBackground(), externalThemeDirectory),
                Texture.create(sprites.progressBarForeground(), externalThemeDirectory),
                Texture.create(sprites.progressBarIndeterminate(), externalThemeDirectory));
    }

    public SimpleFont getFont(String fontId) {
        var font = fonts.getOrDefault(fontId, fonts.get(Theme.FONT_DEFAULT));
        if (font == null) {
            throw new IllegalStateException("Theme does not contain a default font. Available fonts: " + fonts.keySet());
        }
        return font;
    }

    public ThemeShader getShader(String shaderId) {
        var shader = shaders.get(shaderId);
        if (shader == null) {
            throw new IllegalArgumentException("Missing shader definition in theme for " + shaderId);
        }
        return shader;
    }
}
