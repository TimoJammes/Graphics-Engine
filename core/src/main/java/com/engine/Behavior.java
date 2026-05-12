package com.engine;

public interface Behavior {

    public void update(Entity entity, float dt);
}


class RotateBehavior implements Behavior {

    double speed; // rad/s
    Vector3 axis;

    public RotateBehavior(double speed, Vector3 axis) {
        this.axis = axis;
        this.speed = speed;
    }

    @Override
    public void update(Entity entity, float dt) {
        entity.rotateWorld(speed*dt, axis.x, axis.y, axis.z);
    }
}

class OscillateBehavior implements Behavior {
    float speed;
    Vector3 axis;
    int dir = 1;
    Vector3 center;

    public OscillateBehavior(float speed, Vector3 axis, Vector3 center) {
        this.speed = speed;
        this.axis = axis;
        this.center = center;
    }

    @Override
    public void update(Entity entity, float dt) {
        float dx = speed * dt * axis.x * dir;
        float dy = speed * dt * axis.y * dir;
        float dz = speed * dt * axis.z * dir;
        entity.translateWorld(dx, dy, dz);

        if (Matrix.normSqr(entity.transform.position.slice(0, 3).sub(center)) >= 1)
            dir *= -1;
    }
}
