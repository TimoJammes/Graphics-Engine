package com.engine;

public class Transform {
    Vector4 position;
    Vector3 scale;
    Quaternion rotation;

     Transform(Vector4 position, Quaternion rotation, Vector3 scale) {
        this.position = position;
        this.scale = scale;
        this.rotation = rotation;
    }

    Transform() {
        this.position = new Vector4(0, 0, 0, 1);
        this.scale = new Vector3(1, 1, 1);
        this.rotation = new Quaternion();
    }

    void translateWorld(float dx, float dy, float dz) {
         position = position.add(new Vector4(dx, dy, dz, 0));


    }
    void translateLocal(float dx, float dy, float dz) {

         Vector4 forward = rotation.rotate(dx, dy, dz);
         position = position.add(forward);
    }

     final Matrix toMatrix() {



        final float[][] array = new float[4][4];

        final Matrix quaternionMatrix = rotation.toMatrix();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                array[i][j] = quaternionMatrix.get(i, j) * scale.get(j);
            }
        }

        array[0][3] = position.get(0);
        array[1][3] = position.get(1);
        array[2][3] = position.get(2);

        array[3][3] = 1;

        return new Matrix(array);
    }

    Vector3 getUp() {
        return rotation.getUp();
    }
    Vector3 getForward() {
        return rotation.getForward();
    }
    Vector3 getRight() {
        return rotation.getRight();
    }

    void reset() {
        position = new Vector4(0, 0, 0, 1);
        rotation = new Quaternion();
        scale = new Vector3(1, 1, 1);
    }
}
