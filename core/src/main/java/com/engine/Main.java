package com.engine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
//import com.badlogic.gdx.Input;
//import com.badlogic.gdx.assets.loaders.BitmapFontLoader;
//import com.badlogic.gdx.graphics.Color;

//import com.badlogic.gdx.graphics.g2d.SpriteBatch;

//import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
//import com.badlogic.gdx.utils.ScreenUtils;

//import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
//import java.util.Arrays;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter implements EngineListener {
//    private SpriteBatch batch;

    enum Event {DRAW}

    public static final int SCREEN_WIDTH = 1000;
    public static final int SCREEN_HEIGHT = 800;

    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private SpriteBatch batch;

    private final Queue<Event> eventQueue = new ArrayDeque<>();

    private Engine engine;

    private boolean needsRedraw;
    private int redrawCount;

    @Override
    public void create() {
//        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        batch = new SpriteBatch();

        engine = new Engine(shapeRenderer);

        engine.setListener(this);

//        engine.setScene(new GroundScene(1000, 1000));
        engine.setScene(new MultiScene());

//        engine.camera.transform.position = new float[]{43, 1990, -680};

        eventQueue.add(Event.DRAW);
    }

    @Override
    public void render() {


        if (Gdx.input.isKeyJustPressed(Input.Keys.L))
            engine.scene.lightingType = (engine.scene.lightingType == LightingType.FLAT) ? LightingType.GOURAUD : LightingType.FLAT;
        float dt = Gdx.graphics.getDeltaTime();
        engine.update(dt);

        Event event = eventQueue.poll();
        if (event != null) {
            if (event == Event.DRAW) {
                needsRedraw = true; //because of libGdx frame buffer drawing system
                redrawCount = 0;
            }
        }

        if (needsRedraw) {
            draw();
            redrawCount++;
            if (redrawCount == 2) {
                redrawCount = 0;
                needsRedraw = false;
            }
        }
    }

//    void update(float dt) {}

    void draw() {
//        ScreenUtils.clear(Color.BLACK);
        engine.renderScene();
        batch.begin();
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 10, Main.SCREEN_HEIGHT - 10);
//        font.draw(batch, "FPS: " + Math.round(1/dt), 10, Gdx.graphics.getHeight() - 10);
        batch.end();
    }

    @Override
    public void onMovement() {
        eventQueue.add(Event.DRAW);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
