package com.engine;

import com.badlogic.gdx.graphics.Color;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Entity implements Movable {

    final Transform transform;
    final Mesh mesh;
    final Material material;

    Color color;
    boolean hasNormals = false;
    boolean isLightObj = false;

    List<Behavior> behaviors = new ArrayList<>();

//    String name;

    public Entity(Transform transform, Mesh mesh, Color color, Material material) {
        this.transform = transform;
        this.mesh = mesh;
        this.color = color;
        this.material = material;
    }

    public Entity(Transform transform, Mesh mesh, Color color) {
        this(transform, mesh, color, new Material());

//        System.out.println(mesh.vertices.length / 3);
    }

    public Entity(Mesh mesh, Color color) {
        this(new Transform(), mesh, color);
    }

    public Entity(String objFilePath) {
        ObjLoader.Result res;
        try {
            res = ObjLoader.load(objFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


//        Mesh mesh1 = new Mesh(res.vertices, res.indices);
        assert res.hasNormals() : "entity loaded from .obj has no normals";
        Mesh mesh1 = new Mesh(res.vertices, res.normals, res.indices);
//        System.out.println(res.vertices.length);
        this(mesh1, res.color);
        hasNormals = true;
    }

    public Entity(Entity e) {
        this(e.transform.clone(), e.mesh, e.color);
        hasNormals = e.hasNormals;
    }

    public void setPos(float x, float y, float z) {
        transform.position[0] = x;
        transform.position[1] = y;
        transform.position[2] = z;
    }

    public void setPos(float[] pos) {
        setPos(pos[0], pos[1], pos[2]);
    }

    public void setScale(float s) {
        transform.scale[0] = s;
        transform.scale[1] = s;
        transform.scale[2] = s;
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

    public double angleAroundAxis(float ax, float ay, float az) {
        return transform.rotation.angleAroundAxis(ax, ay, az);
    }

    public Light generateLight(Light.Type type) {
        return new Light(type, this, color);
    }
}

//[np.array([Rx(-np.pi/5) @ Ry(t2) @ ((Rx(t1) @ np.array([0, 0, innerRadius]) + np.array([0, 0, outerRadius])))
// for t2 in np.linspace(0, 2*np.pi, nSidesHoriz)])
// for t1 in np.linspace(0, 2*np.pi, nSidesVert)]
class SphereEntity extends Entity {

    SphereEntity(float radius) {

        int stacks = 16;  // latitude divisions
        int slices = 16;  // longitude divisions

        int vertexCount = (stacks + 1) * (slices + 1);
        float[] vertices = new float[vertexCount * 3];
        int vIdx = 0;

        for (int i = 0; i <= stacks; i++) {
            float phi = (float) (Math.PI * i / stacks);        // 0 to PI
            for (int j = 0; j <= slices; j++) {
                float theta = (float) (2 * Math.PI * j / slices); // 0 to 2PI
                vertices[vIdx++] = radius * (float) (Math.sin(phi) * Math.cos(theta));
                vertices[vIdx++] = radius * (float) (Math.cos(phi));
                vertices[vIdx++] = radius * (float) (Math.sin(phi) * Math.sin(theta));
            }
        }

        int triangleCount = stacks * slices * 2;
        int[] indices = new int[triangleCount * 3];
        int iIdx = 0;

        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int tl = i * (slices + 1) + j;
                int tr = tl + 1;
                int bl = tl + (slices + 1);
                int br = bl + 1;

                indices[iIdx++] = tl;
                indices[iIdx++] = bl;
                indices[iIdx++] = tr;

                indices[iIdx++] = tr;
                indices[iIdx++] = bl;
                indices[iIdx++] = br;
            }
        }

        Mesh mesh = new Mesh(vertices, indices);

        super(mesh, Color.WHITE);
    }
}

class GroundEntity extends Entity {


    GroundEntity(Color color, int tilesX, int tilesY, float sizeX, float sizeY) {

        float[] vertices = new float[tilesX * tilesY * 3];

        for (int i = 0; i < tilesX; i++) {
            for (int j = 0; j < tilesY; j++) {
                vertices[(i * tilesY + j) * 3]     = (i / (float)(tilesX - 1) - 0.5f) * sizeX;
                vertices[(i * tilesY + j) * 3 + 2] = (j / (float)(tilesY - 1) - 0.5f) * sizeY;
            }
        }

        int[] indices = new int[(tilesX - 1) * (tilesY - 1) * 6];
        int idx = 0;
        for (int i = 0; i < tilesX - 1; i++) {
            for (int j = 0; j < tilesY - 1; j++) {
                int tl = i * tilesY + j;
                int tr = i * tilesY + j + 1;
                int bl = (i + 1) * tilesY + j;
                int br = (i + 1) * tilesY + j + 1;

                indices[idx++] = tl;
                indices[idx++] = bl;
                indices[idx++] = br;

                indices[idx++] = tl;
                indices[idx++] = br;
                indices[idx++] = tr;
            }
        }

        float[] normals = new float[tilesX * tilesY * 3];


        for (int i = 0; i < tilesX * tilesY; i++) {
            normals[i * 3] = 0;
            normals[i * 3 + 1] = 1;
            normals[i * 3 + 2] = 0;
        }


        Mesh mesh = new Mesh(vertices, normals, indices);

        super(mesh, color);
        hasNormals = true;
        mesh.isClosed = false;

    }
}
