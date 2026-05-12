package com.engine;

public interface Movable {

    void rotateLocal(double theta, float ax, float ay, float az);
    void rotateWorld(double theta, float ax, float ay, float az);

    void translateWorld(float dx, float dy, float dz);

    void translateLocal(float dx, float dy, float dz);
}
