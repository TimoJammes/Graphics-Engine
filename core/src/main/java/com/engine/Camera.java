package com.engine;

//import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class Camera implements Movable {


    public static final int TUNED_FPS = 144;
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

    Camera(int FOV, float near, float far, float aspect, float[] position, Quaternion rotation) {
        this(FOV, near, far, aspect, new Transform(position, rotation, new float[]{1, 1, 1}));
    }

    boolean update(float dt) {
        double theta = Math.PI / 256 * dt * TUNED_FPS;
        float mag = .02f * dt * TUNED_FPS;

        float dx = 0, dy = 0, dz = 0;
        float rotX = 0, rotY = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) dz -= mag;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) dz += mag;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) dy += mag;
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

        Quaternion qYaw = new Quaternion();
        qYaw.rotateWorld(yaw, 0, 1, 0);
        Quaternion qPitch = new Quaternion();
        qPitch.rotateWorld(pitch, 1, 0, 0);
        transform.rotation = qYaw.mul(qPitch);  // yaw first, then pitch


        return (rotX != 0 || rotY != 0 || dx != 0 || dy != 0 || dz != 0 || Gdx.input.isKeyJustPressed(Input.Keys.R));
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

    final float[][] getViewMatrix() {


        final float[][] rotationMatrix = transform.rotation.toMatrix();

        final float[][] rotationMatrixT = Matrix.transpose(rotationMatrix);

        final float[] slice3Pos = Matrix.getSlice(transform.position, 0, 3);
        final float[] minusRTDotPos = Matrix.negate(
            new float[]{
                Matrix.dot(Matrix.getRow(rotationMatrixT, 0), slice3Pos),
                Matrix.dot(Matrix.getRow(rotationMatrixT, 1), slice3Pos),
                Matrix.dot(Matrix.getRow(rotationMatrixT, 2), slice3Pos)
            }
        );

        final float[][] res = new float[4][4];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                res[i][j] = rotationMatrixT[i][j];
            }
        }

        res[0][3] = minusRTDotPos[0];
        res[1][3] = minusRTDotPos[1];
        res[2][3] = minusRTDotPos[2];

        res[3][3] = 1;

        return res;
    }


    final float[][] getProjectionMatrix() {

        final float[][] res = new float[4][4];

        res[0][0] = (float) (1 / (aspectRatio * Math.tan(fovRad / 2.0)));
        res[1][1] = (float) (1 / Math.tan(fovRad / 2.0));
        res[2][2] = -(farPlane + nearPlane) / (farPlane - nearPlane);
        res[2][3] = -2 * farPlane * nearPlane / (farPlane - nearPlane);

        res[3][2] = -1;

        return res;
    }

    void resetTransform() {
        transform.reset();
    }


}
