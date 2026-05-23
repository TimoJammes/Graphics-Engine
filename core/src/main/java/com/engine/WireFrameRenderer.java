package com.engine;

import com.badlogic.gdx.graphics.Color;

public class WireFrameRenderer {


    private final int[] clipResult = new int[2];

    private final ScreenRenderer screenRenderer;

    private int currentMaxVertices = 0;
    private float[] clipBuffer;

    private int[] visibleEdges;
    private int[] postClipEdges;
    private int[] screenBuffer;

    private int totalVertices;

    private boolean edgeDiscarded;

    WireFrameRenderer(ScreenRenderer screenRenderer) {
        this.screenRenderer = screenRenderer;
    }

    private void ensureCapacityWF(int vertexCount) {
        if (vertexCount <= currentMaxVertices) return;
        currentMaxVertices = vertexCount;
        clipBuffer = new float[currentMaxVertices * 4 * Renderer.CLIP_STRIDE];
        visibleEdges = new int[currentMaxVertices * 3];
        postClipEdges = new int[currentMaxVertices * 2 * 3];
        screenBuffer = new int[currentMaxVertices * 3 * 2 * 2];
    }

    void render(Entity entity, float[][] VP) {

        ensureCapacityWF(entity.mesh.vertices.length / Renderer.VERTEX_STRIDE);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix());

        final float[] vertices = entity.mesh.vertices;
        final int[] edges = entity.mesh.edges;

        totalVertices = vertices.length / Renderer.VERTEX_STRIDE;

        Renderer.computeClipVertices(vertices, MVP, clipBuffer);
        int edgeCount = cullOutsideEdges(edges, clipBuffer, visibleEdges);
        int postClipEdgeCount = SHClipEdges(edgeCount, edges, postClipEdges);
        computeScreenVertices(postClipEdgeCount, screenBuffer);

        displayEdges(entity.color, postClipEdgeCount);

    }

    private void displayEdges(Color color, int postClipEdgeCount) {
        for (int i = 0; i < postClipEdgeCount; i++) {

            int screenX1 = screenBuffer[i * 2 * 2];
            int screenY1 = screenBuffer[i * 2 * 2 + 1];
            int screenX2 = screenBuffer[(i * 2 + 1) * 2];
            int screenY2 = screenBuffer[(i * 2 + 1) * 2 + 1];

//            if (screenX1 < 0 || screenX1 >= Main.SCREEN_WIDTH ||
//                screenY1 < 0 || screenY1 >= Main.SCREEN_HEIGHT ||
//                screenX2 < 0 || screenX2 >= Main.SCREEN_WIDTH ||
//                screenY2 < 0 || screenY2 >= Main.SCREEN_HEIGHT)
//                throw new IllegalStateException("Screen coordinate out of bounds: (" + screenX1 + "," + screenY1 + "), (" + screenX2 + "," + screenY2 + ")");

            drawLine(screenX1, screenY1, screenX2, screenY2, color.r, color.g, color.b);
        }
    }

    private void computeScreenVertices(int postClipEdgeCount, int[] out) {
        for (int i = 0; i < postClipEdgeCount; i++) {

            for (int j = 0; j < 2; j++) {
                int idx = postClipEdges[i * 2 + j];

                final float w = clipBuffer[idx * Renderer.CLIP_STRIDE + 3];
                final float ndcX = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE] / w, -1f, 1f);
                final float ndcY = Math.clamp(clipBuffer[idx * Renderer.CLIP_STRIDE + 1] / w, -1f, 1f);

                final float screenX = (ndcX + 1) / 2 * (Main.SCREEN_WIDTH - 1);
                final float screenY = (ndcY + 1) / 2 * (Main.SCREEN_HEIGHT - 1);

                out[(i * 2 + j) * 2] = (int) screenX;
                out[(i * 2 + j) * 2 + 1] = (int) screenY;
            }
        }
    }


    private int SHClipEdges(int edgeCount, int[] edges, int[] out) {
        int postClipEdgeCount = 0;

        for (int i = 0; i < edgeCount; i++) {
            int idx = visibleEdges[i];
            int a = edges[idx];
            int b = edges[idx + 1];

            int[] result = clipEdgeAllPlanes(a, b);
            if (result == null) continue;  // fully outside

            out[postClipEdgeCount * 2] = result[0];
            out[postClipEdgeCount * 2 + 1] = result[1];
            postClipEdgeCount++;
        }
        return postClipEdgeCount;
    }

    private int[] clipEdgeAllPlanes(int idx1, int idx2) {
        clipResult[0] = idx1;
        clipResult[1] = idx2;

        edgeDiscarded = false;

        for (int i = 0; i < Renderer.CLIP_PLANES.length; i++) {
            int[] plane = Renderer.CLIP_PLANES[i];
            SHClipEdge(clipResult[0], clipResult[1], plane[0], plane[1]);
            if (edgeDiscarded) return null;  // discarded
        }
        return clipResult;
    }

    private void SHClipEdge(int idx1, int idx2, int component, int sign) {
        float v1 = clipBuffer[idx1 * Renderer.CLIP_STRIDE + component];
        float v2 = clipBuffer[idx2 * Renderer.CLIP_STRIDE + component];
        float w1 = clipBuffer[idx1 * Renderer.CLIP_STRIDE + 3];
        float w2 = clipBuffer[idx2 * Renderer.CLIP_STRIDE + 3];

        boolean v1Outside = sign * v1 < -w1;
        boolean v2Outside = sign * v2 < -w2;

        clipResult[0] = idx1;
        clipResult[1] = idx2;

        if (!v1Outside && !v2Outside) return;  // fully inside
        if (v1Outside && v2Outside) {
//            clipResult[0] = -1;
            edgeDiscarded = true;
            return;
        }

        //must clip

        int behindIdx = v1Outside ? idx1 : idx2;
        int frontIdx = v1Outside ? idx2 : idx1;

        int f = frontIdx * Renderer.CLIP_STRIDE;
        int bh = behindIdx * Renderer.CLIP_STRIDE;

        float d1 = clipBuffer[f + 3] + sign * clipBuffer[f + component];
        float d2 = clipBuffer[bh + 3] + sign * clipBuffer[bh + component];
        float t = d1 / (d1 - d2);

        clipBuffer[totalVertices * Renderer.CLIP_STRIDE] = clipBuffer[f] + t * (clipBuffer[bh] - clipBuffer[f]);
        clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = clipBuffer[f + 1] + t * (clipBuffer[bh + 1] - clipBuffer[f + 1]);
        clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = clipBuffer[f + 2] + t * (clipBuffer[bh + 2] - clipBuffer[f + 2]);
        clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 3] = clipBuffer[f + 3] + t * (clipBuffer[bh + 3] - clipBuffer[f + 3]);

        clipResult[0] = frontIdx;
        clipResult[1] = totalVertices;
        totalVertices++;

    }

    private int cullOutsideEdges(int[] edges, float[] clipVertices, int[] out) {
        int edgeCount = 0;
        for (int i = 0; i < edges.length; i += 2) {
            int idx1 = edges[i];
            int idx2 = edges[i + 1];

            float x1 = clipVertices[idx1 * Renderer.CLIP_STRIDE], y1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 1], z1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 2];
            float x2 = clipVertices[idx2 * Renderer.CLIP_STRIDE], y2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 1], z2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 2];
            float w1 = clipVertices[idx1 * Renderer.CLIP_STRIDE + 3], w2 = clipVertices[idx2 * Renderer.CLIP_STRIDE + 3];

            boolean cull =
                (z1 < -w1 && z2 < -w2) ||  // all behind near plane
                    (z1 > w1 && z2 > w2) || //all further than far plane
                    (x1 > w1 && x2 > w2) ||  // all right of right plane
                    (x1 < -w1 && x2 < -w2) ||  // all left of left plane
                    (y1 > w1 && y2 > w2) ||  // all above top plane
                    (y1 < -w1 && y2 < -w2); // all below bottom plane

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
            screenRenderer.setPixel(x0, Main.SCREEN_HEIGHT - y0 - 1, (byte) (r * 255), (byte) (g * 255), (byte) (b * 255));
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
