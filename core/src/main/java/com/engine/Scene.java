package com.engine;

import com.badlogic.gdx.graphics.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Scene {

    enum RenderMode {WIRE_FRAME, SOLID}

    static final RenderMode defaultRenderMode = RenderMode.SOLID;
    static final boolean defaultRandomizeTexture = false;
    static final boolean defaultShowWireFrame = true;

    final List<Entity> entities = new ArrayList<>();

//    final Map<Entity, Boolean> showWireFrame = new HashMap<>();

    final Map<Entity, RenderOptions> renderOptions = new HashMap<>();

    Color backgroundColor;

    boolean update(float dt) {

        boolean isUpdate = false;
        for(Entity entity : entities) {
            for(Behavior b: entity.behaviors) {
                b.update(entity, dt);
                isUpdate = true;
            }
        }
        return isUpdate;
    }
    void addEntity(Entity e) {
        entities.add(e);
    }

    Entity addEntityFromObj(String path) {
        ObjLoader.Result res;
        try {
            res = ObjLoader.load(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Mesh mesh1 = new Mesh(res.vertices, res.indices);

        Entity entity = new Entity(mesh1, res.color);
        addEntity(entity);

        return entity;
    }

}

class TeapotScene extends Scene {
    TeapotScene() {

        backgroundColor = Color.BLUE;

        Entity teapot = addEntityFromObj("teapot.obj");

        teapot.setPos(0, 0, -10);
//        teapot.setScale(.1f, .1f, .1f);

        teapot.rotateWorld(-Math.PI/2, 1, 0, 0);
//        teapot.color = new Color(0.5f, 0.5f, 0.5f, 1);
        teapot.color = Color.valueOf("#97f8ff");
//        teapot.color = Color.RED;

        renderOptions.put(teapot, new RenderOptions(RenderMode.SOLID, true, false));
    }


}
class AnimalScene extends Scene {

    AnimalScene() {

        backgroundColor = Color.BLUE;

        Entity beaver1 = addEntityFromObj("OBJ-animals/animal-beaver.obj");
        beaver1.color = Color.BROWN;
        Entity beaver2 = addEntityFromObj("OBJ-animals/animal-beaver.obj");
        beaver2.color = Color.BROWN;
        Entity beaver3 = addEntityFromObj("OBJ-animals/animal-beaver.obj");
        beaver3.color = Color.BROWN;
        Entity beaver4 = addEntityFromObj("OBJ-animals/animal-beaver.obj");
        beaver4.color = Color.BROWN;

        beaver1.setPos(0, 0, -2);
        beaver2.setPos(0, 0, 2);
        beaver3.setPos(2, 0, 0);
        beaver4.setPos(-2, 0, 0);

        beaver2.rotateWorld(Math.PI, 0, 1, 0);
        beaver3.rotateWorld(-Math.PI/2, 0, 1, 0);
        beaver4.rotateWorld(Math.PI/2, 0, 1, 0);

        beaver1.behaviors.add(new RotateBehavior(Math.PI, new Vector3(0, 1, 0)));
        beaver1.behaviors.add(new OscillateBehavior(1, new Vector3(0, 1, 0), (Vector3)beaver1.transform.position.slice(0, 3)));

        renderOptions.put(beaver1, new RenderOptions(RenderMode.SOLID, false, true));
        renderOptions.put(beaver2, new RenderOptions(RenderMode.SOLID, true, true));
        renderOptions.put(beaver3, new RenderOptions(RenderMode.SOLID, true, false));
        renderOptions.put(beaver4, new RenderOptions(RenderMode.WIRE_FRAME, false, false));
    }

}
class GroundScene extends Scene {

    GroundScene() {

        backgroundColor = Color.BLUE;

        int x = 10;
        int y = x;
        float[] vertices = new float[x*y*3];

        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                vertices[(i*y+j)*3] = i - x/2f;
                vertices[(i*y+j)*3+2] = j - y/2f;
            }
        }

        int[] indices = new int[(x-1)*(y-1)*6];
        int idx = 0;
        for (int i = 0; i < x-1; i++) {
            for (int j = 0; j < y-1; j++) {
                int tl = i*y + j;
                int tr = i*y + j+1;
                int bl = (i+1)*y + j;
                int br = (i+1)*y + j+1;

                indices[idx++] = tl;
                indices[idx++] = bl;
                indices[idx++] = br;

                indices[idx++] = tl;
                indices[idx++] = br;
                indices[idx++] = tr;
            }
        }


        Mesh mesh = new Mesh(vertices, indices);

        Entity ground = new Entity(mesh, Color.GREEN);

        ground.setPos(0, -.5f, 0);
        float scale = 1f;
        ground.setScale(scale, scale, scale);

        addEntity(ground);

        renderOptions.put(ground, new RenderOptions(RenderMode.SOLID, true, true));

    }
}

class RenderOptions {
    Scene.RenderMode renderMode;
    boolean randomizeTexture;
    boolean showWireFrame;

    RenderOptions() {
        renderMode = Scene.defaultRenderMode;
        randomizeTexture = Scene.defaultRandomizeTexture;
        showWireFrame = Scene.defaultShowWireFrame;
    }

    RenderOptions(Scene.RenderMode renderMode, boolean randomizeTexture, boolean showWireFrame) {
        this.renderMode = renderMode;
        this.randomizeTexture = randomizeTexture;
        this.showWireFrame = showWireFrame;
    }
}
