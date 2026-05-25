package com.engine;

public final class Matrix {

    private Matrix() {}

    static float[] mul(float[] m, float x) {
        float[] res = new float[m.length];

        for(int i = 0; i < m.length; i++) {
            res[i] = m[i] * x;
        }
        return res;
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        };
    }

    static float[] normalize(float[] v) {
        float len = (float)Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        return new float[]{v[0]/len, v[1]/len, v[2]/len};
    }

    static float[][] add(float[][] m1, float[][] m2) {
        float[][] res = new float[m1.length][m1[0].length];
        for (int i = 0; i < m1.length; i++) {
            for (int j = 0; j < m1[0].length; j++) {
                res[i][j] = m1[i][j] +  m2[i][j];
            }
        }
        return res;
    }

    static float[] add(float[] m1, float[] m2) {
        float[] res = new float[m1.length];
        for (int i = 0; i < m1.length; i++) {
            res[i] = m1[i] +  m2[i];
        }
        return res;
    }
    static float[][] sub(float[][] m1, float[][] m2) {
        final float[][] res = new float[m1.length][m1[0].length];
        for (int i = 0; i < m1.length; i++) {
            for (int j = 0; j < m1[0].length; j++) {
                res[i][j] = m1[i][j] - m2[i][j];
            }
        }
        return res;
    }
    static float[] sub(float[] m1, float[] m2) {
        final float[] res = new float[m1.length];
        for (int i = 0; i < m1.length; i++) {
                res[i] = m1[i] - m2[i];
        }
        return res;
    }
    static float[][] negate(float[][] m) {
        final float[][] res = new float[m.length][m[0].length];
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                res[i][j] = -m[i][j];
            }
        }
        return res;
    }
    static float[] negate(float[] v) {
        final float[] res = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            res[i] = -v[i];
        }
        return res;
    }

    static float dot(float[] v1, float[] v2) {
        float dot = 0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }
    static float[][] matmul(float[][] m1, float[][] m2) {

        if (m1[0].length != m2.length) throw new IllegalArgumentException("#cols of M1 must be equal to #rows of M2");

        int newRows = m1.length;
        int newCols = m2[0].length;

        final float[][] res = new float[newRows][newCols];

        for (int i = 0; i < newRows; i++) {

            for (int j = 0; j < newCols; j++) {

                for (int k = 0; k < m1[0].length; k++) {


                    res[i][j] += m1[i][k] * m2[k][j];
                }
            }
        }

        return res;
    }

    static float[] matmul(float[][] m1, float[] m2) {

        if (m1[0].length != m2.length) throw new IllegalArgumentException("#cols of M1 must be equal to #rows of M2");

        int newRows = m1.length;

        final float[] res = new float[newRows];

        for (int i = 0; i < newRows; i++) {
            for (int k = 0; k < m1[0].length; k++) {
                res[i] += m1[i][k] * m2[k];
            }
        }

        return res;
    }


    static float[][] transpose(float[][] m) {
        final float[][] res = new float[m[0].length][m.length];

        for (int i = 0; i < m[0].length; i++) {
            for (int j = 0; j < m.length; j++) {
                res[i][j] = m[j][i];
            }
        }
        return res;
    }
    static float normSqr(float[][] m) {
        float sum = 0;

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                sum += m[i][j] * m[i][j];
            }
        }
        return sum;
    }
    static float normSqr(float[] m) {
        float sum = 0;

        for (int i = 0; i < m.length; i++) {
                sum += m[i] * m[i];
        }
        return sum;
    }

    static float getX(float[] v) {
        return v[0];
    }
    static float getY(float[] v) {
        if (v.length < 2)
            throw new IllegalArgumentException("Must be a >1-dim vector to get w!");
        return v[1];
    }
    static float getZ(float[] v) {
        if (v.length < 3)
            throw new IllegalArgumentException("Must be a >2-dim vector to get w!");
        return v[2];
    }
    static float getW(float[] v) {
        if (v.length < 4)
            throw new IllegalArgumentException("Must be a >3-dim vector to get w!");
        return v[3];
    }

    static void setX(float[] v, float val) {
        v[0] = val;
    }
    static void setY(float[] v, float val) {
        if (v.length < 2)
            throw new IllegalArgumentException("Must be a >1-dim vector to get w!");
        v[1] = val;
    }
    static void setZ(float[] v, float val) {
        if (v.length < 3)
            throw new IllegalArgumentException("Must be a >2-dim vector to get w!");
        v[2] = val;
    }
    static void setW(float[] v, float val) {
        if (v.length < 4)
            throw new IllegalArgumentException("Must be a >3-dim vector to get w!");
        v[3] = val;
    }

    static float[] getSlice(float[] v, int a, int b) {
        float[] res = new float[b - a];

        for (int i = a; i < b; i++) {
            res[i - a] = v[i];
        }
        return res;
    }
    static float[] getCol(float[][] m, int j) {
        final float[] res = new float[m.length];

        for (int i = 0; i < m.length; i++) {
            res[i] = m[i][j];
        }

        return res;
    }

    static float[] getRow(float[][] m, int i) {
        final float[] res = new float[m[0].length];

        for (int j = 0; j < m[0].length; j++) {
            res[j] = m[i][j];
        }

        return res;
    }

    static int[] shape(float[][] m) {
        return new int[]{m.length, m[0].length};
    }

    static void print(float[][] m) {
        for (float[] row : m) {
            for (float val : row) {
                System.out.printf("%4f", val);
            }
            System.out.println();
        }
    }
}
