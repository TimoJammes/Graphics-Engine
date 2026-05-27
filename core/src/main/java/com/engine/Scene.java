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
    LightingType lightingType = LightingType.GOURAUD;
//    final List<Light> lights = new ArrayList<>();

    final Map<Entity, RenderOptions> renderOptions = new HashMap<>();

    Color backgroundColor;

    RenderMode renderMode = RenderMode.SOLID;

    boolean update(float dt) {

        boolean isUpdate = false;
        for (Entity entity : entities) {
            for (Behavior b : entity.behaviors) {
                boolean updated = b.update(entity, dt);
                isUpdate |= updated;
            }
        }
        return isUpdate;
    }
//    void addEntity(Entity e) {
//        entities.add(e);
//    }

    protected EntityBuilder entity(String objFilePath) {
        return new EntityBuilder(new Entity(objFilePath), this);
    }

    protected EntityBuilder entity(Entity e) {
        return new EntityBuilder(e, this);
    }

    protected void light(Entity lightObj, Light.Type type) {
        lightObj.isLightObj = true;
        light = new Light(type, lightObj, lightObj.material);
    }

    boolean hasLight() {
        return light != null;
    }

    void addRenderOption(Entity e, boolean showWireFrame) {
        renderOptions.put(e, new RenderOptions(showWireFrame));
    }
}

class TeapotScene extends Scene {
    TeapotScene() {

        backgroundColor = Color.BLACK;

        entity("teapot.obj")
//            .pos(0, 0, 0)
            .rotateWorld(-Math.PI / 2, 1, 0, 0)
            .diffuse(Color.PINK)
            .behavior(new RotateBehavior(-Math.PI / 6, new float[]{0, 1, 0}))
            .behavior(new RotateBehavior(Math.PI / 10, new float[]{1, 0, 0}))
//            .showWireFrame()
            .spawn();

        GroundEntity ground = new GroundEntity(100, 100, 20, 20);
        entity(ground)
            .pos(0, -3, 0)
            .spawn();

        Entity lightObj = entity(new SphereEntity(.1f))
            .pos(4, 6, 0)
            .diffuse(Color.WHITE)
            .behavior(new CircleBehavior(Math.PI / 8, new float[]{0, 6, 0}, new float[]{0, 1, 0}))
            .spawn();

        light(lightObj, Light.Type.POINT);
    }
}

class MultiScene extends Scene {

    MultiScene() {

        backgroundColor = Color.BLUE;

        Entity beaver1 = entity("OBJ-animals/animal-beaver.obj")
            .material(new Material(Material.Type.BRONZE))
//            .diffuse(Color.BROWN)
            .pos(0, 0, -2)
            .behavior(new RotateBehavior(Math.PI, new float[]{0, 1, 0}))
            .spawn();

//        System.out.println(beaver1.material.diffuse);
//        System.out.println(beaver1.material.ambient);
//        System.out.println(beaver1.material.specular);
//        System.out.println(beaver1.material.shininess);

        float[] pos2 = new float[]{0, 1, 2};
        Entity beaver2 = new Entity(beaver1);
        System.out.println(beaver2.material.diffuse);
        entity(beaver2)
//            .material(new Material(Material.Type.EMERALD))
            .pos(pos2)
            .rotateWorld(Math.PI, 0, 1, 0)
            .behavior(new OscillateBehavior(1, new float[]{0, 1, 0}, pos2))
            .spawn();

        entity(new Entity(beaver1))
            .pos(2, 0, 0)
            .rotateWorld(-Math.PI / 2, 0, 1, 0)
            .spawn();

        entity(new Entity(beaver1))
            .pos(-2, 0, 0)
            .rotateWorld(Math.PI / 2, 0, 1, 0)
            .spawn();

        entity(new GroundEntity(100, 100, 50, 50))
//            .scale(2f)
            .diffuse(Color.GREEN)
//            .specular(new Color(1, 1, 1, 1))
//            .shininess(32)
            .spawn();

        Entity lightObj = entity(new SphereEntity(.1f))
            .pos(4, 5, 0)
            .diffuse(Color.WHITE)
            .behavior(new CircleBehavior(Math.PI / 8, new float[]{0, 5, 0}, new float[]{0, 1, 0}))
//            .behavior(new OscillateBehavior(1, new float[]{0, 1, 0}, new float[]{4, 5, 0}))
            .spawn();

        light(lightObj, Light.Type.POINT);
    }
}

class AnimalScene extends Scene {

    AnimalScene(RenderMode mode) {
        backgroundColor = Color.BLUE;

        entity("OBJ-animals/animal-beaver.obj")
            .diffuse(Color.BROWN)
            .pos(0, 0, -3)
            .behavior(new RotateBehavior(Math.PI, new float[]{0, 1, 0}))
            .spawn();
    }

    AnimalScene() {
        this(RenderMode.SOLID);
    }
}

class GroundScene extends Scene {

    GroundScene(int tilesX, int tilesY) {

        backgroundColor = Color.BLUE;

        entity(new GroundEntity(tilesX, tilesY, 10, 10))
            .pos(0, -.5f, 0)
            .scale(0.1f)
            .spawn();
    }
}

class TestScene extends Scene {

    TestScene() {
        backgroundColor = Color.BLACK;

        entity("jet.obj")
            .scale(1f)
            .diffuse(Color.GRAY)
            .spawn();
    }
}

class BenchMarkScene extends Scene {
    BenchMarkScene() {
        backgroundColor = Color.BLUE;

        int numX = 50;
        int numY = numX;

        for (int x = -numX / 2; x < Math.max(1, numX / 2); x++) {
            for (int y = -numY / 2; y < Math.max(1, numY / 2); y++) {
                entity("OBJ-animals/animal-beaver.obj")
                    .diffuse(Color.BROWN)
                    .pos(x + .5f, y + .5f, -4)
                    .behavior(new RotateBehavior(Math.PI / 4, new float[]{0, 1, 0}))
                    .showWireFrame()
                    .spawn();
            }
        }
    }
}

class RenderOptions {
    //    Scene.RenderMode renderMode;
    boolean randomizeTexture;
    boolean showWireFrame;

    RenderOptions() {
//        renderMode = Scene.defaultRenderMode;
        randomizeTexture = Scene.defaultRandomizeTexture;
        showWireFrame = Scene.defaultShowWireFrame;
    }

//    RenderOptions(boolean randomizeTexture, boolean showWireFrame) {

    /// /        this.renderMode = renderMode;
//        this.randomizeTexture = randomizeTexture;
//        this.showWireFrame = showWireFrame;
//    }

    RenderOptions(boolean showWireFrame) {
//        this.renderMode = renderMode;
        this.randomizeTexture = false;
        this.showWireFrame = showWireFrame;
    }
}
