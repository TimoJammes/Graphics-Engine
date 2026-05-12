package com.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

//import java.io.IOException;
//import java.util.ArrayList;

public class Engine {

    private EngineListener listener;

    private final Renderer renderer;

    protected Camera camera;

//     final ArrayList<Entity> entities = new  ArrayList<>();
    private Scene scene;

     Engine(ShapeRenderer shapeRenderer, Camera camera) {
        this.camera = camera;

        renderer = new Renderer(shapeRenderer);
    }
     Engine(ShapeRenderer shapeRenderer) {

         renderer = new Renderer(shapeRenderer);

         Vector4 camPos = new Vector4(0, 0, 0, 1);
         Quaternion camQ = new Quaternion();
         camera = new Camera(80, 0.01f, 1000, (float)Main.SCREEN_WIDTH/Main.SCREEN_HEIGHT, camPos, camQ);
    }

    void renderScene() {
         renderer.renderScene(scene, camera);
    }

    void update(float dt) {

         boolean cameraUpdated = camera.update(dt);
         boolean sceneUpdated = scene.update(dt);

         if (cameraUpdated || sceneUpdated)
             if (listener != null) listener.onMovement();

//         scene.update(dt);

    }

    public void setScene(Scene scene) {
         this.scene = scene;
    }

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

}
