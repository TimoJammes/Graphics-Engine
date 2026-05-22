package com.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.Arrays;


public class Renderer {

    private static final RenderOptions DEFAULT_RENDER_OPTIONS = new RenderOptions();
    private static final float NEAR_PLANE_EPSILON = 0.01f;

//    private static final int MAX_VERTICES = 1_000_000;
//    private static final int MAX_TRIANGLES = MAX_VERTICES * 2;
    private static final int clipStride = 4; //stride of 4 to store w (4th coord) of vertices (clip w = -view z)

    private static final int vertexStride = 3;

    private final ShapeRenderer shapeRenderer;
    private final ScreenRenderer screenRenderer = new ScreenRenderer(Gdx.graphics.getWidth(),  Gdx.graphics.getHeight());

    //solidRender pre-alloc
    /**
     * stores clip-space vertices
     */
    private float[] clipVertexBuffer;// = new float[MAX_VERTICES * clipStride];
    /**
     * stores indices of non-culled triangles (each index points to the first index of a triangle in indices)
     */
    private int[] triangleOrder;// = new int[MAX_TRIANGLES];
    /**
     * stores clip-space triangle average z (used to sort positions)
     */
    float[] avgZBuffer;// = new float[MAX_TRIANGLES];
    /**
     * stores ordered indices of triangleOrder based on avgZBuffer value of the triangles
     */
    Integer[] positions;// = new Integer[MAX_TRIANGLES];
    /**
     * stores the
     */
    private int[] postClipIndices;// = new int[MAX_TRIANGLES * 2 * 3]; // * 2 for clipping, * 3 for 3 vertex indices
    private float[] screenVerticesBuffer;// = new float[MAX_TRIANGLES * 2 * 2 * 3]; //*2 for possible extra triangle from SH clipping, *2 for x and y, *3 for 3 vertices per triangle

    private int currentMaxVertices = 0;

    private int currentMaxVerticesWF = 0;
    private float[] clipVertexBufferWF;;
    private int[] visibleEdges;
    private int[] postClipEdges;
    private float[] screenVerticesBufferWF;

    Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
    }

    void renderScene(Scene scene, Camera camera) {

        float[][] V = camera.getViewMatrix();
        float[][] P = camera.getProjectionMatrix();

        float[][] VP = Matrix.matmul(P, V);

        ScreenUtils.clear(scene.backgroundColor);
//        screenRenderer.clear();

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

    /**
     * Re-allocates space in solidRender's buffers if current entity's vertexCount would overflow buffers.
     * @param vertexCount Current entity's vertex count.
     */
    private void ensureCapacity(int vertexCount) {
        if (vertexCount <= currentMaxVertices) return;
        currentMaxVertices = vertexCount;
        clipVertexBuffer = new float[currentMaxVertices * 5 * clipStride];
        triangleOrder = new int[currentMaxVertices * 2];
        avgZBuffer = new float[currentMaxVertices * 2];
        positions = new Integer[currentMaxVertices * 2];
        postClipIndices = new int[currentMaxVertices * 4 * 3];
        screenVerticesBuffer = new float[currentMaxVertices * 4 * 3 * 2];
    }

    private void ensureCapacityWF(int vertexCount) {
        if (vertexCount <= currentMaxVerticesWF) return;
        currentMaxVerticesWF = vertexCount;
        clipVertexBufferWF = new float[currentMaxVerticesWF * 5 * clipStride];
        visibleEdges = new int[currentMaxVerticesWF * 2 * 3];
        postClipEdges = new int[currentMaxVerticesWF * 4 * 3 * 2];
        screenVerticesBufferWF = new float[currentMaxVerticesWF * 4 * 3 * 2 * 2];
    }

    void solidRender(Entity entity, float[][] VP, RenderOptions options) {

        ensureCapacity(entity.mesh.vertices.length / vertexStride);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;


        computeClipVertices(vertices, MVP, clipVertexBuffer);

        int triangleCount = cullOuterTriangles(indices, clipVertexBuffer, triangleOrder);

        computeAvgClipZ(triangleCount, triangleOrder, indices, clipVertexBuffer, avgZBuffer);

        for (int i = 0; i < triangleCount; i++) positions[i] = i; //to sort triangles by avg z values

        Arrays.sort(positions, 0, triangleCount, (a, b) -> Float.compare(avgZBuffer[a], avgZBuffer[b]));

        int totalVertices = vertices.length / vertexStride;

        int postClipTriangleCount = 0;
        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleOrder[positions[i]];

            int idx1 = indices[idx];
            int idx2 = indices[idx + 1];
            int idx3 = indices[idx + 2];

            float w1 = clipVertexBuffer[idx1 * clipStride + 3];
            float w2 = clipVertexBuffer[idx2 * clipStride + 3];
            float w3 = clipVertexBuffer[idx3 * clipStride + 3];

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

                float t1 = clipVertexBuffer[f1 + 3] / (clipVertexBuffer[f1 + 3] - clipVertexBuffer[bh + 3]);
                float t2 = clipVertexBuffer[f2 + 3] / (clipVertexBuffer[f2 + 3] - clipVertexBuffer[bh + 3]);

                float intersectionX1 = clipVertexBuffer[f1] + t1 * (clipVertexBuffer[bh] - clipVertexBuffer[f1]);
                float intersectionY1 = clipVertexBuffer[f1 + 1] + t1 * (clipVertexBuffer[bh + 1] - clipVertexBuffer[f1 + 1]);
                float intersectionZ1 = clipVertexBuffer[f1 + 2] + t1 * (clipVertexBuffer[bh + 2] - clipVertexBuffer[f1 + 2]);

                float intersectionX2 = clipVertexBuffer[f2] + t2 * (clipVertexBuffer[bh] - clipVertexBuffer[f2]);
                float intersectionY2 = clipVertexBuffer[f2 + 1] + t2 * (clipVertexBuffer[bh + 1] - clipVertexBuffer[f2 + 1]);
                float intersectionZ2 = clipVertexBuffer[f2 + 2] + t2 * (clipVertexBuffer[bh + 2] - clipVertexBuffer[f2 + 2]);

                clipVertexBuffer[totalVertices * clipStride] = intersectionX1;
                clipVertexBuffer[totalVertices * clipStride + 1] = intersectionY1;
                clipVertexBuffer[totalVertices * clipStride + 2] = intersectionZ1;
                clipVertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3] = frontIdx1;
                postClipIndices[postClipTriangleCount * 3 + 1] = frontIdx2;
                postClipIndices[postClipTriangleCount * 3 + 2] = totalVertices;
                totalVertices++;
                postClipTriangleCount++;

                clipVertexBuffer[totalVertices * clipStride] = intersectionX2;
                clipVertexBuffer[totalVertices * clipStride + 1] = intersectionY2;
                clipVertexBuffer[totalVertices * clipStride + 2] = intersectionZ2;
                clipVertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;

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

                float t1 = clipVertexBuffer[f + 3] / (clipVertexBuffer[f + 3] - clipVertexBuffer[b1 + 3]);
                float t2 = clipVertexBuffer[f + 3] / (clipVertexBuffer[f + 3] - clipVertexBuffer[b2 + 3]);

                float intersectionX1 = clipVertexBuffer[f] + t1 * (clipVertexBuffer[b1] - clipVertexBuffer[f]);
                float intersectionY1 = clipVertexBuffer[f + 1] + t1 * (clipVertexBuffer[b1 + 1] - clipVertexBuffer[f + 1]);
                float intersectionZ1 = clipVertexBuffer[f + 2] + t1 * (clipVertexBuffer[b1 + 2] - clipVertexBuffer[f + 2]);

                float intersectionX2 = clipVertexBuffer[f] + t2 * (clipVertexBuffer[b2] - clipVertexBuffer[f]);
                float intersectionY2 = clipVertexBuffer[f + 1] + t2 * (clipVertexBuffer[b2 + 1] - clipVertexBuffer[f + 1]);
                float intersectionZ2 = clipVertexBuffer[f + 2] + t2 * (clipVertexBuffer[b2 + 2] - clipVertexBuffer[f + 2]);


                postClipIndices[postClipTriangleCount * 3] = frontIdx;
                clipVertexBuffer[totalVertices * clipStride] = intersectionX1;
                clipVertexBuffer[totalVertices * clipStride + 1] = intersectionY1;
                clipVertexBuffer[totalVertices * clipStride + 2] = intersectionZ1;
                clipVertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3 + 1] = totalVertices;
                totalVertices++;
                clipVertexBuffer[totalVertices * clipStride] = intersectionX2;
                clipVertexBuffer[totalVertices * clipStride + 1] = intersectionY2;
                clipVertexBuffer[totalVertices * clipStride + 2] = intersectionZ2;
                clipVertexBuffer[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipIndices[postClipTriangleCount * 3 + 2] = totalVertices;
                totalVertices++;
                postClipTriangleCount++;
            }
        }


        for (int i = 0; i < postClipTriangleCount; i++) {

            for (int j = 0; j < 3; j++) {
                int idx = postClipIndices[i * 3 + j];

                final float w = clipVertexBuffer[idx * clipStride + 3];
                final float ndcX = clipVertexBuffer[idx * clipStride] / w;
                final float ndcY = clipVertexBuffer[idx * clipStride + 1] / w;

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

            rasterizeTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
            drawTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
        }

        if (!options.showWireFrame)
            shapeRenderer.end();
    }

    private void rasterizeTriangle(RenderOptions options, float v1X, float v1Y, float v2X, float v2Y, float v3X, float v3Y) {

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
     * draws triangle connecting v1, v2, v3.
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

        ensureCapacityWF(entity.mesh.vertices.length / vertexStride);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] edges = entity.mesh.edges;


        computeClipVertices(vertices, MVP, clipVertexBufferWF);

        int edgeCount = cullOuterEdges(edges, clipVertexBufferWF, visibleEdges);

        int totalVertices = vertices.length / vertexStride;

        int postClipEdgeCount = 0;
        for (int i = 0; i < edgeCount; i++) {
            int idx = visibleEdges[i];

            int idx1 = edges[idx];
            int idx2 = edges[idx + 1];

            float w1 = clipVertexBufferWF[idx1 * clipStride + 3];
            float w2 = clipVertexBufferWF[idx2 * clipStride + 3];

            int behind = 0;
            if (w1 <= 0)
                behind++;
            if (w2 <= 0)
                behind++;

            //fully visible
            if (behind == 0) {
                postClipEdges[postClipEdgeCount * 2] = idx1;
                postClipEdges[postClipEdgeCount * 2 + 1] = idx2;
                postClipEdgeCount++;
            } else if (behind == 1) {

                int behindIdx, frontIdx;

                if (w1 <= 0) {
                    behindIdx = idx1;
                    frontIdx = idx2;
                } else {
                    behindIdx = idx2;
                    frontIdx = idx1;
                }

                int f = frontIdx * clipStride;
                int bh = behindIdx * clipStride;

                float t = clipVertexBufferWF[f + 3] / (clipVertexBufferWF[f + 3] - clipVertexBufferWF[bh + 3]);

                float intersectionX = clipVertexBufferWF[f] + t * (clipVertexBufferWF[bh] - clipVertexBufferWF[f]);
                float intersectionY = clipVertexBufferWF[f + 1] + t * (clipVertexBufferWF[bh + 1] - clipVertexBufferWF[f + 1]);
                float intersectionZ = clipVertexBufferWF[f + 2] + t * (clipVertexBufferWF[bh + 2] - clipVertexBufferWF[f + 2]);

                clipVertexBufferWF[totalVertices * clipStride] = intersectionX;
                clipVertexBufferWF[totalVertices * clipStride + 1] = intersectionY;
                clipVertexBufferWF[totalVertices * clipStride + 2] = intersectionZ;
                clipVertexBufferWF[totalVertices * clipStride + 3] = NEAR_PLANE_EPSILON;
                postClipEdges[postClipEdgeCount * 3] = frontIdx;
                postClipEdges[postClipEdgeCount * 3 + 1] = totalVertices;
                totalVertices++;
                postClipEdgeCount++;

            } else {
                throw new IllegalStateException();
            }
        }


        for (int i = 0; i < postClipEdgeCount; i++) {

            for (int j = 0; j < 2; j++) {
                int idx = postClipEdges[i * 2 + j];

                final float w = clipVertexBufferWF[idx * clipStride + 3];
                final float ndcX = clipVertexBufferWF[idx * clipStride] / w;
                final float ndcY = clipVertexBufferWF[idx * clipStride + 1] / w;

                final float screenX = (ndcX + 1) / 2 * Main.SCREEN_WIDTH;
                final float screenY = (ndcY + 1) / 2 * Main.SCREEN_HEIGHT;

                screenVerticesBufferWF[(i * 2 + j) * 2] = screenX;
                screenVerticesBufferWF[(i * 2 + j) * 2 + 1] = screenY;
            }
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(entity.color);

        for (int i = 0; i < postClipEdgeCount; i++) {

            float screenX1 = screenVerticesBufferWF[i * 2 * 2];
            float screenY1 = screenVerticesBufferWF[i * 2 * 2 + 1];
            float screenX2 = screenVerticesBufferWF[(i * 2 + 1) * 2];
            float screenY2 = screenVerticesBufferWF[(i * 2 + 1) * 2 + 1];


            shapeRenderer.line(
                screenX1, screenY1,
                screenX2, screenY2
            );
        }

        shapeRenderer.end();
    }

    private int cullOuterEdges(int[] edges, float[] clipVertices, int[] out) {
        int edgeCount = 0;
        for (int i = 0; i < edges.length; i += 2) {
            int idx1 = edges[i];
            int idx2 = edges[i + 1];

            float x1 = clipVertices[idx1 * clipStride], y1 = clipVertices[idx1 * clipStride + 1];
            float x2 = clipVertices[idx2 * clipStride], y2 = clipVertices[idx2 * clipStride + 1];
            float w1 = clipVertices[idx1 * clipStride + 3], w2 = clipVertices[idx2 * clipStride + 3];

            boolean cull =
                (w1 <= 0 && w2 <= 0) ||  // all behind near plane
                    (x1 > w1 && x2 > w2) ||  // all right of right plane
                    (x1 < -w1 && x2 < -w2) ||  // all left of left plane
                    (y1 > w1 && y2 > w2) ||  // all above top plane
                    (y1 < -w1 && y2 < -w2);    // all below bottom plane

            if (cull) continue;

            out[edgeCount++] = i;  // only add visible edges
        }
        return edgeCount;
    }

    void drawLine(int x0, int y0, int x1, int y1, float r, float g, float b) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;  // step direction in X
        int sy = y0 < y1 ? 1 : -1;  // step direction in Y
        int err = dx - dy;

        while (true) {
            screenRenderer.setPixel(x0, Gdx.graphics.getHeight()-y0, (byte)(r * 255), (byte)(g * 255), (byte)(b * 255));
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 <  dx) { err += dx; y0 += sy; }
        }
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
