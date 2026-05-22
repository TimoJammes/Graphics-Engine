package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.nio.ByteBuffer;

public class ScreenRenderer {
    private final Pixmap pixmap;
    private final Texture texture;
    private final SpriteBatch batch;
    private final ByteBuffer buffer;
    private final int width, height;

    public ScreenRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);
        batch = new SpriteBatch();
        buffer = pixmap.getPixels();
    }

    public void setPixel(int x, int y, byte r, byte g, byte b) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        int idx = (y * width + x) * 4;
        buffer.put(idx,     r);
        buffer.put(idx + 1, g);
        buffer.put(idx + 2, b);
        buffer.put(idx + 3, (byte) 255);
    }

    public void clear(byte r, byte g, byte b) {
        for (int i = 0; i < width * height * 4; i += 4) {
            buffer.put(i,     (byte) r);
            buffer.put(i + 1, (byte) g);
            buffer.put(i + 2, (byte) b);
            buffer.put(i + 3, (byte) 255);
        }
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
