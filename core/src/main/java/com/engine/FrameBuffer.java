package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FrameBuffer {
    private final Pixmap pixmap;
    private final Texture texture;
    private final SpriteBatch batch;
    private final ByteBuffer colorBuffer;
    private final int width, height;
    private final float[] depthBuffer;

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);
        batch = new SpriteBatch();
        colorBuffer = pixmap.getPixels();
        depthBuffer = new float[width * height];
    }

    float getDepth(int x, int y) {
        return depthBuffer[y * width + x];
    }

    void setDepth(int x, int y, float depth) {
        depthBuffer[y * width + x] = depth;
    }

    public void setPixel(int x, int y, byte r, byte g, byte b) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int idx = (y * width + x) * 4;
        colorBuffer.put(idx,     r);
        colorBuffer.put(idx + 1, g);
        colorBuffer.put(idx + 2, b);
        colorBuffer.put(idx + 3, (byte) 255);
    }

    public void setPixel(int x, int y, Color color) {
        byte r = (byte) (color.r * 255);
        byte g = (byte) (color.g * 255);
        byte b = (byte) (color.b * 255);
        setPixel(x, y, r, g, b);
    }

    public void clear(byte r, byte g, byte b) {
        for (int i = 0; i < width * height * 4; i += 4) {
            colorBuffer.put(i,     (byte) r);
            colorBuffer.put(i + 1, (byte) g);
            colorBuffer.put(i + 2, (byte) b);
            colorBuffer.put(i + 3, (byte) 255);
        }
        Arrays.fill(depthBuffer, 0);
    }

    public void clear(Color color) {
        clear((byte) (255*color.r), (byte) (255*color.g), (byte) (255*color.b));
    }
    public void clear() {
        clear((byte) 0, (byte) 0, (byte) 0);
    }

    public void present() {
        texture.draw(pixmap, 0, 0);  // CPU → GPU upload
        batch.begin();
        batch.draw(texture, 0, 0);
        batch.end();
    }

    public void dispose() {
        pixmap.dispose();
        texture.dispose();
        batch.dispose();
    }
}
