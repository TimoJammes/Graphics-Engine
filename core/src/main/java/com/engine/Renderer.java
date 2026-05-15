package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.Arrays;


public class Renderer {

    private final ShapeRenderer shapeRenderer;

    private static final float NEAR_PLANE_EPSILON = 0.01f;

    private static final int MAX_VERTICES = 1_000_000;
    private static final int clipStride = 4; //stride of 4 to store w (4th coord) of vertices (clip w = -view z)

    private static final int vertexStride = 3;

    //solidRender pre-alloc
    private final float[] clipVertices = new float[MAX_VERTICES * clipStride];
    private final int[] triangleOrder = new int[MAX_VERTICES * 2];
    float[] avgZValues = new float[triangleOrder.length];
    Integer[] positions = new Integer[triangleOrder.length];

    Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
    }

    void renderScene(Scene scene, Camera camera) {

        float[][] V = camera.getViewMatrix();
        float[][] P = camera.getProjectionMatrix();

        float[][] VP = Matrix.matmul(P, V);

        ScreenUtils.clear(scene.backgroundColor);

        for (Entity entity : scene.entities) {
            RenderOptions options = scene.renderOptions.getOrDefault(entity, new RenderOptions());

            switch (options.renderMode) {
                case Scene.RenderMode.WIRE_FRAME:
                    wireFrameRender(entity, VP);
                    break;
                case Scene.RenderMode.SOLID:
                    solidRender(entity, VP, options);
                    break;
                default:
                    throw new IllegalStateException("Unsupported render mode");

            }

        }
    }

    void solidRender(Entity entity, float[][] VP, RenderOptions options) {

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;

        computeClipVertices(vertices, MVP); //stores to clipVertices array

        int triangleCount = cullOuterTriangles(indices);

        computeAvgClipZ(triangleCount, indices); //stores to avgZValues

        for (int i = 0; i < triangleCount; i++) positions[i] = i; //to sort triangles by avgZValues

        Arrays.sort(positions, 0, triangleCount, (a, b) -> Float.compare(avgZValues[a], avgZValues[b]));


        if (!options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < triangleCount; i++) {
            int index = triangleOrder[positions[i]];


            float[] clipV1 = getClipVertex(indices[index]);
            float[] clipV2 = getClipVertex(indices[index + 1]);
            float[] clipV3 = getClipVertex(indices[index + 2]);


            //if 1 vertex behind camera, new triangle created with vertices screenFrontV2, screenIntersection1, screenIntersection2
            //if 2 vertices behind camera, these are modified in place to become intersection vertices
            float[][] shClipVertices = shNearPlaneClip(clipV1, clipV2, clipV3);

            float[] shClipT1V1 = shClipVertices[0];
            float[] shClipT1V2 = shClipVertices[1];
            float[] shClipT1V3 = shClipVertices[2];

            float[] shClipT2V1 = shClipVertices[3];
            float[] shClipT2V2 = shClipVertices[4];
            float[] shClipT2V3 = shClipVertices[5];

            float[] screenT1V1 = clipToScreen(shClipT1V1);
            float[] screenT1V2 = clipToScreen(shClipT1V2);
            float[] screenT1V3 = clipToScreen(shClipT1V3);

            float[] screenT2V1 = shClipT2V1 == null ? null : clipToScreen(shClipT2V1);
            float[] screenT2V2 = shClipT2V1 == null ? null : clipToScreen(shClipT2V2);
            float[] screenT2V3 = shClipT2V1 == null ? null : clipToScreen(shClipT2V3);

            setRenderColor(entity, options, indices[index]); //pass indices[index] for randomizeTexture

            //draws 1 triangle (first triplet of vertices) or 2 if SH-clipping with 2 vertices behind camera (second triplet of vertices)
            drawTriangles(options, screenT1V1, screenT1V2, screenT1V3, screenT2V1, screenT2V2, screenT2V3);
        }

        if (!options.showWireFrame)
            shapeRenderer.end();
    }

    private void setRenderColor(Entity entity, RenderOptions options, int idx) {
        Color color = entity.color;
        if (options.randomizeTexture) {
            float[] offsets = {0f, 0.03f, -0.03f, 0.06f, -0.06f};
            float offset = offsets[idx % offsets.length];

            color = new Color(
                Math.clamp(entity.color.r + offset, 0, 1),
                Math.clamp(entity.color.g + offset, 0, 1),
                Math.clamp(entity.color.b + offset, 0, 1),
                1f
            );
        }

        shapeRenderer.setColor(color);
    }

    /**
     * draws triangle connecting v1, v2, v3 by default.
     * if 2 vertices were behind camera, 2 triangles drawn through Sutherland-Hodgman clipping wrt near-plane.
     */
    private void drawTriangles(RenderOptions options, float[] v1, float[] v2, float[] v3, float[] frontV2, float[] intersecV1, float[] intersecV2) {

        if (options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.triangle(
            Matrix.getX(v1), Matrix.getY(v1),
            Matrix.getX(v2), Matrix.getY(v2),
            Matrix.getX(v3), Matrix.getY(v3)
        );

        if (frontV2 != null) //same as checking if intersectionV2 isnt null
            shapeRenderer.triangle(
                Matrix.getX(frontV2), Matrix.getY(frontV2),
                Matrix.getX(intersecV1), Matrix.getY(intersecV1),
                Matrix.getX(intersecV2), Matrix.getY(intersecV2)
            );


        if (options.showWireFrame) {
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.line(
                Matrix.getX(v1), Matrix.getY(v1),
                Matrix.getX(v2), Matrix.getY(v2)
            );
            shapeRenderer.line(
                Matrix.getX(v1), Matrix.getY(v1),
                Matrix.getX(v3), Matrix.getY(v3)
            );
            shapeRenderer.line(
                Matrix.getX(v2), Matrix.getY(v2),
                Matrix.getX(v3), Matrix.getY(v3)
            );
            if (frontV2 != null) {
                shapeRenderer.line(
                    Matrix.getX(frontV2), Matrix.getY(frontV2),
                    Matrix.getX(intersecV1), Matrix.getY(intersecV1)
                );
                shapeRenderer.line(
                    Matrix.getX(frontV2), Matrix.getY(frontV2),
                    Matrix.getX(intersecV2), Matrix.getY(intersecV2)
                );
                shapeRenderer.line(
                    Matrix.getX(intersecV1), Matrix.getY(intersecV1),
                    Matrix.getX(intersecV2), Matrix.getY(intersecV2)
                );
            }
            shapeRenderer.end();
        }
    }

    private float[][] shNearPlaneClip(float[] clipV1, float[] clipV2, float[] clipV3) {
        int negWCount = 0;

        if (Matrix.getW(clipV1) <= 0) negWCount++;
        if (Matrix.getW(clipV2) <= 0) negWCount++;
        if (Matrix.getW(clipV3) <= 0) negWCount++;

        if (negWCount == 1) {
            return shCullingOneBehind(clipV1, clipV2, clipV3); //assigns to frontV2, intersectionV1 & intersectionV2 directly
        }

        if (negWCount == 2) {
            return shCullingTwoBehind(clipV1, clipV2, clipV3);
        }

        if(negWCount == 3) {
            throw new IllegalStateException();
        }

        return new float[][]{clipV1, clipV2, clipV3, null, null, null};
    }

    private float[] getClipVertex(int idx) {
        return new float[]{
            clipVertices[idx * clipStride],
            clipVertices[idx * clipStride + 1],
            clipVertices[idx * clipStride + 2],
            clipVertices[idx * clipStride + 3]};
    }

    private void computeAvgClipZ(int triangleCount, int[] indices) {
        for (int i = 0; i < triangleCount; i++) {
            avgZValues[i] = avgZ(triangleOrder[i], indices);
        }
    }

    /**
     * culls triangles (from vertices in clipVertices) behind camera or out of frustum &
     * stores triangle indices (first idx of triangle vertex index in indices) in triangleOrder
     * @param indices
     * @return number of triangles after cull (# indices stored in triangleOrder)
     */
    private int cullOuterTriangles(int[] indices) {
        int triangleCount = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int idx1 = indices[i];
            int idx2 = indices[i + 1];
            int idx3 = indices[i + 2];

            float w1 = clipVertices[idx1 * 4 + 3];
            float w2 = clipVertices[idx2 * 4 + 3];
            float w3 = clipVertices[idx3 * 4 + 3];

            // behind camera check
            if (w1 <= 0 && w2 <= 0 && w3 <= 0) continue;
//            if (w1 <= 0 || w2 <= 0 || w3 <= 0) continue;

            // basic NDC bounds check
            if (w1 > 0 && w2 > 0 && w3 > 0 && !inFrustum(idx1) && !inFrustum(idx2) && !inFrustum(idx3)) continue;
//            if (!inFrustum(idx1) || !inFrustum(idx2) || !inFrustum(idx3)) continue;

            triangleOrder[triangleCount++] = i;  // only add visible triangles
        }
        return triangleCount;
    }

    private void computeClipVertices(float[] vertices, float[][] MVP) {
        for (int i = 0; i < vertices.length / vertexStride; i++) {

            float x = vertices[i * vertexStride];
            float y = vertices[i * vertexStride + 1];
            float z = vertices[i * vertexStride + 2];

            //to avoid float[] creation through Matrix.matmul
            directMatmul(clipVertices, i * clipStride, MVP, x, y, z, 1);
        }
    }

    /**
     * Computes matrix @ [x, y, z, w] and stores the resulting vector in storageArray, starting at startIdx
     * @param storageArray array to store matmul result in
     * @param startIdx index to start storing matmul result at
     * @param matrix LHS operator of matmul (4x4 matrix)
     * @param x 1st coord of RHS operator (vector) of matmul
     * @param y 2nd coord of RHS operator (vector) of matmul
     * @param z 3rd coord of RHS operator (vector) of matmul
     * @param w 4th coord of RHS operator (vector) of matmul
     */
    private void directMatmul(float[] storageArray, int startIdx, float[][] matrix, float x, float y, float z, float w) {

        if (matrix.length != 4 || matrix[0].length != 4)
            throw new IllegalArgumentException("directMatmul only computes @ of 4x4 matrix & vector4");

        float resX = 0;
        float resY = 0;
        float resZ = 0;
        float resW = 0;

        resX += matrix[0][0] * x;
        resX += matrix[0][1] * y;
        resX += matrix[0][2] * z;
        resX += matrix[0][3] * w;

        resY += matrix[1][0] * x;
        resY += matrix[1][1] * y;
        resY += matrix[1][2] * z;
        resY += matrix[1][3] * w;

        resZ += matrix[2][0] * x;
        resZ += matrix[2][1] * y;
        resZ += matrix[2][2] * z;
        resZ += matrix[2][3] * w;

        resW += matrix[3][0] * x;
        resW += matrix[3][1] * y;
        resW += matrix[3][2] * z;
        resW += matrix[3][3] * w;

        storageArray[startIdx] = resX;
        storageArray[startIdx + 1] = resY;
        storageArray[startIdx + 2] = resZ;
        storageArray[startIdx + 3] = resW;
    }
    /**
     *
     * @param v1
     * @param v2
     * @param v3
     * @return the vertices of the two clipped triangles
     */
    private float[][] shCullingOneBehind(float[] v1, float[] v2, float[] v3) {
        float[] behindV, frontV1, frontV2;

        if (Matrix.getW(v1) <= 0) {
            behindV = v1;
            frontV1 = v2;
            frontV2 = v3;
        } else if (Matrix.getW(v2) <= 0) {
            behindV = v2;
            frontV1 = v1;
            frontV2 = v3;
        } else {
            behindV = v3;
            frontV1 = v1;
            frontV2 = v2;
        }

        float t1 = Matrix.getW(frontV1) / (Matrix.getW(frontV1) - Matrix.getW(behindV));
        float t2 = Matrix.getW(frontV2) / (Matrix.getW(frontV2) - Matrix.getW(behindV));

        float intersectionX1 = Matrix.getX(frontV1) + t1 * (Matrix.getX(behindV) - Matrix.getX(frontV1));
        float intersectionY1 = Matrix.getY(frontV1) + t1 * (Matrix.getY(behindV) - Matrix.getY(frontV1));
        float intersectionZ1 = Matrix.getZ(frontV1) + t1 * (Matrix.getZ(behindV) - Matrix.getZ(frontV1));

        float intersectionX2 = Matrix.getX(frontV2) + t2 * (Matrix.getX(behindV) - Matrix.getX(frontV2));
        float intersectionY2 = Matrix.getY(frontV2) + t2 * (Matrix.getY(behindV) - Matrix.getY(frontV2));
        float intersectionZ2 = Matrix.getZ(frontV2) + t2 * (Matrix.getZ(behindV) - Matrix.getZ(frontV2));

        float[] intersectionV1 = new float[]{intersectionX1, intersectionY1, intersectionZ1, NEAR_PLANE_EPSILON};
        float[] intersectionV2 = new float[]{intersectionX2, intersectionY2, intersectionZ2, NEAR_PLANE_EPSILON};

        return new float[][]{
            frontV1, frontV2, intersectionV1,
            frontV2, intersectionV1, intersectionV2
        };
    }

    /**
     *
     * @param v1
     * @param v2
     * @param v3
     * @return the vertices of the clipped triangle (unchanged front vertex, intersection V1 & V2) and 3x null (no new triangle)
     */
    private static float[][] shCullingTwoBehind(float[] v1, float[] v2, float[] v3) {
        float[] frontV, behindV1, behindV2;

        if (Matrix.getW(v1) > 0) {
            frontV = v1;
            behindV1 = v2;
            behindV2 = v3;
        } else if (Matrix.getW(v2) > 0) {
            frontV = v2;
            behindV1 = v1;
            behindV2 = v3;
        } else {
            frontV = v3;
            behindV1 = v1;
            behindV2 = v2;
        }

        float t1 = Matrix.getW(frontV) / (Matrix.getW(frontV) - Matrix.getW(behindV1));
        float t2 = Matrix.getW(frontV) / (Matrix.getW(frontV) - Matrix.getW(behindV2));

        float intersectionX1 = Matrix.getX(frontV) + t1 * (Matrix.getX(behindV1) - Matrix.getX(frontV));
        float intersectionY1 = Matrix.getY(frontV) + t1 * (Matrix.getY(behindV1) - Matrix.getY(frontV));
        float intersectionZ1 = Matrix.getZ(frontV) + t1 * (Matrix.getZ(behindV1) - Matrix.getZ(frontV));

        float intersectionX2 = Matrix.getX(frontV) + t2 * (Matrix.getX(behindV2) - Matrix.getX(frontV));
        float intersectionY2 = Matrix.getY(frontV) + t2 * (Matrix.getY(behindV2) - Matrix.getY(frontV));
        float intersectionZ2 = Matrix.getZ(frontV) + t2 * (Matrix.getZ(behindV2) - Matrix.getZ(frontV));

        float[] intersectionV1 = new float[]{intersectionX1, intersectionY1, intersectionZ1, NEAR_PLANE_EPSILON};
        float[] intersectionV2 = new float[]{intersectionX2, intersectionY2, intersectionZ2, NEAR_PLANE_EPSILON};

        return new float[][]{
            frontV, intersectionV1, intersectionV2,
            null, null, null
        };
    }

    boolean inFrustum(int idx) {
        float x = clipVertices[idx * clipStride];
        float y = clipVertices[idx * clipStride + 1];
        float w = clipVertices[idx * clipStride + 3];

        return Math.abs(x) <= w && Math.abs(y) <= w;
    }


    void wireFrameRender(Entity entity, float[][] VP) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(entity.color);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix());

        final float[] vertices = entity.mesh.vertices;

        final int[] edges = entity.mesh.edges;


        for (int i = 0; i < edges.length; i += 2) {
            int idx1 = edges[i];
            int idx2 = edges[i + 1];

            float x1 = vertices[idx1 * vertexStride];
            float y1 = vertices[idx1 * vertexStride + 1];
            float z1 = vertices[idx1 * vertexStride + 2];

            float x2 = vertices[idx2 * vertexStride];
            float y2 = vertices[idx2 * vertexStride + 1];
            float z2 = vertices[idx2 * vertexStride + 2];

            float[] clipSpaceV1 = Matrix.matmul(MVP, new float[]{x1, y1, z1, 1});
            float[] clipSpaceV2 = Matrix.matmul(MVP, new float[]{x2, y2, z2, 1});

            if (Matrix.getW(clipSpaceV1) <= 0 && Matrix.getW(clipSpaceV2) <= 0) continue;

            float[] ndcSpaceV1 = clipToNdc(clipSpaceV1);
            float[] ndcSpaceV2 = clipToNdc(clipSpaceV2);


            if (!inNDC(ndcSpaceV1) && !inNDC(ndcSpaceV2)) continue;

            float[] screenV1 = ndcToScreen(ndcSpaceV1);
            float[] screenV2 = ndcToScreen(ndcSpaceV2);

            shapeRenderer.line(
                Matrix.getX(screenV1), Matrix.getY(screenV1),
                Matrix.getX(screenV2), Matrix.getY(screenV2)
            );
        }

        shapeRenderer.end();
    }

    boolean inNDC(float[] v) {
        return Matrix.getX(v) >= -1 && Matrix.getX(v) <= 1 && Matrix.getY(v) >= -1 && Matrix.getY(v) <= 1;
    }

    private float[] clipToScreen(float[] v) {
        final float w = Matrix.getW(v);
        final float ndcX = Matrix.getX(v) / w;
        final float ndcY = Matrix.getY(v) / w;
//        final float ndcZ = Matrix.getZ(v) / w;

        final float screenX = (ndcX + 1) / 2 * Main.SCREEN_WIDTH;
        final float screenY = (ndcY + 1) / 2 * Main.SCREEN_HEIGHT;

        return new float[]{screenX, screenY};

    }
    final float[] clipToNdc(float[] clipSpaceV) {
        final float w = Matrix.getW(clipSpaceV);
        final float ndcX = Matrix.getX(clipSpaceV) / w;
        final float ndcY = Matrix.getY(clipSpaceV) / w;
        final float ndcZ = Matrix.getZ(clipSpaceV) / w;

        return new float[]{ndcX, ndcY, ndcZ};
    }

    final float[] ndcToScreen(float[] ndcSpaceV) {
        final float screenX = (Matrix.getX(ndcSpaceV) + 1) / 2 * Main.SCREEN_WIDTH;
        final float screenY = (Matrix.getY(ndcSpaceV) + 1) / 2 * Main.SCREEN_HEIGHT;

        return new float[]{screenX, screenY};
    }

    final float avgZ(int index, int[] indices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * clipStride + 3];
        z2 = -clipVertices[(indices[index + 1]) * clipStride + 3];
        z3 = -clipVertices[(indices[index + 2]) * clipStride + 3];

        return (z1 + z2 + z3) / 3f;
    }
}
