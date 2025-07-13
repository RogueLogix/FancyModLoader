/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.opengl;

import static net.neoforged.fml.earlydisplay.render.LoadingScreenRenderer.LAYOUT_HEIGHT;
import static net.neoforged.fml.earlydisplay.render.LoadingScreenRenderer.LAYOUT_WIDTH;
import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_CREATION_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_NATIVE_CONTEXT_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_ZERO;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL32C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_FLOAT;
import static org.lwjgl.opengl.GL32C.GL_LINEAR;
import static org.lwjgl.opengl.GL32C.GL_NEAREST;
import static org.lwjgl.opengl.GL32C.GL_RENDERER;
import static org.lwjgl.opengl.GL32C.GL_RGBA;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL32C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL32C.GL_TRUE;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL32C.GL_VENDOR;
import static org.lwjgl.opengl.GL32C.GL_VERSION;
import static org.lwjgl.opengl.GL32C.glBufferData;
import static org.lwjgl.opengl.GL32C.glDrawArrays;
import static org.lwjgl.opengl.GL32C.glDrawElements;
import static org.lwjgl.opengl.GL32C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL32C.glGenBuffers;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glGenVertexArrays;
import static org.lwjgl.opengl.GL32C.glGetString;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;
import static org.lwjgl.opengl.GL32C.glVertexAttribPointer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.fml.earlydisplay.render.APIRenderer;
import net.neoforged.fml.earlydisplay.render.APIWindow;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.util.IntSize;
import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlWindow implements APIWindow, APIRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("EARLYDISPLAY GlWindow");

    private final long window;
    private final EarlyFramebuffer framebuffer;

    private MaterializedTheme theme;

    private IntSize layoutSize = new IntSize(-1, -1);
    private Map<String, ElementShader> shaderCache = new HashMap<>();
    private Map<Texture, Integer> textureCache = new HashMap<>();

    private final int VAO;
    private final int vertexBuffer;
    private final int elementBuffer;

    public GlWindow(int width, int height, CharSequence title) {
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DEBUG_OPENGL)) {
            LOGGER.info("Requesting the creation of an OpenGL debug context");
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GL_TRUE);
        }
        window = glfwCreateWindow(width, height, title, 0L, 0L);
        if (this.window == 0L) {
            throw new IllegalStateException("Failed to create a window");
        }

        // This thread owns the GL render context now. We should make a note of that.
        glfwMakeContextCurrent(window);
        // Wait for one frame to be complete before swapping; enable vsync in other words.
        glfwSwapInterval(1);
        var capabilities = GL.createCapabilities();
        GlState.readFromOpenGL();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("GL info: {} GL version {}, {}", glGetString(GL_RENDERER), glGetString(GL_VERSION), glGetString(GL_VENDOR));

        // we always render to an 854x480 texture and then fit that to the screen
        framebuffer = new EarlyFramebuffer(LAYOUT_WIDTH, LAYOUT_HEIGHT);

        VAO = glGenVertexArrays();
        vertexBuffer = glGenBuffers();
        elementBuffer = glGenBuffers();
        {
            GlState.bindVertexArray(VAO);
            GlState.bindElementArrayBuffer(elementBuffer);
            GlState.bindArrayBuffer(vertexBuffer);
            final var format = SimpleBufferBuilder.Format.POS_TEX_COLOR;
            final var types = format.types();
            int offset = 0;
            for (int i = 0; i < types.length; i++) {
                glEnableVertexAttribArray(i);
                SimpleBufferBuilder.Element type = types[i];
                switch (type.glType) {
                    case GL_FLOAT -> glVertexAttribPointer(i, type.count, GL_FLOAT, false, format.stride, offset);
                    case GL_UNSIGNED_BYTE -> glVertexAttribPointer(i, type.count, GL_UNSIGNED_BYTE, true, format.stride, offset);
                    default -> throw new IllegalStateException("Unknown glType, I don't know how to bind this vertex element: " + type);
                }
                // add to the offset for the next element.
                offset += type.width;
            }
            GlState.bindElementArrayBuffer(0);
            GlState.bindArrayBuffer(0);
            GlState.bindVertexArray(0);
        }

        glfwMakeContextCurrent(0);
    }

    @Override
    public void close() {
        glfwMakeContextCurrent(window);
        // Set the title to what the game wants
        glfwSwapInterval(0);

        shaderCache.values().forEach(ElementShader::close);
        shaderCache.clear();
        textureCache.values().forEach(GL11C::glDeleteTextures);
        textureCache.clear();
    }

    @Override
    public long windowHandle() {
        return window;
    }

    @Override
    public void doDraw(Consumer<APIRenderer> drawFunc, boolean onBackgroundThread) {
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();

            GlState.readFromOpenGL();
            var backup = GlState.createSnapshot();

            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            framebuffer.resize(w[0], h[0]);

            drawFunc.accept(this);

            GlState.viewport(0, 0, w[0], h[0]);
            if (this.theme != null) {
                framebuffer.blitToScreen(this.theme.theme().colorScheme().screenBackground(), w[0], h[0]);
            }
            // Swap buffers; we're done
            glfwSwapBuffers(window);

            GlState.applySnapshot(backup);
        } finally {
            if (onBackgroundThread) {
                glfwMakeContextCurrent(0); // we release the gl context IF we're running off of the main thread
            }
        }
    }

    public void beginDrawing(MaterializedTheme theme) {
        this.theme = theme;
        var background = theme.theme().colorScheme().screenBackground();
        framebuffer.activate();
        GlState.clearColor(background.r(), background.g(), background.b(), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
    }

    @Override
    public void endDrawing() {
        framebuffer.deactivate();
    }

    @Override
    public void pushDebugGroup(String name) {
        GlDebug.pushGroup(name);
    }

    @Override
    public void popDebugGroup() {
        GlDebug.popGroup();
    }

    @Override
    public IntSize framebufferSize() {
        return new IntSize(framebuffer.width(), framebuffer.height());
    }

    @Override
    public void setLayoutSize(IntSize layoutSize) {
        if (this.layoutSize.equals(layoutSize)) {
            return;
        }
        this.layoutSize = layoutSize;
        shaderCache.values().forEach(ElementShader::close);
        shaderCache.clear();
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        GlState.viewport(x, y, width, height);
    }

    @Override
    public void setScissor(boolean enabled, int x, int y, int width, int height) {
        if (!enabled) {
            GlState.scissorTest(false);
            return;
        }
        GlState.scissorTest(true);
        GlState.scissorBox(x, y, width, height);
    }

    @Override
    public void draw(SimpleBufferBuilder builder, String shader, Texture texture) {
        if (builder.format() != SimpleBufferBuilder.Format.POS_TEX_COLOR) {
            throw new IllegalArgumentException("Only POS_TEX_COLOR buffer builders supported");
        }

        builder.finish();

        if (builder.indices() == 0) {
            builder.reset();
            return;
        }

        GlState.bindArrayBuffer(vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, builder.vertexBuffer(), GL_DYNAMIC_DRAW);

        final var elementShader = shaderCache.computeIfAbsent(shader, this::compileShader);
        elementShader.activate();
        elementShader.setUniform1i(ElementShader.UNIFORM_SAMPLER0, 0);
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(textureCache.computeIfAbsent(texture, this::uploadTexture));
        GlState.bindVertexArray(VAO);
        if (builder.mode() == SimpleBufferBuilder.Mode.QUADS) {
            GlState.bindElementArrayBuffer(elementBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, builder.quadsElementBuffer(), GL_DYNAMIC_DRAW);
        }
        if (builder.mode() == SimpleBufferBuilder.Mode.QUADS) {
            glDrawElements(GL_TRIANGLES, builder.indices(), GL_UNSIGNED_INT, 0);
        } else {
            glDrawArrays(GL_TRIANGLES, 0, builder.indices());
        }
        GlState.bindVertexArray(0);
        GlState.bindTexture2D(0);
        GlState.bindElementArrayBuffer(0);
        GlState.bindArrayBuffer(0);
        elementShader.clear();

        builder.reset();
    }

    private ElementShader compileShader(String shader) {
        final var themeShader = theme.getShader(shader);
        return ElementShader.create(
                shader,
                layoutSize,
                themeShader.vertexShader(),
                themeShader.fragmentShader(),
                theme.externalThemeDirectory());
    }

    private int uploadTexture(Texture texture) {
        final var image = texture.textureData();
        var texId = glGenTextures();
        GlState.activeTexture(GL_TEXTURE0);
        GlState.bindTexture2D(texId);
        GlDebug.labelTexture(texId, texture.debugName());
        boolean linear = texture.scaling().linearScaling();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, image.rgba() ? GL_RGBA : GL_RED, image.width(), image.height(), 0, image.rgba() ? GL_RGBA : GL_RED, GL_UNSIGNED_BYTE, image.imageData());
        GlState.activeTexture(GL_TEXTURE0);
        return texId;
    }
}
