package com.engine;

import com.badlogic.gdx.graphics.Color;

public class Light {

    public enum Type {DIRECTIONAL, POINT, SPOT}

    private Entity entity;
    Type type;
    private float[] direction;  // used by DIRECTIONAL and SPOT
    private float cutoffAngle;  // used by SPOT only
    Color diffuse;
    Color specular;
    Color ambient;

    Light(Type type, Entity entity, float[] direction, float cutoffAngle, Color diffuse, Color specular, Color ambient) {
        this.type = type;
        this.direction = direction;
        this.cutoffAngle = cutoffAngle;
        this.diffuse = diffuse;
        this.specular = specular;
        this.ambient = ambient;
        this.entity = entity;
    }

    Light(Type type, Entity entity, Material material) {
        this(type, entity, null, -1, material.diffuse, material.specular, material.ambient);
    }

    float[] getPosition() {return entity.transform.position;}
    float getX() {return getPosition()[0];}
    float getY() {return getPosition()[1];}
    float getZ() {return getPosition()[2];}

}
