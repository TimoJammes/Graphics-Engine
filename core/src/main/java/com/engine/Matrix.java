package com.engine;

//import java.util.Arrays;

public class Matrix {

    /**
     * 0-indexed.
     */

    final float[][] array;
    final int rows;
    final int cols;

    Matrix(float[] array) {
        this.array = new float[array.length][1];

        for (int i = 0; i < array.length; i++) {
            this.array[i][0] = array[i];
        }
        rows = array.length;
        cols = 1;
    }

    Matrix(float[][] array) {
        this.array = array;
        rows = array.length;
        cols = array[0].length;
    }

    float get(int i, int j) {
        return array[i][j];
    }

    void set(int i, int j, float val) {
        array[i][j] = val;
    }

    Matrix add(Matrix m2) {
        if (cols != m2.cols || rows != m2.rows)
            throw new IllegalArgumentException("Attempted to add matrices with different shapes!");

        float[][] newArray = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                newArray[i][j] = array[i][j] + m2.array[i][j];
            }
        }

        Matrix res = new Matrix(newArray);

        return res;
    }

    Matrix matmul(Matrix m2) {

        if (cols != m2.rows) throw new IllegalArgumentException("#cols of M1 must be equal to #rows of M2");

        int newRows = rows;
        int newCols = m2.cols;

        final float[][] newArray = new float[newRows][newCols];

        for (int i = 0; i < newRows; i++) {

            for (int j = 0; j < newCols; j++) {

                for (int k = 0; k < cols; k++) {


                    newArray[i][j] += array[i][k] * m2.array[k][j];
                }
            }
        }

        Matrix res = new Matrix(newArray);

        if (res.rows == 4 && res.cols == 1) {
            return new Vector4(res.col(0).oneDimArray);
        }

        return res;
    }

    static float normSqr(Matrix m) {
        float sum = 0;

        for (int i = 0; i < m.rows; i++) {
            for (int j = 0; j < m.cols; j++) {
                sum += m.array[i][j] * m.array[i][j];
            }
        }
        return sum;
    }

    Vector col(int j) {
        final float[] array = new float[rows];

        for (int i = 0; i < rows; i++) {
            array[i] = get(i, j);
        }

        return new Vector(array);
    }

    Vector row(int i) {
        final float[] array = new float[cols];

        for (int j = 0; j < cols; j++) {
            array[j] = get(i, j);
        }

        return new Vector(array);
    }

    Matrix T() {
        final float[][] array = new float[cols][rows];

        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                array[i][j] = get(j, i);
            }
        }
        return new Matrix(array);
    }

    Matrix negate() {
        final float[][] array = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                array[i][j] = -this.array[i][j];
            }
        }

        return new Matrix(array);
    }

    Matrix sub(Matrix m2) {
        final float[][] array = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                array[i][j] = this.array[i][j] - m2.array[i][j];
            }
        }

        return new Matrix(array);

    }

    int[] shape() {
        return new int[]{rows, cols};
    }

    void print() {
        for (float[] row : array) {
            for (float val : row) {
                System.out.printf("%4f", val);
            }
            System.out.println();
        }
    }
}


class Vector extends Matrix {

    final float[] oneDimArray;

    Vector(float[] array) {
        super(array);
        oneDimArray = array;
    }

    Vector(float[] array, int length) {
        if (array.length != length)
            throw new IllegalArgumentException("Tried creating Vector" + length + " from array.length != " + length + "!");
        this(array);

    }

    final float dot(Vector v2) {
        if (rows != v2.rows) throw new IllegalArgumentException("Tried dotting two vectors of different dimensions!");

        float dot = 0;
        for (int i = 0; i < rows; i++) {
            dot += get(i) * v2.get(i);
        }

        return dot;
    }


    Vector slice(int a, int b) {
        float[] array = new float[b - a];

        for (int i = a; i < b; i++) {
            array[i - a] = get(i);
        }

        if (b - a == 2) return new Vector2(array);
        if (b - a == 3) return new Vector3(array);
        if (b - a == 4) return new Vector4(array);

        return new Vector(array);
    }

    final float get(int i) {

        return get(i, 0);
    }

    void set(int i, float val) {
        oneDimArray[i] = val;

        super.set(i, 0, val);
    }


    @Override
    float get(int i, int j) {
        if (j != 0) throw new UnsupportedOperationException("Tried to get in col > 1 in Vector" + rows + "!");

        return super.get(i, j);
    }
}

class Vector2 extends Vector {
    Vector2(float[] array) {
        super(array, 2);
    }

    Vector2(float x, float y) {
        this(new float[]{x, y});
    }
}

class Vector3 extends Vector {

    Vector3(float[] array) {

        super(array, 3);
    }

    Vector3(float x, float y, float z) {

        this(new float[]{x, y, z});
    }

    @Override
    Vector3 negate() {
        return new Vector3(-get(0), -get(1), -get(2));
    }

    float getX() {return oneDimArray[0];}
    float getY() {return oneDimArray[1];}
    float getZ() {return oneDimArray[2];}
}

class Vector4 extends Vector {

    Vector4(float[] array) {

        super(array, 4);
    }

    Vector4(float x, float y, float z, float w) {
        this(new float[]{x, y, z, w});
    }

    Vector4 add(Vector4 m2) {


        float[] newArray = new float[rows];
        for (int i = 0; i < rows; i++) {
            newArray[i] = array[i][0] + m2.array[i][0];

        }

        return new Vector4(newArray);
    }

    void setX(float x) {set(0, x);}
    void setY(float y) {set(1, y);}
    void setZ(float z) {set(2, z);}
    void setW(float w) {set(3, w);}

    float getX() {return oneDimArray[0];}
    float getY() {return oneDimArray[1];}
    float getZ() {return oneDimArray[2];}
    float getW() {return oneDimArray[3];}
}
