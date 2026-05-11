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

     Matrix toMatrix() {
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

        return new Matrix(m);
    }

    void rotate(double theta, float ax, float ay, float az) {
        double l = Math.sqrt(ax*ax + ay*ay + az*az);
        ax /= (float) l;
        ay /= (float) l;
        az /= (float) l;

        float deltaW = (float) Math.cos(theta/2);
        float deltaX = (float) (Math.sin(theta/2) * ax);
        float deltaY = (float) (Math.sin(theta/2) * ay);
        float deltaZ = (float) (Math.sin(theta/2) * az);

        float[] newQCoords = mul(deltaW, deltaX, deltaY, deltaZ);

        w = newQCoords[0];
        x = newQCoords[1];
        y = newQCoords[2];
        z = newQCoords[3];

        normalize();

    }

    Vector4 rotate(float x, float y, float z) {
         Quaternion pureQ = new Quaternion(0, x, y, z);
         pureQ = rotate(pureQ);
         return new Vector4(pureQ.x, pureQ.y, pureQ.z, 0);
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

    Vector3 getRight() {
         return (Vector3) rotate(1, 0 ,0).slice(0, 3);
    }

    Vector3 getUp() {
         return  (Vector3) rotate(0, 1 ,0).slice(0, 3);
    }
    Vector3 getForward() {
         return  (Vector3) rotate(0, 0 ,-1).slice(0, 3);
    }
}

