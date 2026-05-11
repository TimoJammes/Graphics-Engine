package com.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;

public class Engine {


    static enum renderType {WIRE_FRAME, SOLID}

    private EngineListener listener;

    private final Renderer renderer;

    protected Camera camera;

     final ArrayList<Entity> entities = new  ArrayList<>();



     Engine(ShapeRenderer shapeRenderer, Camera camera) {
        renderer = new Renderer(shapeRenderer);
        this.camera = camera;
    }
     Engine(ShapeRenderer shapeRenderer) {
        renderer = new Renderer(shapeRenderer);
    }

     void renderEntities(renderType type) {
         if (type == renderType.WIRE_FRAME)
            renderer.wireFrameRender(entities, camera);
         else if (type == renderType.SOLID)
             renderer.solidRender(entities, camera);
         else
             throw new IllegalArgumentException();
    }

    void moveCamera(float dt) {
        double theta = Math.PI / 256;
        float mag = .01f;

        float dx = 0, dy = 0, dz = 0;
        float rotX = 0, rotY = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))       dx -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))      dx += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.UP))         dz -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))       dz += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE))      dy += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) dy -= mag;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) rotX += theta;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) rotX -= theta;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) rotY += theta;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) rotY -= theta;

        camera.translateLocal(dx, 0, dz);
        camera.translate(0, dy, 0);

        if (rotX != 0) camera.rotate(rotX, new Vector3(1, 0, 0));
        if (rotY != 0) camera.rotate(rotY, new Vector3(0, 1, 0));


        if (Gdx.input.isKeyPressed(Input.Keys.R)) camera.resetTransform();

        if (rotX != 0 || rotY != 0 || dx  != 0 || dy != 0 || dz != 0) {
            if (listener != null) listener.onCameraMove();
        }
//        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
//            camera.translateLocal(-1*mag, 0, 0);
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
//            camera.translateLocal(1*mag, 0, 0);
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
//            camera.translateLocal(0, 0, -1*mag);
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
//            camera.translateLocal(0, 0, 1*mag);
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
//            camera.translate(0, 1*mag, 0);
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
//            camera.translate(0, -1*mag, 0);
//        }
//
//        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
////            camera.rotate(-theta, camera.transform.getRight());
//            camera.rotate(theta, new Vector3(1, 0, 0));
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
//            camera.rotate(-theta, new Vector3(1, 0, 0));
////            camera.rotate(theta, camera.transform.getRight());
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
//            camera.rotate(theta, new Vector3(0, 1, 0));
////            camera.rotate(theta, camera.transform.getUp());
//        }
//        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
//            camera.rotate(-theta, new Vector3(0, 1, 0));
////            camera.rotate(-theta, camera.transform.getUp());
//        }
//
//        if (Gdx.input.isKeyPressed(Input.Keys.R)) {
//            camera.resetTransform();
//        }
     }

    void loadSimpleExample() {
        Vector4 p1 = new Vector4(0, 0, -5, 1);
        Quaternion q1 = new Quaternion();
        Vector3 s1 = new Vector3(1, 1, 1);
        Transform transform1 = new Transform(p1, q1, s1);

        float[] v1 = new float[]{
            -1, -1, -1,
            1, -1, -1,
            1, 1, -1,
            -1, 1, -1,
            -1, -1, 1,
            1, -1, 1,
            1, 1, 1,
            -1, 1, 1,
        };
        int[] i1 = new int[]{
            0, 1, 2,
            0, 1, 3,
            0, 3, 2,
            4, 5, 6,
            4, 5, 7,
            4, 7, 6,
        };
        Mesh mesh1 = new Mesh(v1, i1, 3);

        Entity e1 = new Entity(transform1, mesh1, Color.GREEN);


        Vector4 camPos = new Vector4(0, 0, 0, 1);
        Quaternion camQ = new Quaternion();
        camera = new Camera(90, 0.1f, 1000, (float) Main.SCREEN_WIDTH/Main.SCREEN_HEIGHT, camPos, camQ);

        entities.add(e1);
    }

    void addEntity(Entity e) {
         entities.add(e);
    }

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

}
