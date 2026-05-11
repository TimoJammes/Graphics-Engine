package com.engine;

import com.badlogic.gdx.graphics.Color;


public class Entity {

     final Transform transform;
     final Mesh mesh;
     final Color color;
//    private final Material material;

    public Entity(Transform transform, Mesh mesh, Color color) {
        this.transform = transform;
        this.mesh = mesh;
        this.color = color;
    }
}
