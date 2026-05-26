package com.engine;

import com.badlogic.gdx.graphics.Color;

/*Claude Generated*/
public class EntityBuilder {

    private final Entity entity;
    private final Scene scene;           // holds entities + renderOptions
    private boolean wireframe = false;
    private boolean hasRenderOption = false;

    EntityBuilder(Entity entity, Scene scene) {
        this.entity = entity;
        this.scene = scene;
    }

    public EntityBuilder pos(float x, float y, float z) {
        entity.setPos(x, y, z);
        return this;
    }

    public EntityBuilder pos(float[] pos) {
        entity.setPos(pos);
        return this;
    }

    public EntityBuilder material(Material material) {
        entity.material.ambientStrength = material.ambientStrength;
        entity.material.diffuseStrength = material.diffuseStrength;
        entity.material.specularStrength = material.specularStrength;
        entity.material.shininess = material.shininess;
        return this;
    }

    public EntityBuilder material(float ambientStrength, float diffuseStrength, float specularStrength, int shininess) {
        entity.material.ambientStrength = ambientStrength;
        entity.material.diffuseStrength = diffuseStrength;
        entity.material.specularStrength = specularStrength;
        entity.material.shininess = shininess;
        return this;
    }

    public EntityBuilder isLightObj() {
        entity.isLightObj = true;
        return this;
    }
    public EntityBuilder scale(float scale) {
        entity.setScale(scale);
        return this;
    }

    public EntityBuilder color(Color color) {
        entity.color = color;
        return this;
    }

    public EntityBuilder rotateWorld(double angle, float x, float y, float z) {
        entity.rotateWorld(angle, x, y, z);
        return this;
    }

    public EntityBuilder behavior(Behavior b) {
        entity.behaviors.add(b);
        return this;
    }

    public EntityBuilder showWireFrame() {
        this.wireframe = true;
        this.hasRenderOption = true;
        return this;
    }

    /** Registers the entity with the scene and returns it. */
    public Entity spawn() {
        if (hasRenderOption) {
            scene.addRenderOption(entity, wireframe);
        }
        scene.entities.add(entity);
        return entity;
    }
}
