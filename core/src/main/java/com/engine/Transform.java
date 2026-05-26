package com.engine;

public class Transform {
    float[] position; //x, y, z, w
    float[] scale; //sx, sy, sz
    Quaternion rotation;

    Transform(float[] position, Quaternion rotation, float[] scale) {
        this.position = position;
        this.scale = scale;
        this.rotation = rotation;
    }

    Transform() {
        this.position = new float[]{0, 0, 0, 1};
        this.scale = new float[]{1, 1, 1};
        this.rotation = new Quaternion();
    }

    void translateWorld(float dx, float dy, float dz) {
        position = Matrix.add(position, new float[]{dx, dy, dz, 0});


    }

    void translateLocal(float dx, float dy, float dz) {

        float[] forward = rotation.rotate(dx, dy, dz);
        position = Matrix.add(position, forward);
    }

    final float[][] toMatrix() {
        final float[][] res = new float[4][4];

        final float[][] quaternionMatrix = rotation.toMatrix();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                res[i][j] = quaternionMatrix[i][j] * scale[j];
            }
        }

        res[0][3] = position[0];
        res[1][3] = position[1];
        res[2][3] = position[2];
        res[3][3] = 1;

        return res;
    }

    float[] getUp() {
        return rotation.getUp();
    }

    float[] getForward() {
        return rotation.getForward();
    }

    float[] getRight() {
        return rotation.getRight();
    }

    void reset() {
        position = new float[]{0, 0, 0, 1};
        rotation = new Quaternion();
        scale = new float[]{1, 1, 1};
    }

    public Transform clone() {
        Transform copy = new Transform();
        copy.position = position.clone();
        copy.scale = scale.clone();
        copy.rotation = rotation.clone();
        return copy;
    }
}
