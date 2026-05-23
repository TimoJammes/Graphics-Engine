package com.engine;

import com.badlogic.gdx.graphics.Color;

public class SolidRenderer {


    private static final float WIRE_FRAME_DEPTH_EPSILON = 1e-3f;
    private final FrameBuffer frameBuffer;
    /**
     * stores clip-space vertices
     */
    private float[] clipBuffer;
    /**
     * stores indices of non-culled triangles (each index points to the first index of a triangle in indices)
     */
    private int[] triangleBuffer;
    /**
     * stores the
     */
    private int[] postClipTriangleIndicesBuffer;
    private float[] screenBuffer;

    private int currentMaxVertices = 0;

    private int totalVertices;

    private int[] polyIn = new int[9];
    private int[] polyOut = new int[9];

    private final Color scratchColor = new Color();

    SolidRenderer(FrameBuffer frameBuffer) {
        this.frameBuffer = frameBuffer;
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
        postClipTriangleIndicesBuffer = new int[currentMaxVertices * 4 * 3];
        screenBuffer = new float[currentMaxVertices * 4 * 3 * 3];
    }

    void render(Entity entity, float[][] VP, RenderOptions options) {

        ensureCapacity(entity.mesh.vertices.length / Renderer.VERTEX_STRIDE);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] indices = entity.mesh.indices;

        totalVertices = vertices.length / Renderer.VERTEX_STRIDE;

        Renderer.computeClipVertices(vertices, MVP, clipBuffer);
        int triangleCount = cullOutsideTriangles(indices, clipBuffer, triangleBuffer);
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

        displayTriangles(options, entity.color, postClipTriangleCount);
    }

    private void displayTriangles(RenderOptions options, Color baseColor, int postClipTriangleCount) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            int screenX1 = (int) screenBuffer[i * 3 * 3];
            int screenY1 = (int) screenBuffer[i * 3 * 3 + 1];
            float invW1 = screenBuffer[i * 3 * 3 + 2];
            int screenX2 = (int) screenBuffer[(i * 3 + 1) * 3];
            int screenY2 = (int) screenBuffer[(i * 3 + 1) * 3 + 1];
            float invW2 = screenBuffer[(i * 3 + 1) * 3 + 2];
            int screenX3 = (int) screenBuffer[(i * 3 + 2) * 3];
            int screenY3 = (int) screenBuffer[(i * 3 + 2) * 3 + 1];
            float invW3 = screenBuffer[(i * 3 + 2) * 3 + 2];

            assert (screenX1 >= 0 && screenX1 < Main.SCREEN_WIDTH &&
                screenY1 >= 0 && screenY1 < Main.SCREEN_HEIGHT &&
                screenX2 >= 0 && screenX2 < Main.SCREEN_WIDTH &&
                screenY2 >= 0 && screenY2 < Main.SCREEN_HEIGHT &&
                screenX3 >= 0 && screenX3 < Main.SCREEN_WIDTH &&
                screenY3 >= 0 && screenY3 < Main.SCREEN_HEIGHT) : "illegal screen pos";

            Color color = getRenderColor(options, baseColor, i * 3, i * 3 + 1, i * 3 + 2);
            rasterizeTriangle(options, color, screenX1, screenY1, invW1, screenX2, screenY2, invW2, screenX3, screenY3, invW3);
        }
    }

    private void computeScreenVertices(int postClipTriangleCount, float[] out) {
        for (int i = 0; i < postClipTriangleCount; i++) {

            for (int j = 0; j < 3; j++) {
                int idx = postClipTriangleIndicesBuffer[i * 3 + j];

                final float w = clipBuffer[idx * Renderer.CLIP_STRIDE + 3];
                final float ndcX = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE] / w, -1, 1);
                final float ndcY = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE + 1] / w, -1, 1);

                final float screenX = (ndcX + 1) / 2 * (Main.SCREEN_WIDTH - 1);
                final float screenY = (ndcY + 1) / 2 * (Main.SCREEN_HEIGHT - 1);

                out[(i * 3 + j) * 3] = screenX;
                out[(i * 3 + j) * 3 + 1] = screenY;
                out[(i * 3 + j) * 3 + 2] = 1 / w;
            }
        }
    }

    private int SHClipTriangles(int triangleCount, int[] indices, int[] out) {
        int postClipTriangleCount = 0;

        for (int i = 0; i < triangleCount; i++) {
//            int idx = triangleBuffer[triangleOrderBuffer[i]];
            int idx = triangleBuffer[i];
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

            if (bothOutside) {//keep none
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

    /*https://www.sunshine2k.de/coding/java/TriangleRasterization/TriangleRasterization.html*/
    private void rasterizeTriangle(RenderOptions options, Color color, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {

        //a top, b middle, c bottom vertex
        int aX = v1X, aY = v1Y;
        float aInvW = invW1;
        int bX = v2X, bY = v2Y;
        float bInvW = invW2;
        int cX = v3X, cY = v3Y;
        float cInvW = invW3;
        int tX, tY;
        float tInvW;

        if (aY > bY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            aX = bX;
            aY = bY;
            aInvW = bInvW;
            bX = tX;
            bY = tY;
            bInvW = tInvW;
        }
        if (aY > cY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            aX = cX;
            aY = cY;
            aInvW = cInvW;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
        }
        if (bY > cY) {
            tX = bX;
            tY = bY;
            tInvW = bInvW;
            bX = cX;
            bY = cY;
            bInvW = cInvW;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
        }

        if (cY == aY) return;

        float t = (float) (bY - aY) / (cY - aY);
        //interpolated x value for triangle separation between top and bottom
        int midX = (int) (aX + t * (cX - aX));
        float midInvW = aInvW + t * (cInvW - aInvW);


        /* here we know that aY <= bY <= cY */
        /* check for trivial case of bottom-flat triangle */
        if (bY == cY) {
            fillBottomFlatTriangle(color, aX, aY, aInvW, bX, bY, bInvW, cX, cY, cInvW);
        }
        /* check for trivial case of top-flat triangle */
        else if (aY == bY) {
            fillTopFlatTriangle(color, aX, aY, aInvW, bX, bY, bInvW, cX, cY, cInvW);
        } else {
            /* general case - split the triangle in a topflat and bottom-flat one */
            fillBottomFlatTriangle(color, aX, aY, aInvW, bX, bY, bInvW, midX, bY, midInvW);
            fillTopFlatTriangle(color, bX, bY, bInvW, midX, bY, midInvW, cX, cY, cInvW);
        }

        if (options.showWireFrame) {
            drawLine(v1X, v1Y, invW1, v2X, v2Y, invW2, 0, 0, 0);
            drawLine(v1X, v1Y, invW1, v3X, v3Y, invW3, 0, 0, 0);
            drawLine(v2X, v2Y, invW2, v3X, v3Y, invW3, 0, 0, 0);
        }
    }

    void drawLine(int x0, int y0, float invW0, int x1, int y1, float invW1, float r, float g, float b) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int steps = Math.max(dx, dy);
        float invWSlope = steps > 0 ? (invW1 - invW0) / steps : 0;
        float curInvW = invW0;

        byte rb = (byte)(r * 255);
        byte gb = (byte)(g * 255);
        byte bb = (byte)(b * 255);

        while (true) {
            int yFlip = Main.SCREEN_HEIGHT - y0 - 1;
            if (curInvW + WIRE_FRAME_DEPTH_EPSILON > frameBuffer.getDepth(x0, yFlip)) {
//                frameBuffer.setDepth(x0, yFlip, curInvW);
                frameBuffer.setPixel(x0, yFlip, rb, gb, bb);
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 <  dx) { err += dx; y0 += sy; }
            curInvW += invWSlope;
        }
    }

    private void fillBottomFlatTriangle(Color color, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {
        float invSlopeX1 = (float) (v2X - v1X) / (v2Y - v1Y);
        float invSlopeX2 = (float) (v3X - v1X) / (v3Y - v1Y);
        float invSlopeW1 = (invW2 - invW1) / (v2Y - v1Y);
        float invSlopeW2 = (invW3 - invW1) / (v3Y - v1Y);

        float curX1 = v1X;
        float curX2 = v1X;
        float curInvW1 = invW1;
        float curInvW2 = invW1;

        for (int scanlineY = v1Y; scanlineY <= v2Y; scanlineY++) {
            drawHorizLine(color, (int) curX1, curInvW1, (int) curX2, curInvW2, scanlineY);
            curX1 += invSlopeX1;
            curX2 += invSlopeX2;
            curInvW1 += invSlopeW1;
            curInvW2 += invSlopeW2;
        }
    }

    private void fillTopFlatTriangle(Color color, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {
        float invSlopeX1 = (float) (v3X - v1X) / (v3Y - v1Y);
        float invSlopeX2 = (float) (v3X - v2X) / (v3Y - v2Y);
        float invSlopeW1 = (invW3 - invW1) / (v3Y - v1Y);
        float invSlopeW2 = (invW3 - invW2) / (v3Y - v2Y);

        float curX1 = v3X;
        float curX2 = v3X;
        float curInvW1 = invW3;
        float curInvW2 = invW3;

        for (int scanlineY = v3Y; scanlineY > v1Y; scanlineY--) {
            drawHorizLine(color, (int) curX1, curInvW1, (int) curX2, curInvW2, scanlineY);
            curX1 -= invSlopeX1;
            curX2 -= invSlopeX2;
            curInvW1 -= invSlopeW1;
            curInvW2 -= invSlopeW2;
        }
    }

    private void drawHorizLine(Color color, int x1, float invW1, int x2, float invW2, int y) {
        int xStart = Math.min(x1, x2);
        int xEnd = Math.max(x1, x2);

        if (x1 > x2) {
            float tmp = invW1;
            invW1 = invW2;
            invW2 = tmp;
        }

        float invWSlope = (xEnd > xStart) ? (invW2 - invW1) / (xEnd - xStart) : 0;
        float curInvW = invW1;

        int yFlip = Main.SCREEN_HEIGHT - y - 1;

        byte r = (byte)(color.r * 255);
        byte g = (byte)(color.g * 255);
        byte b = (byte)(color.b * 255);

        for (int x = xStart; x <= xEnd; x++) {

            if (curInvW > frameBuffer.getDepth(x, yFlip)) {
                frameBuffer.setDepth(x, yFlip, curInvW);
                frameBuffer.setPixel(x, yFlip, r, g, b);
            }
            curInvW += invWSlope;
        }
    }

    private Color getRenderColor(RenderOptions options, Color color, int idx1, int idx2, int idx3) {
        if (!options.randomizeTexture) {
            return color;
        }

        // deterministic pseudo-random hash from all 3 vertex indices
        int hash = (idx1 * 92837111) ^ (idx2 * 689287499) ^ (idx3 * 283823481);
        float offset = ((hash & 0xFF) / 255f - 0.5f) * 0.12f; // range [-0.06, 0.06]

        scratchColor.set(Math.clamp(color.r + offset, 0, 1), Math.clamp(color.g + offset, 0, 1), Math.clamp(color.b + offset, 0, 1), 1f);
        return scratchColor;
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
}
