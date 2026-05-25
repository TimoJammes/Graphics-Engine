package com.engine;

import com.badlogic.gdx.graphics.Color;

public class Light {

    enum Type {DIRECTIONAL, POINT, SPOT}

    private Entity entity;
    private Type type;
    private float[] direction;  // used by DIRECTIONAL and SPOT
    private float cutoffAngle;  // used by SPOT only
    Color color;
    private float intensity;

    Light(Type type, Entity entity, float[] direction, float cutoffAngle, Color color, float intensity) {
        this.type = type;
        this.direction = direction;
        this.cutoffAngle = cutoffAngle;
        this.color = color;
        this.intensity = intensity;
        this.entity = entity;
    }

    Light(Type type, Entity entity, Color color) {
        this(type, entity, null, -1, color, 1);
    }

    float[] getPosition() {return entity.transform.position;}

}
