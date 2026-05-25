package com.engine;

interface Behavior {

    boolean update(Entity entity, float dt);
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

class CircleBehavior implements Behavior {

    private static final double DRAW_THRESHOLD = Math.PI / 180;

    private final double omega; // rad/s
    private final float[] center;
    private final float[] planeNormal; // normal to the rotation plane
    private double angle;
    private double rotationSinceLastDraw;

    public CircleBehavior(double omega, float[] center, float[] planeNormal) {
        this.omega = omega;
        this.center = center;
        this.planeNormal = planeNormal;
    }

    @Override
    public boolean update(Entity entity, float dt) {
        double theta = omega * dt;
        angle += theta;

        // compute two orthogonal axes in the rotation plane
        float[] u = perpendicular(planeNormal);
        float[] v = Matrix.cross(planeNormal, u);

        float radius = distance(entity.transform.position, center);

        entity.transform.position[0] = center[0] + radius * (float)(Math.cos(angle) * u[0] + Math.sin(angle) * v[0]);
        entity.transform.position[1] = center[1] + radius * (float)(Math.cos(angle) * u[1] + Math.sin(angle) * v[1]);
        entity.transform.position[2] = center[2] + radius * (float)(Math.cos(angle) * u[2] + Math.sin(angle) * v[2]);

        rotationSinceLastDraw += theta;
        if (rotationSinceLastDraw > DRAW_THRESHOLD) {
            rotationSinceLastDraw = 0;
            return true;
        }
        return false;
    }

    private float[] perpendicular(float[] n) {
        // find a vector not parallel to n, then cross product gives perpendicular
        float[] arbitrary = Math.abs(n[0]) < 0.9f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
        return Matrix.normalize(Matrix.cross(n, arbitrary));
    }

    private float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
