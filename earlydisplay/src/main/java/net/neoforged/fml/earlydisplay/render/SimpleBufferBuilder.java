/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

/**
 * A very simple, Mojang inspired BufferBuilder.
 * <em>This has been customized for 2d rendering such as text and simple planar textures</em>
 * <p>
 * Not bound to any specific format, ideally should be held onto for re-use.
 * <p>
 * upload to external vertex arrays for proper instancing using {@link #finish()} and related buffer fetch functions.
 * <p>
 * This is a Triangles only buffer, all data uploaded is in Triangles.
 * Quads are converted to triangles using {@code 0, 1, 2, 0, 2, 3}.
 * <p>
 * Any given {@link Format} should have its individual {@link Element} components
 * buffered in the order specified by the {@link Format},
 * followed by an {@link #endVertex()} call to prepare for the next vertex.
 * <p>
 * It is illegal to buffer primitives in any format other than the one specified to
 * {@link #begin(Format, Mode)}.
 *
 * @author covers1624
 */
public class SimpleBufferBuilder implements Closeable {
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    private final String label;
    private long bufferAddr;   // Pointer to the backing buffer.
    private ByteBuffer buffer; // ByteBuffer view of the backing buffer.

    private static final int ELEMENT_BYTES_PER_QUAD = 6 * 4;
    private ByteBuffer quadsElementBuffer;

    private Format format;     // The current format we are buffering.
    private Mode mode;         // The current mode we are buffering.
    private boolean building;  // If we are building the buffer.
    private int elementIndex;  // The current element index we are buffering. if elementIndex == format.types.length, we expect 'endVertex'
    private int index;         // The current index into the buffer we are writing to.
    private int vertices;      // The number of complete vertices we have buffered.

    /**
     * Create a new SimpleBufferBuilder with an initial capacity.
     * <p>
     * The buffer will be doubled as required.
     * <p>
     * Generally picking a small number, around 128/256 should be a
     * safe bet. Provided you cache your buffers, it should not mean much overall.
     *
     * @param capacity The initial capacity in bytes.
     */
    public SimpleBufferBuilder(String label, int capacity) {
        this.label = label;
        bufferAddr = ALLOCATOR.malloc(capacity);
        buffer = MemoryUtil.memByteBuffer(bufferAddr, capacity);
        quadsElementBuffer = BufferUtils.createByteBuffer(0);
    }

    private void ensureElementBufferLength(int vertices) {
        if (quadsElementBuffer.limit() >= vertices * ELEMENT_BYTES_PER_QUAD) {
            return;
        }

        var newElementBufferVertexLength = Math.max(1024, quadsElementBuffer.limit() / ELEMENT_BYTES_PER_QUAD);
        while (newElementBufferVertexLength < vertices) {
            newElementBufferVertexLength *= 2;
        }

        final var newIndexCount = newElementBufferVertexLength + newElementBufferVertexLength / 2;

        quadsElementBuffer = BufferUtils.createByteBuffer(newIndexCount * 4);

        final int quads = newElementBufferVertexLength / 4;
        // generate indices for the extension to the buffer
        for (int i = 0; i < quads; i++) {
            // Quads are a bit different, we need to emit 2 triangles such that
            // when combined they make up a single quad.
            quadsElementBuffer.putInt(i * 4 + 0).putInt(i * 4 + 1).putInt(i * 4 + 2);
            quadsElementBuffer.putInt(i * 4 + 1).putInt(i * 4 + 3).putInt(i * 4 + 2);
        }
        
        quadsElementBuffer.flip();
    }

    /**
     * Start building a new set of vertex data in the
     * given format and mode.
     *
     * @param format The format to start building in.
     * @param mode   The mode to start building in.
     */
    Exception lastBegan;
    public SimpleBufferBuilder begin(Format format, Mode mode) {
        if (bufferAddr == MemoryUtil.NULL) {
            throw new IllegalStateException("Buffer has been freed."); // You already free'd the buffer
        }
        if (building) {
            throw new IllegalStateException("Already building."); // Your already building verticies.
        }
        if (vertices != 0) {
            throw new IllegalStateException("Not reset."); // You didn't draw or reset the buffer
        }
        this.format = format;
        this.mode = mode;
        building = true;
        lastBegan = new Exception();
        elementIndex = 0;
        ensureSpace(format.stride);
        // Rewind ready for new data.
        buffer.rewind();
        buffer.limit(buffer.capacity());
        return this;
    }

    /**
     * Buffer a position element.
     *
     * @param x The x.
     * @param y The y.
     * @return The same builder.
     */
    public SimpleBufferBuilder pos(float x, float y) {
        if (!building) throw new IllegalStateException("Not building."); // You did not call begin.

        if (elementIndex == format.types.length) throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        if (format.types[elementIndex] != Element.POS) throw new IllegalArgumentException("Expected " + format.types[elementIndex]); // You called the wrong method for the format order.

        // Assumes that our POS element specifies the FLOAT data type.
        buffer.putFloat(index + 0, x);
        buffer.putFloat(index + 4, y);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.types[elementIndex].width;
        elementIndex++;
        return this;
    }

    /**
     * Buffer a texture element.
     *
     * @param u The u.
     * @param v The v.
     * @return The same builder.
     */
    public SimpleBufferBuilder tex(float u, float v) {
        if (!building) throw new IllegalStateException("Not building."); // You did not call begin.

        if (elementIndex == format.types.length) throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        if (format.types[elementIndex] != Element.TEX) throw new IllegalArgumentException("Expected " + format.types[elementIndex]); // You called the wrong method for the format order.

        // Assumes our TEX element specifies the FLOAT data type.
        buffer.putFloat(index + 0, u);
        buffer.putFloat(index + 4, v);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.types[elementIndex].width;
        elementIndex++;
        return this;
    }

    /**
     * Buffer a color element.
     *
     * @param r The red component. (0-1)
     * @param g The green component. (0-1)
     * @param b The blue component. (0-1)
     * @param a The alpha component. (0-1)
     * @return The same buffer.
     */
    public SimpleBufferBuilder colour(float r, float g, float b, float a) {
        // Expand floats to 0-255 and forward.
        return colour((byte) (r * 255F), (byte) (g * 255F), (byte) (b * 255F), (byte) (a * 255F));
    }

    /**
     * @param packedColor an ARGB packed int
     * @return the same buffer.
     * @see ThemeColor#toArgb()
     */
    public SimpleBufferBuilder colour(int packedColor) {
        var color = ThemeColor.ofArgb(packedColor);
        return colour(color.r(), color.g(), color.b(), color.a());
    }

    /**
     * Buffer a color element.
     *
     * @param r The red component. (0-255)
     * @param g The green component. (0-255)
     * @param b The blue component. (0-255)
     * @param a The alpha component. (0-255)
     * @return The same buffer.
     */
    public SimpleBufferBuilder colour(byte r, byte g, byte b, byte a) {
        if (!building) throw new IllegalStateException("Not building."); // You did not call begin.

        if (elementIndex == format.types.length) throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        if (format.types[elementIndex] != Element.COLOR) throw new IllegalArgumentException("Expected " + format.types[elementIndex]); // You called the wrong method for the format order.

        // Assumes our COLOR element specifies the UNSIGNED_BYTE data type.
        buffer.put(index + 0, r);
        buffer.put(index + 1, g);
        buffer.put(index + 2, b);
        buffer.put(index + 3, a);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.types[elementIndex].width;
        elementIndex++;
        return this;
    }

    /**
     * End building the current vertex and prepare for the next.
     *
     * @return The same builder.
     */
    public SimpleBufferBuilder endVertex() {
        if (!building) throw new IllegalStateException("Not building."); // You did not call begin.

        if (elementIndex != format.types.length) throw new IllegalStateException("Expected " + format.types[elementIndex]); // You did not finish building the vertex.

        // Reset elementIndex
        elementIndex = 0;
        // Increment the number of vertices we have so far buffered.
        vertices++;
        // Make sure there is space for the next vertex.
        ensureSpace(format.stride);
        return this;
    }

    // Checks there is enough space in the buffer for specified number of bytes.
    // If there is not enough space, the buffer is increased by 50%.
    private void ensureSpace(int newBytes) {
        int cap = buffer.capacity();
        if (index + newBytes > cap) {
            int newCap = Math.max(3 * cap / 2, 3 * newBytes / 2);
            bufferAddr = ALLOCATOR.realloc(bufferAddr, newCap);
            buffer = MemoryUtil.memByteBuffer(bufferAddr, newCap);
            buffer.rewind();
        }
    }
    
    public void finish() {
        if (!building) throw new IllegalStateException("Not building.");
        building = false;

        if (elementIndex == format.types.length) throw new IllegalStateException("Expected endVertex"); // You didn't finish building your vertex.
        if (elementIndex != 0) throw new IllegalStateException("Not finished building vertex, Expected: " + format.types[elementIndex]); // You didn't finish building your vertex data.
        if (vertices == 0) return; // No vertices buffered, lets not do anything.
        if (vertices % mode.vertices != 0) throw new IllegalStateException("Does not contain vertices aligned to " + mode); // You did not put in enough vertices to cleanly slice the data into TRIANGLES/QUADS

        buffer.position(0);
        buffer.limit(index);
        
        if (mode == Mode.QUADS) {
            ensureElementBufferLength(vertices);
        }
    }

    public Mode mode() {
        return mode;
    }

    public Format format() {
        return format;
    }

    public int indices() {
        return mode == Mode.TRIANGLES ? vertices : vertices + vertices / 2;
    }

    public ByteBuffer vertexBuffer() {
        return buffer;
    }

    public ByteBuffer quadsElementBuffer() {
        return quadsElementBuffer;
    }

    public void reset() {
        vertices = 0;
        index = 0;
    }

    /**
     * Clear this builder's cached buffer.
     */
    @Override
    public void close() {
        ALLOCATOR.free(bufferAddr);
        bufferAddr = MemoryUtil.NULL;
    }

    /**
     * Represents a primitive mode that this builder is capable of buffering in.
     */
    public enum Mode {
        TRIANGLES(3),
        QUADS(4),
        ;

        public final int vertices;

        Mode(int vertices) {
            this.vertices = vertices;
        }
    }

    /**
     * Specifies a vertex element with a specific data type, number of primitives and a size in bytes.
     */
    public enum Element {
        POS(GL_FLOAT, 2, 2 * 4),
        TEX(GL_FLOAT, 2, 2 * 4),
        COLOR(GL_UNSIGNED_BYTE, 4, 4);

        public final int glType;
        public final int count;
        public final int width;

        Element(int glType, int count, int width) {
            this.glType = glType;
            this.count = count;
            this.width = width;
        }
    }

    /**
     * Specifies a combination of vertex elements.
     */
    public enum Format {
        POS_TEX(Element.POS, Element.TEX),
        POS_COLOR(Element.POS, Element.COLOR),
        POS_TEX_COLOR(Element.POS, Element.TEX, Element.COLOR);

        private final Element[] types;
        public final int stride;

        Format(Element... types) {
            this.types = types;

            // Stride is the width of each vertex in bytes.
            stride = Arrays.stream(types).mapToInt(e -> e.width).sum();
        }

        public Element[] types() {
            return types.clone();
        }
    }
}
