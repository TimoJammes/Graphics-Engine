package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.Arrays;

public class SolidRenderer {


    private static final int[][] planes = {{2, 1}, {2, -1}, {0, 1}, {0, -1}, {1, 1}, {1, -1}};

    private final ScreenRenderer screenRenderer;
    private final ShapeRenderer shapeRenderer;
    /**
     * stores clip-space vertices
     */
    private float[] clipVertexBuffer;// = new float[MAX_VERTICES * Renderer.clipStride];
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
    private int[] screenVerticesBuffer;// = new float[MAX_TRIANGLES * 2 * 2 * 3]; //*2 for possible extra triangle from SH clipping, *2 for x and y, *3 for 3 vertices per triangle

    private int currentMaxVertices = 0;

    private int totalVertices;

    //    private final int[] clipResult = new int[4];
//    private boolean triangleDiscarded;
    int[] polyIn = new int[9];
    int[] polyOut = new int[9];

    SolidRenderer(ScreenRenderer screenRenderer) {
        this.screenRenderer = screenRenderer;
        shapeRenderer = null;
    }

    SolidRenderer(ShapeRenderer shapeRenderer) {
        screenRenderer = null;
        this.shapeRenderer = shapeRenderer;
    }

    /**
     * Re-allocates space in solidRender's buffers if current entity's vertexCount would overflow buffers.
     *
     * @param vertexCount Current entity's vertex count.
     */
    private void ensureCapacity(int vertexCount) {
        if (vertexCount <= currentMaxVertices) return;
        currentMaxVertices = vertexCount;
        clipVertexBuffer = new float[currentMaxVertices * 5 * Renderer.CLIP_STRIDE];
        triangleOrder = new int[currentMaxVertices * 2];
        avgZBuffer = new float[currentMaxVertices * 2];
        positions = new Integer[currentMaxVertices * 2];
        postClipIndices = new int[currentMaxVertices * 4 * 3];
        screenVerticesBuffer = new int[currentMaxVertices * 4 * 3 * 2];
    }

    void render(Entity entity, float[][] VP, RenderOptions options) {

        ensureCapacity(entity.mesh.vertices.length / Renderer.VERTEX_STRIDE);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;

        totalVertices = vertices.length / Renderer.VERTEX_STRIDE;

        Renderer.computeClipVertices(vertices, MVP, clipVertexBuffer);
        int triangleCount = cullOutsideTriangles(indices, clipVertexBuffer, triangleOrder);
        zSortTriangles(triangleCount, indices, positions);
        int postClipTriangleCount = SHClipTriangles(triangleCount, indices, postClipIndices);

        //check clipping worked (all clip vertices in frustum)
//        for (int i =0; i < postClipTriangleCount; i++) {
//            int idx = postClipIndices[i];
//
//            float x = clipVertexBuffer[idx * Renderer.CLIP_STRIDE];
//            float y = clipVertexBuffer[idx * Renderer.CLIP_STRIDE + 1];
//            float z = clipVertexBuffer[idx * Renderer.CLIP_STRIDE + 2];
//            float w = clipVertexBuffer[idx * Renderer.CLIP_STRIDE + 3];
//
//            assert x >= -w - 1e-4f && x <= w + 1e-4f : "x outside frustum: x=" + x + " w=" + w + " idx=" + idx;
//            assert y >= -w - 1e-4f && y <= w + 1e-4f : "y outside frustum: y=" + y + " w=" + w + " idx=" + idx;
//            assert z >= -w - 1e-4f && z <= w + 1e-4f : "z outside frustum: z=" + z + " w=" + w + " idx=" + idx;
//        }

        computeScreenVertices(postClipTriangleCount, screenVerticesBuffer);

        if (!options.showWireFrame) shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        displayTriangles(options, entity.color, postClipTriangleCount);
        if (!options.showWireFrame) shapeRenderer.end();
    }

    private void displayTriangles(RenderOptions options, Color color, int postClipTriangleCount) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            float screenX1 = screenVerticesBuffer[i * 3 * 2];
            float screenY1 = screenVerticesBuffer[i * 3 * 2 + 1];
            float screenX2 = screenVerticesBuffer[(i * 3 + 1) * 2];
            float screenY2 = screenVerticesBuffer[(i * 3 + 1) * 2 + 1];
            float screenX3 = screenVerticesBuffer[(i * 3 + 2) * 2];
            float screenY3 = screenVerticesBuffer[(i * 3 + 2) * 2 + 1];

            setRenderColor(options, color, i * 3, i * 3 + 1, i * 3 + 2); //pass indices[index] for randomizeTexture

//            rasterizeTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
//            if (!(screenX1 >= 0 && screenX1 <= Main.SCREEN_WIDTH && screenY1 >= 0 && screenY1 <= Main.SCREEN_HEIGHT && screenX2 >= 0 && screenX2 <= Main.SCREEN_WIDTH && screenY2 >= 0 && screenY2 <= Main.SCREEN_HEIGHT && screenX3 >= 0 && screenX3 <= Main.SCREEN_WIDTH && screenY3 >= 0 && screenY3 <= Main.SCREEN_HEIGHT)) {
//                assert false;
//            }
            drawTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
        }
    }

    private void computeScreenVertices(int postClipTriangleCount, int[] out) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            for (int j = 0; j < 3; j++) {
                int idx = postClipIndices[i * 3 + j];

                final float w = clipVertexBuffer[idx * Renderer.CLIP_STRIDE + 3];
                final float ndcX = Math.clamp(clipVertexBuffer[idx * Renderer.CLIP_STRIDE] / w, -1, 1);
                final float ndcY = Math.clamp(clipVertexBuffer[idx * Renderer.CLIP_STRIDE + 1] / w, -1, 1);

                //TODO -1 for screenRenderer (buffer writing)
                final float screenX = (ndcX + 1) / 2 * (Main.SCREEN_WIDTH - 0);
                final float screenY = (ndcY + 1) / 2 * (Main.SCREEN_HEIGHT - 0);

                out[(i * 3 + j) * 2] = (int) screenX;
                out[(i * 3 + j) * 2 + 1] = (int) screenY;
            }
        }
    }

    private int SHClipTriangles(int triangleCount, int[] indices, int[] out) {
        int postClipTriangleCount = 0;

        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleOrder[positions[i]];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            int polyVertexCount = clipTriangleAllPlanes(a, b, c);
            if (polyVertexCount == 0) continue;  // fully outside

            for (int j = 1; j < polyVertexCount - 1; j++) {
                out[postClipTriangleCount * 3] = polyIn[0];
                out[postClipTriangleCount * 3 + 1] = polyIn[j];
                out[postClipTriangleCount * 3 + 2] = polyIn[j+1];
                postClipTriangleCount++;

            }
        }
        return postClipTriangleCount;
    }

    private int clipTriangleAllPlanes(int idx1, int idx2, int idx3) {

        polyIn[0] = idx1;
        polyIn[1] = idx2;
        polyIn[2] = idx3;
        int inCount = 3;

        for (int i = 0; i < planes.length; i++) {

            int[] plane = planes[i];
            int outCount = SHClipPoly(polyIn, inCount, plane[0], plane[1], polyOut);

            if (outCount == 0) return 0;  // discarded

            int[] tmp = polyIn;
            polyIn = polyOut;
            polyOut = tmp;
            inCount = outCount;

//            SHClipTriangle(clipResult[0], clipResult[1], clipResult[2], plane[0], plane[1]);
        }

        return inCount;
    }

    private int SHClipPoly(int[] polyIn, int inCount, int component, int sign, int[] polyOut) {
        int outCount = 0;

        for (int i = 0; i < inCount; i++) {
            int edgeIdx1 = polyIn[i];
            int edgeIdx2 = polyIn[(i + 1) % inCount];

            float v1 = clipVertexBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + component];
            float v2 = clipVertexBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + component];
            float w1 = clipVertexBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 3];
            float w2 = clipVertexBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + 3];

            boolean v1Outside = sign * v1 < -w1;
            boolean v2Outside = sign * v2 < -w2;

            boolean bothOutside = v1Outside && v2Outside;
            boolean bothInside = !v1Outside && !v2Outside;
            boolean firstInside = !v1Outside && v2Outside;
            boolean secondInside = v1Outside && !v2Outside;

            if (bothOutside) {
            } else if (bothInside) {
                polyOut[outCount++] = edgeIdx2;
            } else {
                //compute intersection point
                int i1 = edgeIdx1 * Renderer.CLIP_STRIDE;
                int i2 = edgeIdx2 * Renderer.CLIP_STRIDE;

                float d1 = clipVertexBuffer[i1 + 3] + sign * clipVertexBuffer[i1 + component];
                float d2 = clipVertexBuffer[i2 + 3] + sign * clipVertexBuffer[i2 + component];

                float t1 = d1 / (d1 - d2);

                clipVertexBuffer[totalVertices * Renderer.CLIP_STRIDE] = clipVertexBuffer[i1] + t1 * (clipVertexBuffer[i2] - clipVertexBuffer[i1]);
                clipVertexBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = clipVertexBuffer[i1 + 1] + t1 * (clipVertexBuffer[i2 + 1] - clipVertexBuffer[i1 + 1]);
                clipVertexBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = clipVertexBuffer[i1 + 2] + t1 * (clipVertexBuffer[i2 + 2] - clipVertexBuffer[i1 + 2]);
                clipVertexBuffer[totalVertices * Renderer.CLIP_STRIDE + 3] = clipVertexBuffer[i1 + 3] + t1 * (clipVertexBuffer[i2 + 3] - clipVertexBuffer[i1 + 3]);

                if (firstInside) {
                    polyOut[outCount++] = totalVertices;
                }
                if (secondInside) {
                    polyOut[outCount++] = totalVertices;
                    polyOut[outCount++] = edgeIdx2;
                }

                totalVertices++;
            }
        }

        return outCount;
    }

    private void zSortTriangles(int triangleCount, int[] indices, Integer[] out) {
        computeAvgClipZ(triangleCount, triangleOrder, indices, clipVertexBuffer, avgZBuffer);

        for (int i = 0; i < triangleCount; i++) out[i] = i; //to sort triangles by avg z values

        Arrays.sort(out, 0, triangleCount, (a, b) -> Float.compare(avgZBuffer[a], avgZBuffer[b]));
    }

    private void rasterizeTriangle(RenderOptions options, float v1X, float v1Y, float v2X, float v2Y, float v3X, float v3Y) {

    }

    private void setRenderColor(RenderOptions options, Color color, int idx1, int idx2, int idx3) {
        if (!options.randomizeTexture) {
            shapeRenderer.setColor(color);
            return;
        }

        // deterministic pseudo-random hash from all 3 vertex indices
        int hash = (idx1 * 92837111) ^ (idx2 * 689287499) ^ (idx3 * 283823481);
        float offset = ((hash & 0xFF) / 255f - 0.5f) * 0.12f; // range [-0.06, 0.06]

        shapeRenderer.setColor(Math.clamp(color.r + offset, 0, 1), Math.clamp(color.g + offset, 0, 1), Math.clamp(color.b + offset, 0, 1), 1f);
    }

    /**
     * draws triangle connecting v1, v2, v3.
     */
    private void drawTriangle(RenderOptions options, float v1X, float v1Y, float v2X, float v2Y, float v3X, float v3Y) {

        if (options.showWireFrame) shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.triangle(v1X, v1Y, v2X, v2Y, v3X, v3Y);

        if (options.showWireFrame) {
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.line(v1X, v1Y, v2X, v2Y);
            shapeRenderer.line(v1X, v1Y, v3X, v3Y);
            shapeRenderer.line(v2X, v2Y, v3X, v3Y);
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
    private int cullOutsideTriangles(int[] indices, float[] clipVertices, int[] out) {
        int triangleCount = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int idx1 = indices[i];
            int idx2 = indices[i + 1];
            int idx3 = indices[i + 2];

            float x1 = clipVertices[idx1 * Renderer.CLIP_STRIDE];
            float x2 = clipVertices[idx2 * Renderer.CLIP_STRIDE];
            float x3 = clipVertices[idx3 * Renderer.CLIP_STRIDE];

            float y1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 1];
            float y2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 1];
            float y3 = clipVertices[idx3 * Renderer.CLIP_STRIDE + 1];

            float z1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 2];
            float z2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 2];
            float z3 = clipVertices[idx3 * Renderer.CLIP_STRIDE + 2];

            float w1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 3];
            float w2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 3];
            float w3 = clipVertices[idx3 * Renderer.CLIP_STRIDE + 3];

            boolean cull = (z1 < -w1 && z2 < -w2 && z3 < -w3) ||  // all behind near plane
                (z1 > w1 && z2 > w2 && z3 > w3) ||  // all further than far plane
                (x1 > w1 && x2 > w2 && x3 > w3) ||  // all right of right plane
                (x1 < -w1 && x2 < -w2 && x3 < -w3) ||  // all left of left plane
                (y1 > w1 && y2 > w2 && y3 > w3) ||  // all above top plane
                (y1 < -w1 && y2 < -w2 && y3 < -w3);    // all below bottom plane

            if (cull) continue;

            out[triangleCount++] = i;  // only add visible triangles
        }
        return triangleCount;
    }

    final float avgZ(int index, int[] indices, float[] clipVertices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * Renderer.CLIP_STRIDE + 3];
        z2 = -clipVertices[(indices[index + 1]) * Renderer.CLIP_STRIDE + 3];
        z3 = -clipVertices[(indices[index + 2]) * Renderer.CLIP_STRIDE + 3];

        return (z1 + z2 + z3) / 3f;
    }
}
