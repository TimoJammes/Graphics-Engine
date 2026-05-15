package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.Arrays;


public class Renderer {

    private static final RenderOptions DEFAULT_RENDER_OPTIONS = new RenderOptions();
    private final ShapeRenderer shapeRenderer;

    private static final float NEAR_PLANE_EPSILON = 0.01f;

//    private static final int MAX_VERTICES = 1_000_000;
//    private static final int MAX_TRIANGLES = MAX_VERTICES * 2;
    private static final int clipStride = 4; //stride of 4 to store w (4th coord) of vertices (clip w = -view z)

    private static final int vertexStride = 3;

    //solidRender pre-alloc
    private float[] vertexBuffer;// = new float[MAX_VERTICES * clipStride];
    private int[] triangleOrder;// = new int[MAX_TRIANGLES];
    float[] avgZBuffer;// = new float[MAX_TRIANGLES];
    Integer[] positions;// = new Integer[MAX_TRIANGLES];
    private int[] postClipIndices;// = new int[MAX_TRIANGLES * 2 * 3]; // * 2 for clipping, * 3 for 3 vertex indices
    private float[] screenVerticesBuffer;// = new float[MAX_TRIANGLES * 2 * 2 * 3]; //*2 for possible extra triangle from SH clipping, *2 for x and y, *3 for 3 vertices per triangle

    private int currentMaxVertices = 0;

    Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
    }

    void renderScene(Scene scene, Camera camera) {

        float[][] V = camera.getViewMatrix();
        float[][] P = camera.getProjectionMatrix();

        float[][] VP = Matrix.matmul(P, V);

        ScreenUtils.clear(scene.backgroundColor);

        for (Entity entity : scene.entities) {
            RenderOptions options = scene.renderOptions.getOrDefault(entity, DEFAULT_RENDER_OPTIONS);

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

    private void ensureCapacity(int vertexCount) {
        if (vertexCount <= currentMaxVertices) return;
        currentMaxVertices = vertexCount;
        vertexBuffer = new float[currentMaxVertices * 5 * clipStride];
        triangleOrder = new int[currentMaxVertices * 2];
        avgZBuffer = new float[currentMaxVertices * 2];
        positions = new Integer[currentMaxVertices * 2];
        postClipIndices = new int[currentMaxVertices * 4 * 3];
        screenVerticesBuffer = new float[currentMaxVertices * 4 * 3 * 2];
    }

    void solidRender(Entity entity, float[][] VP, RenderOptions options) {

        ensureCapacity(entity.mesh.vertices.length / vertexStride);
//        System.out.println(currentMaxVertices);
        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;


        computeClipVertices(vertices, MVP, vertexBuffer);

        int triangleCount = cullOuterTriangles(indices, vertexBuffer, triangleOrder);

        computeAvgClipZ(triangleCount, triangleOrder, indices, vertexBuffer, avgZBuffer);

        for (int i = 0; i < triangleCount; i++) positions[i] = i; //to sort triangles by avg z values

        Arrays.sort(positions, 0, triangleCount, (a, b) -> Float.compare(avgZBuffer[a], avgZBuffer[b]));

        int totalVertices = vertices.length / vertexStride;

        int postClipTriangleCount = 0;
        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleOrder[positions[i]];

            int idx1 = indices[idx];
            int idx2 = indices[idx + 1];
            int idx3 = indices[idx + 2];

            float w1 = vertexBuffer[idx1 * clipStride + 3];
            float w2 = vertexBuffer[idx2 * clipStride + 3];
            float w3 = vertexBuffer[idx3 * clipStride + 3];

            int behind = 0;
            if (w1 <= 0)
                behind++;
            if (w2 <= 0)
                behind++;
            if (w3 <= 0)
                behind++;

            //fully visible
            if (behind == 0) {
                postClipIndices[postClipTriangleCount * 3] = idx1;
                postClipIndices[postClipTriangleCount * 3 + 1] = idx2;
                postClipIndices[postClipTriangleCount * 3 + 2] = idx3;
                postClipTriangleCount++;
            } else if (behind == 1) {

                int behindIdx, frontIdx1, frontIdx2;

                if (w1 <= 0) {
                    behindIdx = idx1;
                    frontIdx1 = idx2;
                    frontIdx2 = idx3;
                } else if (w2 <= 0) {
                    behindIdx = idx2;
                    frontIdx1 = idx1;
                    frontIdx2 = idx3;
                } else {
                    behindIdx = idx3;
                    frontIdx1 = idx1;
                    frontIdx2 = idx2;
                }

                int f1 = frontIdx1 * clipStride;
                int f2 = frontIdx2 * clipStride;
                int bh = behindIdx * clipStride;

                float t1 = vertexBuffer[f1 + 3] / (vertexBuffer[f1 + 3] - vertexBuffer[bh + 3]);
                float t2 = vertexBuffer[f2 + 3] / (vertexBuffer[f2 + 3] - vertexBuffer[bh + 3]);

                float intersectionX1 = vertexBuffer[f1] + t1 * (vertexBuffer[bh] - vertexBuffer[f1]);
                float intersectionY1 = vertexBuffer[f1 + 1] + t1 * (vertexBuffer[bh + 1] - vertexBuffer[f1 + 1]);
                float intersectionZ1 = vertexBuffer[f1 + 2] + t1 * (vertexBuffer[bh + 2] - vertexBuffer[f1 + 2]);

                float intersectionX2 = vertexBuffer[f2] + t2 * (vertexBuffer[bh] - vertexBuffer[f2]);
                float intersectionY2 = vertexBuffer[f2 + 1] + t2 * (vertexBuffer[bh + 1] - vertexBuffer[f2 + 1]);
                float intersectionZ2 = vertexBuffer[f2 + 2] + t2 * (vertexBuffer[bh + 2] - vertexBuffer[f2 + 2]);

                vertexBuffer[totalVertices * clipStride] = intersectionX1;
                vertexBuffer[totalVertices * clipStride + 1] = intersectionY1;
                vertexBuffer[totalVertices * clipStride + 2] = intersectionZ1;
                vertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3] = frontIdx1;
                postClipIndices[postClipTriangleCount * 3 + 1] = frontIdx2;
                postClipIndices[postClipTriangleCount * 3 + 2] = totalVertices;
                totalVertices++;
                postClipTriangleCount++;

                vertexBuffer[totalVertices * clipStride] = intersectionX2;
                vertexBuffer[totalVertices * clipStride + 1] = intersectionY2;
                vertexBuffer[totalVertices * clipStride + 2] = intersectionZ2;
                vertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;

                postClipIndices[postClipTriangleCount * 3] = frontIdx2;
                postClipIndices[postClipTriangleCount * 3 + 1] = totalVertices - 1;
                postClipIndices[postClipTriangleCount * 3 + 2] = totalVertices;
                totalVertices++;
                postClipTriangleCount++;

            } else if (behind == 2) {

                int frontIdx, behindIdx1, behindIdx2;

                if (w1 > 0) {
                    frontIdx = idx1;
                    behindIdx1 = idx2;
                    behindIdx2 = idx3;
                } else if (w2 > 0) {
                    frontIdx = idx2;
                    behindIdx1 = idx1;
                    behindIdx2 = idx3;
                } else {
                    frontIdx = idx3;
                    behindIdx1 = idx1;
                    behindIdx2 = idx2;
                }

                int f = frontIdx * clipStride;
                int b1 = behindIdx1 * clipStride;
                int b2 = behindIdx2 * clipStride;

                float t1 = vertexBuffer[f + 3] / (vertexBuffer[f + 3] - vertexBuffer[b1 + 3]);
                float t2 = vertexBuffer[f + 3] / (vertexBuffer[f + 3] - vertexBuffer[b2 + 3]);

                float intersectionX1 = vertexBuffer[f] + t1 * (vertexBuffer[b1] - vertexBuffer[f]);
                float intersectionY1 = vertexBuffer[f + 1] + t1 * (vertexBuffer[b1 + 1] - vertexBuffer[f + 1]);
                float intersectionZ1 = vertexBuffer[f + 2] + t1 * (vertexBuffer[b1 + 2] - vertexBuffer[f + 2]);

                float intersectionX2 = vertexBuffer[f] + t2 * (vertexBuffer[b2] - vertexBuffer[f]);
                float intersectionY2 = vertexBuffer[f + 1] + t2 * (vertexBuffer[b2 + 1] - vertexBuffer[f + 1]);
                float intersectionZ2 = vertexBuffer[f + 2] + t2 * (vertexBuffer[b2 + 2] - vertexBuffer[f + 2]);


                postClipIndices[postClipTriangleCount * 3] = frontIdx;
                vertexBuffer[totalVertices * clipStride] = intersectionX1;
                vertexBuffer[totalVertices * clipStride + 1] = intersectionY1;
                vertexBuffer[totalVertices * clipStride + 2] = intersectionZ1;
                vertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3 + 1] = totalVertices;
                totalVertices++;
                vertexBuffer[totalVertices * clipStride] = intersectionX2;
                vertexBuffer[totalVertices * clipStride + 1] = intersectionY2;
                vertexBuffer[totalVertices * clipStride + 2] = intersectionZ2;
                vertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3 + 2] = totalVertices;
                totalVertices++;
                postClipTriangleCount++;
            }
        }


        for (int i = 0; i < postClipTriangleCount; i++) {

            for (int j = 0; j < 3; j++) {
                int idx = postClipIndices[i * 3 + j];

                final float w = vertexBuffer[idx * clipStride + 3];
                final float ndcX = vertexBuffer[idx * clipStride] / w;
                final float ndcY = vertexBuffer[idx * clipStride + 1] / w;

                final float screenX = (ndcX + 1) / 2 * Main.SCREEN_WIDTH;
                final float screenY = (ndcY + 1) / 2 * Main.SCREEN_HEIGHT;

                screenVerticesBuffer[(i * 3 + j) * 2] = screenX;
                screenVerticesBuffer[(i * 3 + j) * 2 + 1] = screenY;
            }
        }
        if (!options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);


        for (int i = 0; i < postClipTriangleCount; i++) {

            float screenX1 = screenVerticesBuffer[i * 3 * 2];
            float screenY1 = screenVerticesBuffer[i * 3 * 2 + 1];
            float screenX2 = screenVerticesBuffer[(i * 3 + 1) * 2];
            float screenY2 = screenVerticesBuffer[(i * 3 + 1) * 2 + 1];
            float screenX3 = screenVerticesBuffer[(i * 3 + 2) * 2];
            float screenY3 = screenVerticesBuffer[(i * 3 + 2) * 2 + 1];

            setRenderColor(entity, options, i * 3, i * 3 + 1, i * 3 + 2); //pass indices[index] for randomizeTexture

            //draws 1 triangle (first triplet of vertices) or 2 if SH-clipping with 2 vertices behind camera (second triplet of vertices)
            drawTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
        }

        if (!options.showWireFrame)
            shapeRenderer.end();
    }

    private void setRenderColor(Entity entity, RenderOptions options, int idx1, int idx2, int idx3) {
        if (!options.randomizeTexture) {
            shapeRenderer.setColor(entity.color);
            return;
        }

        // deterministic pseudo-random hash from all 3 vertex indices
        int hash = (idx1 * 92837111) ^ (idx2 * 689287499) ^ (idx3 * 283823481);
        float offset = ((hash & 0xFF) / 255f - 0.5f) * 0.12f; // range [-0.06, 0.06]

        shapeRenderer.setColor(
            Math.clamp(entity.color.r + offset, 0, 1),
            Math.clamp(entity.color.g + offset, 0, 1),
            Math.clamp(entity.color.b + offset, 0, 1),
            1f
        );
    }

    /**
     * draws triangle connecting v1, v2, v3 by default.
     * if 2 vertices were behind camera, 2 triangles drawn through Sutherland-Hodgman clipping wrt near-plane.
     */
    private void drawTriangle(RenderOptions options, float v1X, float v1Y, float v2X, float v2Y, float v3X, float v3Y) {

        if (options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.triangle(
            v1X, v1Y,
            v2X, v2Y,
            v3X, v3Y
        );

        if (options.showWireFrame) {
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.line(
                v1X, v1Y,
                v2X, v2Y
            );
            shapeRenderer.line(
                v1X, v1Y,
                v3X, v3Y
            );
            shapeRenderer.line(
                v2X, v2Y,
                v3X, v3Y
            );
            shapeRenderer.end();
        }
    }

    /**
     * computes average z value for triangles
     *
     * @param triangleCount number of triangles (indices in triangleOrder)
     * @param triangleOrder contains index of first triangle vertex index in indices
     * @param indices       contains indices (idx1, idx2, idx3) of vertices of each triangle
     * @param clipVertices  contains clip-space vertex coordinates
     * @param out           array to store avg z values in
     */
    private void computeAvgClipZ(int triangleCount, int[] triangleOrder, int[] indices, float[] clipVertices, float[] out) {
        for (int i = 0; i < triangleCount; i++) {
            out[i] = avgZ(triangleOrder[i], indices, clipVertices);
        }
    }

    /**
     * culls triangles (from vertices in clipVertices) behind camera or completely out of frustum &
     * stores triangle indices (first idx of triangle vertex index in indices) in triangleOrder
     *
     * @param indices
     * @param clipVertices
     * @param out          array to store triangle indices to (index in indices of first index of vertex of each triangle)
     * @return number of triangles after cull (# indices stored in triangleOrder)
     */
    private int cullOuterTriangles(int[] indices, float[] clipVertices, int[] out) {
        int triangleCount = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int idx1 = indices[i];
            int idx2 = indices[i + 1];
            int idx3 = indices[i + 2];

            float x1 = clipVertices[idx1 * clipStride], y1 = clipVertices[idx1 * clipStride + 1];
            float x2 = clipVertices[idx2 * clipStride], y2 = clipVertices[idx2 * clipStride + 1];
            float x3 = clipVertices[idx3 * clipStride], y3 = clipVertices[idx3 * clipStride + 1];
            float w1 = clipVertices[idx1 * clipStride + 3], w2 = clipVertices[idx2 * clipStride + 3], w3 = clipVertices[idx3 * clipStride + 3];


//            if (w1 <= 0 && w2 <= 0 && w3 <= 0) continue;
////            if (w1 <= 0 || w2 <= 0 || w3 <= 0) continue;
//
//            // basic NDC bounds check
//            if (w1 > 0 && w2 > 0 && w3 > 0 && !inFrustum(idx1, clipVertices) && !inFrustum(idx2, clipVertices) && !inFrustum(idx3, clipVertices)) continue;

            boolean cull =
                (w1 <= 0 && w2 <= 0 && w3 <= 0) ||  // all behind near plane
                    (x1 > w1 && x2 > w2 && x3 > w3) ||  // all right of right plane
                    (x1 < -w1 && x2 < -w2 && x3 < -w3) ||  // all left of left plane
                    (y1 > w1 && y2 > w2 && y3 > w3) ||  // all above top plane
                    (y1 < -w1 && y2 < -w2 && y3 < -w3);    // all below bottom plane

            if (cull) continue;

            out[triangleCount++] = i;  // only add visible triangles
        }
        return triangleCount;
    }

    /**
     * compute clip-space vertex coords.
     *
     * @param vertices: entity model-space coords (x, y, z)
     * @param MVP:      model-view-projection matrix
     * @param out:      store computed vertices here (x, y, z, w)
     */
    private void computeClipVertices(float[] vertices, float[][] MVP, float[] out) {
        for (int i = 0; i < vertices.length / vertexStride; i++) {

            float x = vertices[i * vertexStride];
            float y = vertices[i * vertexStride + 1];
            float z = vertices[i * vertexStride + 2];

            //to avoid float[] creation through Matrix.matmul
            directMatmul(out, i * clipStride, MVP, x, y, z, 1);
        }
    }

    /**
     * Computes matrix @ [x, y, z, w] and stores the resulting vector in storageArray, starting at startIdx
     *
     * @param storageArray array to store matmul result in
     * @param startIdx     index to start storing matmul result at
     * @param matrix       LHS operator of matmul (4x4 matrix)
     * @param x            1st coord of RHS operator (vector) of matmul
     * @param y            2nd coord of RHS operator (vector) of matmul
     * @param z            3rd coord of RHS operator (vector) of matmul
     * @param w            4th coord of RHS operator (vector) of matmul
     */
    private void directMatmul(float[] storageArray, int startIdx, float[][] matrix, float x, float y, float z, float w) {

        assert matrix.length == 4 && matrix[0].length == 4;

//        if (matrix.length != 4 || matrix[0].length != 4)
//            throw new IllegalArgumentException("directMatmul only computes @ of 4x4 matrix & vector4");

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

    final float avgZ(int index, int[] indices, float[] clipVertices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * clipStride + 3];
        z2 = -clipVertices[(indices[index + 1]) * clipStride + 3];
        z3 = -clipVertices[(indices[index + 2]) * clipStride + 3];

        return (z1 + z2 + z3) / 3f;
    }
}
