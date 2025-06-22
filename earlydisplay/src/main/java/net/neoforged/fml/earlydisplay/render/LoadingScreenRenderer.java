/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.neoforged.fml.earlydisplay.render.elements.ImageElement;
import net.neoforged.fml.earlydisplay.render.elements.LabelElement;
import net.neoforged.fml.earlydisplay.render.elements.MojangLogoElement;
import net.neoforged.fml.earlydisplay.render.elements.PerformanceElement;
import net.neoforged.fml.earlydisplay.render.elements.ProgressBarsElement;
import net.neoforged.fml.earlydisplay.render.elements.RenderElement;
import net.neoforged.fml.earlydisplay.render.elements.StartupLogElement;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.util.IntSize;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadingScreenRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadingScreenRenderer.class);
    public static final int LAYOUT_WIDTH = 854;
    public static final int LAYOUT_HEIGHT = 480;

    private final APIWindow apiWindow;
    private final MaterializedTheme theme;
    private final String mcVersion;
    private final String neoForgeVersion;

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer

    private int animationFrame;
    private long nextFrameTime = 0;

    private final Semaphore renderLock = new Semaphore(1);

    // Scheduled background rendering of the loading screen
    private final ScheduledFuture<?> automaticRendering;

    private final List<RenderElement> elements;

    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder("shared", 8192);

    /**
     * Render initialization methods called by the Render Thread.
     * It compiles the fragment and vertex shaders for rendering text with STB, and sets up basic render framework.
     * <p>
     * Nothing fancy, we just want to draw and render text.
     */
    public LoadingScreenRenderer(ScheduledExecutorService scheduler,
            APIWindow apiWindow,
            Theme theme,
            @Nullable Path externalThemeDirectory,
            String mcVersion,
            String neoForgeVersion) {
        this.apiWindow = apiWindow;
        this.mcVersion = mcVersion;
        this.neoForgeVersion = neoForgeVersion;

        // Create GL resources
        this.theme = MaterializedTheme.materialize(theme, externalThemeDirectory);
        this.elements = loadElements();

        this.automaticRendering = scheduler.scheduleWithFixedDelay(this::renderToScreen, 50, 50, TimeUnit.MILLISECONDS);
        // schedule a 50 ms ticker to try and smooth out the rendering
        scheduler.scheduleWithFixedDelay(() -> animationFrame++, 1, 50, TimeUnit.MILLISECONDS);
    }

    private List<RenderElement> loadElements() {
        var elements = new ArrayList<RenderElement>();

        var loadingScreen = theme.theme().loadingScreen();
        if (loadingScreen.performance().visible()) {
            elements.add(new PerformanceElement(loadingScreen.performance(), theme));
        }
        if (loadingScreen.startupLog().visible()) {
            elements.add(new StartupLogElement(loadingScreen.startupLog(), theme));
        }
        if (loadingScreen.progressBars().visible()) {
            elements.add(new ProgressBarsElement(loadingScreen.progressBars(), theme));
        }
        if (loadingScreen.mojangLogo().visible()) {
            elements.add(new MojangLogoElement(loadingScreen.mojangLogo(), theme));
        }

        // Add decorative elements
        for (var entry : loadingScreen.decoration().entrySet()) {
            var element = entry.getValue();
            if (!element.visible()) {
                continue; // Likely reconfigured in an extended theme
            }
            elements.add(loadElement(entry.getKey(), element));
        }

        return elements;
    }

    private RenderElement loadElement(String id, ThemeElement element) {
        var renderElement = switch (element) {
            case ThemeImageElement imageElement -> new ImageElement(imageElement, theme);

            case ThemeLabelElement labelElement -> new LabelElement(
                    labelElement,
                    theme,
                    Map.of(
                            "version", mcVersion + "-" + neoForgeVersion.split("-")[0]));

            default -> throw new IllegalStateException("Unexpected theme element " + element + " of type " + element.getClass());
        };
        renderElement.setId(id);
        return renderElement;
    }

    public void stopAutomaticRendering() throws TimeoutException, InterruptedException {
        this.automaticRendering.cancel(false);
        if (!renderLock.tryAcquire(5, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        // we don't want the lock, just making sure it's back on the main thread
        renderLock.release();
    }

    /**
     * The main render loop.
     * renderThread executes this.
     * <p>
     * Performs initialization and then ticks the screen at 20 fps.
     * When the thread is killed, context is destroyed.
     */
    public void renderToScreen() {
        if (!renderLock.tryAcquire()) {
            return;
        }
        try {
            long nt;
            if ((nt = System.nanoTime()) < nextFrameTime) {
                return;
            }
            nextFrameTime = nt + MINFRAMETIME;
            apiWindow.doDraw(this::renderToFramebuffer, automaticRendering != null);
        } catch (Throwable t) {
            LOGGER.error("Unexpected error while rendering the loading screen", t);
        } finally {
            renderLock.release();
        }
    }

    public void renderToFramebuffer(APIRenderer renderer) {
        renderer.pushDebugGroup("update EarlyDisplay framebuffer");

        final var framebufferSize = renderer.framebufferSize();

        // Fit the layout rectangle into the screen while maintaining aspect ratio
        var desiredAspectRatio = LAYOUT_WIDTH / (float) LAYOUT_HEIGHT;
        var actualAspectRatio = framebufferSize.width() / (float) framebufferSize.height();
        if (actualAspectRatio > desiredAspectRatio) {
            // This means we are wider than the desired aspect ratio, and have to center horizontally
            var actualWidth = desiredAspectRatio * framebufferSize.height();
            renderer.setViewport((int) (framebufferSize.width() - actualWidth) / 2, 0, (int) actualWidth, framebufferSize.height());
        } else {
            // This means we are taller than the desired aspect ratio, and have to center vertically
            var actualHeight = framebufferSize.width() / desiredAspectRatio;
            renderer.setViewport(0, (int) (framebufferSize.height() - actualHeight) / 2, framebufferSize.width(), (int) actualHeight);
        }

        renderer.beginDrawing(theme);

        renderer.setLayoutSize(new IntSize(LAYOUT_WIDTH, LAYOUT_HEIGHT));

        var context = new RenderContext(renderer, buffer, theme, LAYOUT_WIDTH, LAYOUT_HEIGHT, animationFrame);

        for (var element : this.elements) {
            element.render(context);
        }

        renderer.endDrawing();
        renderer.popDebugGroup();
    }

    @Override
    public void close() {
        for (var element : elements) {
            element.close();
        }
        buffer.close();
    }
}
