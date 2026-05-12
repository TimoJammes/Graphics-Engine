package com.engine;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;


public class Entity implements Movable {

     final Transform transform;
     final Mesh mesh;
     Color color;

     List<Behavior> behaviors = new ArrayList<>();

     String name;

    public Entity(Transform transform, Mesh mesh, Color color) {
        this.transform = transform;
        this.mesh = mesh;
        this.color = color;
    }

    public Entity(Mesh mesh, Color color) {
        this.transform = new Transform();
        this.mesh = mesh;
        this.color = color;
    }

    public void setPos(float x, float y, float z) {
        transform.position.set(0, x);
        transform.position.set(1, y);
        transform.position.set(2, z);
    }
    public void setScale(float x, float y, float z) {
        transform.scale.set(0, x);
        transform.scale.set(1, y);
        transform.scale.set(2, z);
    }

    public void rotateLocal(double theta, float ax, float ay, float az) {
        transform.rotation.rotateLocal(theta, ax, ay, az);
    }
    public void rotateWorld(double theta, float ax, float ay, float az) {
        transform.rotation.rotateWorld(theta, ax, ay, az);
    }

    public void translateWorld(float dx, float dy, float dz) {
        transform.translateWorld(dx, dy, dz);
    }

    public void translateLocal(float dx, float dy, float dz) {
        transform.translateLocal(dx, dy, dz);
    }
}


