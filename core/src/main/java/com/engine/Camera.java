package com.engine;

import java.util.Arrays;

public class Camera {


    protected final Transform transform;

    private final int FOV;
    private final double fovRad;
    private final float nearPlane;
    private final float farPlane;
    private final float aspectRatio;

     Camera(int FOV, float near, float far, float aspect, Transform transform) {
        this.transform = transform;
        this.FOV = FOV;
        fovRad = Math.toRadians(FOV);
        this.nearPlane = near;
        this.farPlane = far;
        this.aspectRatio = aspect;
    }

     Camera(int FOV, float near, float far, float aspect, Vector4 position, Quaternion rotation) {
        this(FOV, near, far, aspect, new Transform(position, rotation, new Vector3(1, 1, 1)));
    }

    void rotate(double theta, float ax, float ay, float az) {
         transform.rotation.rotate(theta, ax, ay, az);
    }

    void rotate(double theta, Vector3 axis) {
         transform.rotation.rotate(theta, axis.get(0), axis.get(1), axis.get(2));
    }

    void translate(float dx, float dy, float dz) {
         transform.translate(dx, dy, dz);
    }

    void translateLocal(float dx, float dy, float dz) {
         transform.translateLocal(dx, dy, dz);
    }

     final Matrix getViewMatrix() {


        final Matrix rotationMatrix = transform.rotation.toMatrix();

        final Matrix rotationMatrixT = rotationMatrix.T();





        final Vector3 minusRTransposeDotPos = new Vector3(
            rotationMatrixT.row(0).dot(transform.position.slice(0, 3)),
            rotationMatrixT.row(1).dot(transform.position.slice(0, 3)),
            rotationMatrixT.row(2).dot(transform.position.slice(0, 3)))
            .negate();

        final float[][] array = new float[4][4];

        for (int i=0;i<3;i++) {
            for (int j=0;j<3;j++) {
                array[i][j] = rotationMatrixT.get(i, j);
            }
        }

        array[0][3] = minusRTransposeDotPos.get(0);
        array[1][3] = minusRTransposeDotPos.get(1);
        array[2][3] = minusRTransposeDotPos.get(2);

        array[3][3] = 1;

        return new Matrix(array);
    }


     final Matrix getProjectionMatrix() {

        final float[][] array = new float[4][4];

        array[0][0] = (float) (1 / (aspectRatio * Math.tan(fovRad / 2.0)));
        array[1][1] = (float) (1 / Math.tan(fovRad / 2.0));
        array[2][2] = -(farPlane + nearPlane) / (farPlane - nearPlane);
        array[2][3] = -2 * farPlane * nearPlane / (farPlane - nearPlane);

        array[3][2] = -1;

        return new Matrix(array);
    }

    void resetTransform() {
         transform.reset();
    }




}
