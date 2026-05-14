package com.engine;

import com.badlogic.gdx.graphics.Color;

import java.io.IOException;
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

    public Entity(String objFilePath) {
        ObjLoader.Result res;
        try {
            res = ObjLoader.load(objFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Mesh mesh1 = new Mesh(res.vertices, res.indices);

        this(mesh1, res.color);
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

class GroundEntity extends Entity {


    GroundEntity(Color color, int tilesX, int tilesY) {

        float[] vertices = new float[tilesX*tilesY*3];

        for (int i = 0; i < tilesX; i++) {
            for (int j = 0; j < tilesY; j++) {
                vertices[(i*tilesY+j)*3] = i - tilesX/2f;
                vertices[(i*tilesY+j)*3+2] = j - tilesY/2f;
            }
        }

        int[] indices = new int[(tilesX-1)*(tilesY-1)*6];
        int idx = 0;
        for (int i = 0; i < tilesX-1; i++) {
            for (int j = 0; j < tilesY-1; j++) {
                int tl = i*tilesY + j;
                int tr = i*tilesY + j+1;
                int bl = (i+1)*tilesY + j;
                int br = (i+1)*tilesY + j+1;

                indices[idx++] = tl;
                indices[idx++] = bl;
                indices[idx++] = br;

                indices[idx++] = tl;
                indices[idx++] = br;
                indices[idx++] = tr;
            }
        }


        Mesh mesh = new Mesh(vertices, indices);

        super(mesh, color);

    }
}
