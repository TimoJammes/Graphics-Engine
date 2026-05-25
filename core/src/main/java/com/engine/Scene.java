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
    static final boolean defaultShowWireFrame = false;

    final List<Entity> entities = new ArrayList<>();
    Light light;
//    final List<Light> lights = new ArrayList<>();

    final Map<Entity, RenderOptions> renderOptions = new HashMap<>();

    Color backgroundColor;

    RenderMode renderMode = RenderMode.SOLID;

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
}

class TeapotScene extends Scene {
    TeapotScene() {

        backgroundColor = Color.BLUE;

        Entity teapot = new Entity("teapot.obj");

        teapot.setPos(0, 0, 0);
//        teapot.setScale(.1f, .1f, .1f);

        teapot.rotateWorld(-Math.PI/2, 1, 0, 0);
//        teapot.color = new Color(0.5f, 0.5f, 0.5f, 1);
        teapot.color = Color.valueOf("#97f8ff");
        teapot.color = Color.PINK;
//        teapot.color = Color.RED;

        renderOptions.put(teapot, new RenderOptions(false, false));

        entities.add(teapot);


        Entity lightObj = new SphereEntity(.1f);
        lightObj.setPos(new float[]{4, 6, 0});
        lightObj.color = Color.WHITE;

        lightObj.behaviors.add(new CircleBehavior(Math.PI / 4, new float[]{0, 6, 0}, new float[]{0, 1, 0}));

        renderOptions.put(lightObj, new RenderOptions(false, false, true));

        Light light = new Light(Light.Type.POINT, lightObj, lightObj.color);

        entities.add(lightObj);

        this.light = light;
    }


}
class MultiScene extends Scene {

    MultiScene() {

        backgroundColor = Color.BLACK;

        Entity beaver1 = new Entity("OBJ-animals/animal-beaver.obj");
        beaver1.color = Color.BROWN;
        Entity beaver2 = new Entity(beaver1.mesh, Color.BROWN);
        beaver2.hasNormals = true;
        Entity beaver3 = new Entity(beaver1.mesh, Color.BROWN);
        beaver3.hasNormals = true;
        Entity beaver4 = new Entity(beaver1.mesh, Color.BROWN);
        beaver4.hasNormals = true;


        beaver1.setPos(0, 0, -2);
        beaver2.setPos(0, 1, 2);
        beaver3.setPos(2, 0, 0);
        beaver4.setPos(-2, 0, 0);

        beaver2.rotateWorld(Math.PI, 0, 1, 0);
        beaver3.rotateWorld(-Math.PI/2, 0, 1, 0);
        beaver4.rotateWorld(Math.PI/2, 0, 1, 0);

        beaver1.behaviors.add(new RotateBehavior(Math.PI, new float[]{0, 1, 0}));
        beaver2.behaviors.add(new OscillateBehavior(1, new float[]{0, 1, 0}, Matrix.getSlice(beaver2.transform.position, 0, 3)));

        renderOptions.put(beaver1, new RenderOptions(false, false));
        renderOptions.put(beaver2, new RenderOptions(true, false));
        renderOptions.put(beaver3, new RenderOptions(true, false));
        renderOptions.put(beaver4, new RenderOptions(false, false));

        Entity ground = new GroundEntity(Color.GREEN, 100, 100, 50, 50);

        renderOptions.put(ground, new RenderOptions(false, false));

        float scale = 2f;
        ground.setScale(scale);
        entities.add(ground);
        entities.add(beaver1);
        entities.add(beaver2);
        entities.add(beaver3);
        entities.add(beaver4);

//        Light light = new Light(Light.Type.POINT, new float[]{0, 5, 0}, Color.WHITE);
//
//        Entity lightObj = new SphereEntity(.1f);
//        lightObj.setPos(light.position);
//        lightObj.color = light.color;
//
//        renderOptions.put(lightObj, new RenderOptions(false, false, true));
//        entities.add(lightObj);
//
//        this.light = light;

    }

}

class AnimalScene extends Scene {

    AnimalScene(RenderMode mode) {
        backgroundColor = Color.BLUE;
        Entity animal = new Entity("OBJ-animals/animal-beaver.obj");
        animal.color = Color.BROWN;

        animal.setPos(0, 0, -3);
        animal.behaviors.add(new RotateBehavior(Math.PI, new float[]{0, 1, 0}));
        renderOptions.put(animal, new RenderOptions(false, false));

        entities.add(animal);

//        Light light = new Light(Light.Type.POINT, new float[]{0, 2, 0}, Color.WHITE);

//        Entity lightObj = new SphereEntity(.1f);
//        lightObj.setPos(light.position);
//        lightObj.color = light.color;
//
//        renderOptions.put(lightObj, new RenderOptions(false, false, true));
//        entities.add(lightObj);
//
//        this.light = light;
//        lights.add(light);
    }

    AnimalScene() {
        this(RenderMode.SOLID);
    }
}
class GroundScene extends Scene {

    GroundScene(int tilesX, int tilesY) {

        backgroundColor = Color.BLUE;


        Entity ground = new GroundEntity(Color.GREEN, tilesX, tilesY, 10, 10);

        ground.setPos(0, -.5f, 0);
        float scale = 0.1f;
        ground.setScale(scale);

        addEntity(ground);

        renderOptions.put(ground, new RenderOptions(true, false));

    }
}

class TestScene extends Scene {

    TestScene() {
        backgroundColor = Color.BLACK;

        Entity plane = new Entity("jet.obj");

//        plane.setPos(0, 0, -2);

        float scale = 1f;
        plane.setScale(scale);
        plane.color = Color.GRAY;

        renderOptions.put(plane, new RenderOptions(false, false));
        addEntity(plane);

//        Entity lightObj = new SphereEntity(.1f);
//        lightObj.setPos(new float[]{0, 10, 0});
//        lightObj.color = light.color;
//        Light light = new Light(Light.Type.POINT, lightObj.transform.position, Color.WHITE);
//
//
//        renderOptions.put(lightObj, new RenderOptions(false, false, true));
//        entities.add(lightObj);
//
//        this.light = light;
    }

}

class BenchMarkScene extends Scene {
    BenchMarkScene() {
        backgroundColor = Color.BLUE;

        int numX = 50;
        int numY = numX;

        for(int x = -numX/2; x < Math.max(1, numX/2); x++) {
            for(int y = -numY/2; y < Math.max(1, numY/2); y++) {
                Entity beaver = new Entity("OBJ-animals/animal-beaver.obj");
                beaver.color = Color.BROWN;
                beaver.setPos(x+.5f, y+.5f, -4);
                beaver.behaviors.add(new RotateBehavior(Math.PI/4, new float[]{0, 1, 0}));
//                beaver.behaviors.add(new OscillateBehavior(1, new float[]{0, 1, 0}, Matrix.getSlice(beaver.transform.position, 0, 3)));
                renderOptions.put(beaver, new RenderOptions(true, true));
                entities.add(beaver);
            }
        }
    }
}
class RenderOptions {
//    Scene.RenderMode renderMode;
    boolean randomizeTexture;
    boolean showWireFrame;
    boolean isLightObj;
    RenderOptions() {
//        renderMode = Scene.defaultRenderMode;
        randomizeTexture = Scene.defaultRandomizeTexture;
        showWireFrame = Scene.defaultShowWireFrame;
    }

    RenderOptions(boolean randomizeTexture, boolean showWireFrame) {
        this(randomizeTexture, showWireFrame, false);
    }
    RenderOptions(boolean randomizeTexture, boolean showWireFrame, boolean isLightObj) {
//        this.renderMode = renderMode;
        this.randomizeTexture = randomizeTexture;
        this.showWireFrame = showWireFrame;
        this.isLightObj = isLightObj;
    }
}
