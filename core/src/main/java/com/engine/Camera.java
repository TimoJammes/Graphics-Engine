package com.engine;

//import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class Camera implements Movable {


    protected final Transform transform;

    private final int fovDeg;
    private final double fovRad;
    private final float nearPlane;
    private final float farPlane;
    private final float aspectRatio;

    double pitch = 0;
    double yaw = 0;

     Camera(int fovDeg, float near, float far, float aspect, Transform transform) {
        this.transform = transform;
        this.fovDeg = fovDeg;
        fovRad = Math.toRadians(fovDeg);
        this.nearPlane = near;
        this.farPlane = far;
        this.aspectRatio = aspect;
    }

     Camera(int FOV, float near, float far, float aspect, Vector4 position, Quaternion rotation) {
        this(FOV, near, far, aspect, new Transform(position, rotation, new Vector3(1, 1, 1)));
    }

    boolean update(float dt) {
        double theta = Math.PI / 256 * dt * 144;
        float mag = .02f * dt * 144;

        float dx = 0, dy = 0, dz = 0;
        float rotX = 0, rotY = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))       dx -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))      dx += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.UP))         dz -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))       dz += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE))      dy += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) dy -= mag;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) rotX += (float) theta;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) rotX -= (float) theta;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) rotY += (float) theta;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) rotY -= (float) theta;

        if (Gdx.input.isKeyPressed(Input.Keys.R)) resetTransform();

        translateLocal(dx, 0, dz);
        translateWorld(0, dy, 0);

//        if (rotX != 0) rotateLocal(rotX, 1, 0, 0);
//        if (rotY != 0) rotateLocal(rotY, 0, 1, 0);

        pitch += rotX;
        yaw += rotY;

        pitch = Math.clamp(pitch, -Math.PI / 2, Math.PI / 2);

        Quaternion qYaw   = new Quaternion(); qYaw.rotateWorld(yaw,   0, 1, 0);
        Quaternion qPitch = new Quaternion(); qPitch.rotateWorld(pitch, 1, 0, 0);
        transform.rotation = qYaw.mul(qPitch);  // yaw first, then pitch


        return (rotX != 0 || rotY != 0 || dx  != 0 || dy != 0 || dz != 0 || Gdx.input.isKeyPressed(Input.Keys.R));
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
