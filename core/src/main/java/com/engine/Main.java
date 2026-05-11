package com.engine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.loaders.BitmapFontLoader;
import com.badlogic.gdx.graphics.Color;

//import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

import java.io.IOException;
import java.util.Arrays;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter implements EngineListener {
//    private SpriteBatch batch;

    public static final int SCREEN_WIDTH = 1000;
    public static final int SCREEN_HEIGHT = 800;

    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private SpriteBatch batch;

    private Engine engine;

    @Override
    public void create() {
//        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        batch = new SpriteBatch();

        engine = new Engine(shapeRenderer);

        engine.setListener(this);

        ObjLoader.Result res;
        try {
            res = ObjLoader.load("animal-beaver.obj");
//            res = ObjLoader.load("character-a.obj");
//            res = ObjLoader.load("teapot.obj");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        Vector4 p1 = new Vector4(0, 0, -2, 1);
//        Quaternion q1 = new Quaternion(0.707f, 0.707f, 0, 0);
        Quaternion q1 = new Quaternion();
//        Vector3 s1 = new Vector3(.5f, .5f, .5f);
        Vector3 s1 = new Vector3(1, 1, 1);
        Transform transform1 = new Transform(p1, q1, s1);

        float[] v1 = res.vertices;

        int[] i1 = res.indices;
        Mesh mesh1 = new Mesh(v1, i1, 3);

        Entity e1 = new Entity(transform1, mesh1, Color.GREEN);


        Vector4 camPos = new Vector4(0, 0, 0, 1);
        Quaternion camQ = new Quaternion();
        engine.camera = new Camera(80, 0.01f, 1000, (float)Main.SCREEN_WIDTH/Main.SCREEN_HEIGHT, camPos, camQ);

        engine.addEntity(e1);

//        engine.loadSimpleExample();


        onCameraMove();
    }

    @Override
    public void render() {

        float dt = Gdx.graphics.getDeltaTime();
        update(dt);
        draw();
//        engine.renderEntities(Engine.renderType.SOLID);
    }

    void update(float dt) {

        engine.moveCamera(dt);

    }

    void draw() {
        ScreenUtils.clear(Color.BLACK);
        engine.renderEntities(Engine.renderType.SOLID);

        batch.begin();
//        font.draw(batch, ""+Math.round(1/dt), 0, 0);
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 10, Gdx.graphics.getHeight() - 10);
        batch.end();

    }

    @Override
    public void onCameraMove() {
//        draw();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
