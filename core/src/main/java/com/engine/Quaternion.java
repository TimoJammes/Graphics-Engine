package com.engine;

public class Quaternion{
    float w;
    float x;
    float y;
    float z;

     Quaternion(float w, float x, float y, float z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

     Quaternion() {
        this(1, 0, 0, 0);
    }

     float[][] toMatrix() {
        float[][] m = new float[3][3];

        m[0][0] = 1 - 2*(y*y + z*z);
        m[0][1] = 2*(x*y - w*z);
        m[0][2] = 2*(x*z + w*y);

        m[1][0] = 2*(x*y + w*z);
        m[1][1] = 1 - 2*(x*x + z*z);
        m[1][2] = 2*(y*z - w*x);

        m[2][0] = 2*(x*z - w*y);
        m[2][1] = 2*(y*z + w*x);
        m[2][2] = 1 - 2*(x*x + y*y);

        return m;
    }

    void rotateLocal(double theta, float ax, float ay, float az) {
        double l = Math.sqrt(ax*ax + ay*ay + az*az);

        ax = (float)(ax / l);
        ay = (float)(ay / l);
        az = (float)(az / l);

        float sinHalfTheta = (float) Math.sin(theta/2);

        float deltaW = (float) Math.cos(theta/2);
        float deltaX = sinHalfTheta * ax;
        float deltaY = sinHalfTheta * ay;
        float deltaZ = sinHalfTheta * az;

        float[] newQCoords = this.mul(deltaW, deltaX, deltaY, deltaZ);

        w = newQCoords[0];
        x = newQCoords[1];
        y = newQCoords[2];
        z = newQCoords[3];

        normalize();

    }
    void rotateWorld(double theta, float ax, float ay, float az) {
        double l = Math.sqrt(ax*ax + ay*ay + az*az);
        ax /= (float) l;
        ay /= (float) l;
        az /= (float) l;

        float sinHalfTheta = (float) Math.sin(theta/2);

        float deltaW = (float) Math.cos(theta/2);
        float deltaX = sinHalfTheta * ax;
        float deltaY = sinHalfTheta * ay;
        float deltaZ = sinHalfTheta * az;

        Quaternion deltaQ = new  Quaternion(deltaW, deltaX, deltaY, deltaZ);

        Quaternion newQ = deltaQ.mul(this);

        w = newQ.w;
        x = newQ.x;
        y =  newQ.y;
        z = newQ.z;

        normalize();

    }

    float[] rotate(float x, float y, float z) {
         Quaternion pureQ = new Quaternion(0, x, y, z);
         pureQ = rotate(pureQ);
         return new float[]{pureQ.x, pureQ.y, pureQ.z, 0};
    }
    Quaternion rotate(Quaternion q) {

         return mul(q).mul(conjugate()); //inverse = conjugate for unit quaternions
    }

    Quaternion conjugate() {
         return new Quaternion(w, -x, -y, -z);
    }

    Quaternion mul(Quaternion q) {
        return new Quaternion(
            w*q.w - x*q.x - y*q.y - z*q.z,
            w*q.x + x*q.w + y*q.z - z*q.y,
            w*q.y - x*q.z + y*q.w + z*q.x,
            w*q.z + x*q.y - y*q.x + z*q.w
        );
    }

    float[] mul(float w2, float x2, float y2, float z2) {
        return new float[]{
            w*w2 - x*x2 - y*y2 - z*z2,
            w*x2 + x*w2 + y*z2 - z*y2,
            w*y2 - x*z2 + y*w2 + z*x2,
            w*z2 + x*y2 - y*x2 + z*w2
        };
    }

    void normalize() {
         double l = Math.sqrt(w*w+x*x+y*y+z*z);
         w /= (float) l;
         x /= (float) l;
         y /= (float) l;
         z /= (float) l;
    }

    float[] getRight() {
         return Matrix.getSlice(rotate(1, 0, 0), 0, 3);
    }

    float[] getUp() {
        return Matrix.getSlice(rotate(0, 1, 0), 0, 3);
    }
    float[] getForward() {
        return Matrix.getSlice(rotate(0, 0, -1), 0, 3);
    }

    public double angleAroundAxis(float ax, float ay, float az) {
        // Project vector part onto axis
        float dot = x * ax + y * ay + z * az;
        float vpx = dot * ax;
        float vpy = dot * ay;
        float vpz = dot * az;

        // Build twist quaternion
        float tw = w, tx = vpx, ty = vpy, tz = vpz;

        // Normalize it
        float len = (float) Math.sqrt(tw*tw + tx*tx + ty*ty + tz*tz);
        if (len < 1e-10f) return 0f;
        tw /= len; tx /= len; ty /= len; tz /= len;

        // Extract angle
        double angle = 2 * Math.atan2(Math.sqrt(tx*tx + ty*ty + tz*tz), tw);

        // Fix sign
        if (dot < 0) angle = -angle;

        double TWO_PI = 2* Math.PI;
        angle = ((angle % TWO_PI) + TWO_PI) % TWO_PI;

        return angle; // in radians
    }

    static float angleDelta(float a, float b) {
        float diff = b - a;
        // wrap to [-π, π]
        diff = (float) Math.atan2(Math.sin(diff), Math.cos(diff));
        return diff;
    }
}

