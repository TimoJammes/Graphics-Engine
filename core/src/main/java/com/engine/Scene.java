package com.engine;

import com.badlogic.gdx.graphics.Color;

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
                boolean updated = b.update(entity, dt);
                isUpdate |= updated;
            }
        }
        return isUpdate;
    }
    void addEntity(Entity e) {
        entities.add(e);
    }

//    Entity addEntityFromObj(String path) {
//        ObjLoader.Result res;
//        try {
//            res = ObjLoader.load(path);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        Mesh mesh1 = new Mesh(res.vertices, res.indices);
//
//        Entity entity = new Entity(mesh1, res.color);
//        addEntity(entity);
//
//        return entity;
//    }

}

class TeapotScene extends Scene {
    TeapotScene() {

        backgroundColor = Color.BLUE;

        Entity teapot = new Entity("teapot.obj");

        teapot.setPos(0, 0, -10);
//        teapot.setScale(.1f, .1f, .1f);

        teapot.rotateWorld(-Math.PI/2, 1, 0, 0);
//        teapot.color = new Color(0.5f, 0.5f, 0.5f, 1);
        teapot.color = Color.valueOf("#97f8ff");
//        teapot.color = Color.RED;

        renderOptions.put(teapot, new RenderOptions(RenderMode.SOLID, true, false));

        entities.add(teapot);
    }


}
class AnimalScene extends Scene {

    AnimalScene() {

        backgroundColor = Color.BLUE;

        Entity beaver1 = new Entity("OBJ-animals/animal-beaver.obj");
        Entity beaver2 = new Entity("OBJ-animals/animal-beaver.obj");
        Entity beaver3 = new Entity("OBJ-animals/animal-beaver.obj");
        Entity beaver4 = new Entity("OBJ-animals/animal-beaver.obj");

        beaver1.color = Color.BROWN;
        beaver2.color = Color.BROWN;
        beaver3.color = Color.BROWN;
        beaver4.color = Color.BROWN;

        beaver1.setPos(0, 0, -2);
        beaver2.setPos(0, 1, 2);
        beaver3.setPos(2, 0, 0);
        beaver4.setPos(-2, 0, 0);

        beaver2.rotateWorld(Math.PI, 0, 1, 0);
        beaver3.rotateWorld(-Math.PI/2, 0, 1, 0);
        beaver4.rotateWorld(Math.PI/2, 0, 1, 0);

        beaver1.behaviors.add(new RotateBehavior(Math.PI, new float[]{0, 1, 0}));
        beaver2.behaviors.add(new OscillateBehavior(1, new float[]{0, 1, 0}, Matrix.getSlice(beaver2.transform.position, 0, 3)));

        renderOptions.put(beaver1, new RenderOptions(RenderMode.SOLID, false, true));
        renderOptions.put(beaver2, new RenderOptions(RenderMode.SOLID, true, true));
        renderOptions.put(beaver3, new RenderOptions(RenderMode.SOLID, true, false));
        renderOptions.put(beaver4, new RenderOptions(RenderMode.WIRE_FRAME, false, false));

        Entity ground = new GroundEntity(Color.GREEN, 10, 20);

        float scale = 2f;
        ground.setScale(scale, scale, scale);
        entities.add(ground);
        entities.add(beaver1);
        entities.add(beaver2);
        entities.add(beaver3);
        entities.add(beaver4);

    }

}
class GroundScene extends Scene {

    GroundScene() {

        backgroundColor = Color.BLUE;


        Entity ground = new GroundEntity(Color.GREEN, 10, 10);

        ground.setPos(0, -.5f, 0);
        float scale = 1f;
        ground.setScale(scale, scale, scale);

        addEntity(ground);

        renderOptions.put(ground, new RenderOptions(RenderMode.WIRE_FRAME, false, true));

    }
}

class BenchMarkScene extends Scene {
    BenchMarkScene() {
        backgroundColor = Color.BLUE;

        int numX = 1;
        int numY = numX;

        for(int x = -numX/2; x < Math.max(1, numX/2); x++) {
            for(int y = -numY/2; y < Math.max(1, numY/2); y++) {
                Entity beaver = new Entity("OBJ-animals/animal-beaver.obj");
                beaver.color = Color.BROWN;
                beaver.setPos(x+.5f, y+.5f, -4);
                beaver.behaviors.add(new RotateBehavior(Math.PI/4, new float[]{0, 1, 0}));
//                beaver.behaviors.add(new OscillateBehavior(1, new float[]{0, 1, 0}, Matrix.getSlice(beaver.transform.position, 0, 3)));
                renderOptions.put(beaver, new RenderOptions(RenderMode.SOLID, true, true));
                entities.add(beaver);
            }
        }
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
