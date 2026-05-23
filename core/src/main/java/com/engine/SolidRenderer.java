package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.Arrays;

public class SolidRenderer {


    private final ScreenRenderer screenRenderer;
    private final ShapeRenderer shapeRenderer;
    /**
     * stores clip-space vertices
     */
    private float[] clipBuffer;// = new float[MAX_VERTICES * Renderer.clipStride];
    /**
     * stores indices of non-culled triangles (each index points to the first index of a triangle in indices)
     */
    private int[] triangleBuffer;// = new int[MAX_TRIANGLES];
    /**
     * stores clip-space triangle average z (used to sort positions)
     */
    float[] avgZBuffer;// = new float[MAX_TRIANGLES];
    /**
     * stores ordered indices of triangleOrder based on avgZBuffer value of the triangles
     */
    Integer[] triangleOrderBuffer;// = new Integer[MAX_TRIANGLES];
    /**
     * stores the
     */
    private int[] postClipTriangleIndicesBuffer;// = new int[MAX_TRIANGLES * 2 * 3]; // * 2 for clipping, * 3 for 3 vertex indices
    private int[] screenBuffer;// = new float[MAX_TRIANGLES * 2 * 2 * 3]; //*2 for possible extra triangle from SH clipping, *2 for x and y, *3 for 3 vertices per triangle

    private int currentMaxVertices = 0;

    private int totalVertices;

    int[] polyIn = new int[9];
    int[] polyOut = new int[9];

    private final Color scratchColor = new Color();

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
        clipBuffer = new float[currentMaxVertices * 5 * Renderer.CLIP_STRIDE];
        triangleBuffer = new int[currentMaxVertices * 2];
        avgZBuffer = new float[currentMaxVertices * 2];
        triangleOrderBuffer = new Integer[currentMaxVertices * 2];
        postClipTriangleIndicesBuffer = new int[currentMaxVertices * 4 * 3];
        screenBuffer = new int[currentMaxVertices * 4 * 3 * 2];
    }

    void render(Entity entity, float[][] VP, RenderOptions options) {

        ensureCapacity(entity.mesh.vertices.length / Renderer.VERTEX_STRIDE);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;

        totalVertices = vertices.length / Renderer.VERTEX_STRIDE;

        Renderer.computeClipVertices(vertices, MVP, clipBuffer);
        int triangleCount = cullOutsideTriangles(indices, clipBuffer, triangleBuffer);
        zSortTriangles(triangleCount, indices, triangleOrderBuffer);
        int postClipTriangleCount = SHClipTriangles(triangleCount, indices, postClipTriangleIndicesBuffer);

        //check clipping worked (all clip vertices in frustum)
        for (int i = 0; i < postClipTriangleCount * 3; i++) {
            int idx = postClipTriangleIndicesBuffer[i];

            float x = clipBuffer[idx * Renderer.CLIP_STRIDE];
            float y = clipBuffer[idx * Renderer.CLIP_STRIDE + 1];
            float z = clipBuffer[idx * Renderer.CLIP_STRIDE + 2];
            float w = clipBuffer[idx * Renderer.CLIP_STRIDE + 3];

            assert x >= -w - 1e-4f && x <= w + 1e-4f : "x outside frustum: x=" + x + " w=" + w + " idx=" + idx;
            assert y >= -w - 1e-4f && y <= w + 1e-4f : "y outside frustum: y=" + y + " w=" + w + " idx=" + idx;
            assert z >= -w - 1e-4f && z <= w + 1e-4f : "z outside frustum: z=" + z + " w=" + w + " idx=" + idx;
        }

        computeScreenVertices(postClipTriangleCount, screenBuffer);

//        if (!options.showWireFrame) shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        displayTriangles(options, entity.color, postClipTriangleCount);
//        if (!options.showWireFrame) shapeRenderer.end();
    }

    private void displayTriangles(RenderOptions options, Color baseColor, int postClipTriangleCount) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            int screenX1 = screenBuffer[i * 3 * 2];
            int screenY1 = screenBuffer[i * 3 * 2 + 1];
            int screenX2 = screenBuffer[(i * 3 + 1) * 2];
            int screenY2 = screenBuffer[(i * 3 + 1) * 2 + 1];
            int screenX3 = screenBuffer[(i * 3 + 2) * 2];
            int screenY3 = screenBuffer[(i * 3 + 2) * 2 + 1];

//            setRenderColor(options, color, i * 3, i * 3 + 1, i * 3 + 2); //pass indices[index] for randomizeTexture
            Color color = getRenderColor(options, baseColor, i * 3, i * 3 + 1, i * 3 + 2);
            rasterizeTriangle(options, color, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
            assert (screenX1 >= 0 && screenX1 <= Main.SCREEN_WIDTH &&
                screenY1 >= 0 && screenY1 <= Main.SCREEN_HEIGHT &&
                screenX2 >= 0 && screenX2 <= Main.SCREEN_WIDTH &&
                screenY2 >= 0 && screenY2 <= Main.SCREEN_HEIGHT &&
                screenX3 >= 0 && screenX3 <= Main.SCREEN_WIDTH &&
                screenY3 >= 0 && screenY3 <= Main.SCREEN_HEIGHT): "illegal screen pos";
//            drawTriangle(options, screenX1, screenY1, screenX2, screenY2, screenX3, screenY3);
        }
    }

    private void computeScreenVertices(int postClipTriangleCount, int[] out) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            for (int j = 0; j < 3; j++) {
                int idx = postClipTriangleIndicesBuffer[i * 3 + j];

                final float w = clipBuffer[idx * Renderer.CLIP_STRIDE + 3];
                final float ndcX = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE] / w, -1, 1);
                final float ndcY = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE + 1] / w, -1, 1);

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
            int idx = triangleBuffer[triangleOrderBuffer[i]];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            int polyVertexCount = clipTriangleAllPlanes(a, b, c);
            if (polyVertexCount == 0) continue;  // fully outside

            for (int j = 1; j < polyVertexCount - 1; j++) {
                out[postClipTriangleCount * 3] = polyIn[0];
                out[postClipTriangleCount * 3 + 1] = polyIn[j];
                out[postClipTriangleCount * 3 + 2] = polyIn[j + 1];
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

        for (int i = 0; i < Renderer.CLIP_PLANES.length; i++) {

            int[] plane = Renderer.CLIP_PLANES[i];
            int outCount = SHClipPoly(polyIn, inCount, plane[0], plane[1], polyOut);

            if (outCount == 0) return 0;  // discarded

            int[] tmp = polyIn;
            polyIn = polyOut;
            polyOut = tmp;
            inCount = outCount;
        }

        return inCount;
    }

    private int SHClipPoly(int[] polyIn, int inCount, int component, int sign, int[] polyOut) {
        int outCount = 0;

        for (int i = 0; i < inCount; i++) {
            int edgeIdx1 = polyIn[i];
            int edgeIdx2 = polyIn[(i + 1) % inCount];

            float v1 = clipBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + component];
            float v2 = clipBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + component];
            float w1 = clipBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 3];
            float w2 = clipBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + 3];

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

                float d1 = clipBuffer[i1 + 3] + sign * clipBuffer[i1 + component];
                float d2 = clipBuffer[i2 + 3] + sign * clipBuffer[i2 + component];

                float t1 = d1 / (d1 - d2);

                clipBuffer[totalVertices * Renderer.CLIP_STRIDE] = clipBuffer[i1] + t1 * (clipBuffer[i2] - clipBuffer[i1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = clipBuffer[i1 + 1] + t1 * (clipBuffer[i2 + 1] - clipBuffer[i1 + 1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = clipBuffer[i1 + 2] + t1 * (clipBuffer[i2 + 2] - clipBuffer[i1 + 2]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 3] = clipBuffer[i1 + 3] + t1 * (clipBuffer[i2 + 3] - clipBuffer[i1 + 3]);

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
        computeAvgClipZ(triangleCount, triangleBuffer, indices, clipBuffer, avgZBuffer);

        for (int i = 0; i < triangleCount; i++) out[i] = i; //to sort triangles by avg z values

        Arrays.sort(out, 0, triangleCount, (a, b) -> Float.compare(avgZBuffer[a], avgZBuffer[b]));
    }

    private void rasterizeTriangle(RenderOptions options, Color color, int v1X, int v1Y, int v2X, int v2Y, int v3X, int v3Y) {

        //a top, b middle, c bottom vertex
        int aX = v1X, aY = v1Y;
        int bX = v2X, bY = v2Y;
        int cX = v3X, cY = v3Y;
        int tX, tY;

        if (aY > bY) {
            tX = aX;
            tY = aY;
            aX = bX;
            aY = bY;
            bX = tX;
            bY = tY;
        }
        if (aY > cY) {
            tX = aX;
            tY = aY;
            aX = cX;
            aY = cY;
            cX = tX;
            cY = tY;
        }
        if (bY > cY) {
            tX = bX;
            tY = bY;
            bX = cX;
            bY = cY;
            cX = tX;
            cY = tY;
        }

        if (cY == aY) return;

        float t = (float) (bY - aY) / (cY - aY);
        //interpolated x value for triangle separation between top and bottom
        int midX = (int) (aX + t * (cX - aX));


        /* here we know that aY <= bY <= cY */
        /* check for trivial case of bottom-flat triangle */
        if (bY == cY) {
            fillBottomFlatTriangle(color, aX, aY, bX, bY, cX, cY);
        }
        /* check for trivial case of top-flat triangle */
        else if (aY == bY) {
            fillTopFlatTriangle(color, aX, aY, bX, bY, cX, cY);
        } else {
            /* general case - split the triangle in a topflat and bottom-flat one */
            fillBottomFlatTriangle(color, aX, aY, bX, bY, midX, bY);
            fillTopFlatTriangle(color, bX, bY, midX, bY, cX, cY);
        }
    }

    private void fillBottomFlatTriangle(Color color, int v1X, int v1Y, int v2X, int v2Y, int v3X, int v3Y) {
        float invslope1 = (float) (v2X - v1X) / (v2Y - v1Y);
        float invslope2 = (float) (v3X - v1X) / (v3Y - v1Y);

        float curx1 = v1X;
        float curx2 = v1X;

        for (int scanlineY = v1Y; scanlineY <= v2Y; scanlineY++) {
            drawHorizLine(color, (int) curx1, (int) curx2, scanlineY);
            curx1 += invslope1;
            curx2 += invslope2;
        }
    }

    private void fillTopFlatTriangle(Color color, int v1X, int v1Y, int v2X, int v2Y, int v3X, int v3Y) {
        float invslope1 = (float) (v3X - v1X) / (v3Y - v1Y);
        float invslope2 = (float) (v3X - v2X) / (v3Y - v2Y);

        float curx1 = v3X;
        float curx2 = v3X;

        for (int scanlineY = v3Y; scanlineY > v1Y; scanlineY--) {
            drawHorizLine(color, (int) curx1, (int) curx2, scanlineY);
            curx1 -= invslope1;
            curx2 -= invslope2;
        }
    }

    private void drawHorizLine(Color color, int x1, int x2, int y) {
        int xStart = Math.min(x1, x2);
        int xEnd = Math.max(x1, x2);
        for (int x = xStart; x <= xEnd; x++) {
            screenRenderer.setPixel(x, Main.SCREEN_HEIGHT - y - 1, color);
        }
    }

    private Color getRenderColor(RenderOptions options, Color color, int idx1, int idx2, int idx3) {
        if (!options.randomizeTexture) {
            return color;
        }

        // deterministic pseudo-random hash from all 3 vertex indices
        int hash = (idx1 * 92837111) ^ (idx2 * 689287499) ^ (idx3 * 283823481);
        float offset = ((hash & 0xFF) / 255f - 0.5f) * 0.12f; // range [-0.06, 0.06]

        scratchColor.set(Math.clamp(color.r + offset, 0, 1), Math.clamp(color.g + offset, 0, 1), Math.clamp(color.b + offset, 0, 1), 255f);
        return scratchColor;
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

    private final float avgZ(int index, int[] indices, float[] clipVertices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * Renderer.CLIP_STRIDE + 3];
        z2 = -clipVertices[(indices[index + 1]) * Renderer.CLIP_STRIDE + 3];
        z3 = -clipVertices[(indices[index + 2]) * Renderer.CLIP_STRIDE + 3];

        return (z1 + z2 + z3) / 3f;
    }
}
