package com.engine;

public interface Behavior {

    public boolean update(Entity entity, float dt);
}


class RotateBehavior implements Behavior {

    private static final double DRAW_THRESHOLD = Math.PI / 180;

    double omega; // rad/s
    float[] axis;
    double rotationSinceLastDraw;

    public RotateBehavior(double omega, float[] axis) {
        this.axis = axis;
        this.omega = omega;
    }

    @Override
    public boolean update(Entity entity, float dt) {
        double theta = omega * dt;
        entity.rotateWorld(theta, Matrix.getX(axis), Matrix.getY(axis), Matrix.getZ(axis));

        rotationSinceLastDraw += theta;
        if (rotationSinceLastDraw > DRAW_THRESHOLD) {
            rotationSinceLastDraw = 0;
            return true;
        }
        return false;
    }
}

class OscillateBehavior implements Behavior {

    private static final float DRAW_THRESHOLD_SQR = 0.005f*0.005f;
    float speed;
    float[] axis;
    int dir = 1;
    float[] center;

    float distSqrSinceLastDraw;

    public OscillateBehavior(float speed, float[] axis, float[] center) {
        this.speed = speed;
        this.axis = axis;
        this.center = center;
    }

    @Override
    public boolean update(Entity entity, float dt) {
        float dx = speed * dt * Matrix.getX(axis) * dir;
        float dy = speed * dt * Matrix.getY(axis) * dir;
        float dz = speed * dt * Matrix.getZ(axis) * dir;
        entity.translateWorld(dx, dy, dz);

        if (Matrix.normSqr(Matrix.sub(Matrix.getSlice(entity.transform.position, 0, 3), center)) >= 1)
            dir *= -1;

//        return true;
        distSqrSinceLastDraw += dx*dx+dy*dy+dz*dz;
//        System.out.println(distSqrSinceLastDraw/(DRAW_THRESHOLD*DRAW_THRESHOLD));

        if (distSqrSinceLastDraw > DRAW_THRESHOLD_SQR) {
            distSqrSinceLastDraw = 0;
            return true;
        }
        return false;

    }
}
