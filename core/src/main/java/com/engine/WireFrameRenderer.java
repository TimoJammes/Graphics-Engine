package com.engine;

public class WireFrameRenderer {


    private static final int[][] planes = {{2, 1}, {2, -1}, {0, 1}, {0, -1}, {1, 1}, {1, -1}};

    private int[] clipResult = new int[2];

    private final ScreenRenderer screenRenderer;

    private int currentMaxVerticesWF = 0;
    private float[] clipVertexBufferWF;

    private int[] visibleEdges;
    private int[] postClipEdges;
    private int[] screenVerticesBufferWF;

    private int totalVertices;

    private boolean edgeDiscarded;

    WireFrameRenderer(ScreenRenderer screenRenderer) {
        this.screenRenderer = screenRenderer;
    }

    private void ensureCapacityWF(int vertexCount) {
        if (vertexCount <= currentMaxVerticesWF) return;
        currentMaxVerticesWF = vertexCount * 10;
        clipVertexBufferWF = new float[currentMaxVerticesWF * 5 * Renderer.clipStride];
        visibleEdges = new int[currentMaxVerticesWF * 2 * 3];
        postClipEdges = new int[currentMaxVerticesWF * 4 * 3 * 2];
        screenVerticesBufferWF = new int[currentMaxVerticesWF * 4 * 3 * 2 * 2];
    }

    void render(Entity entity, float[][] VP) {

        ensureCapacityWF(entity.mesh.vertices.length / Renderer.vertexStride);

        float[][] MVP = Matrix.matmul(VP, entity.transform.toMatrix()); //Model-View-Projection Matrix

        final float[] vertices = entity.mesh.vertices;
        final int[] edges = entity.mesh.edges;


        Renderer.computeClipVertices(vertices, MVP, clipVertexBufferWF);

        int edgeCount = cullOuterEdges(edges, clipVertexBufferWF, visibleEdges);

        totalVertices = vertices.length / Renderer.vertexStride;

        int postClipEdgeCount = SHClipEdges(edgeCount, edges);


        for (int i = 0; i < postClipEdgeCount; i++) {

            for (int j = 0; j < 2; j++) {
                int idx = postClipEdges[i * 2 + j];

                final float w = clipVertexBufferWF[idx * Renderer.clipStride + 3];
                final float ndcX = Math.clamp(clipVertexBufferWF[idx * Renderer.clipStride] / w, -1f, 1f);
                final float ndcY = Math.clamp(clipVertexBufferWF[idx * Renderer.clipStride + 1] / w, -1f, 1f);

                final float screenX = (ndcX + 1) / 2 * (Main.SCREEN_WIDTH - 1);
                final float screenY = (ndcY + 1) / 2 * (Main.SCREEN_HEIGHT - 1);

                screenVerticesBufferWF[(i * 2 + j) * 2] = (int) screenX;
                screenVerticesBufferWF[(i * 2 + j) * 2 + 1] = (int) screenY;
            }
        }

//        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
//        shapeRenderer.setColor(entity.color);

        for (int i = 0; i < postClipEdgeCount; i++) {

            int screenX1 = screenVerticesBufferWF[i * 2 * 2];
            int screenY1 = screenVerticesBufferWF[i * 2 * 2 + 1];
            int screenX2 = screenVerticesBufferWF[(i * 2 + 1) * 2];
            int screenY2 = screenVerticesBufferWF[(i * 2 + 1) * 2 + 1];

//            shapeRenderer.line(
//                screenX1, screenY1,
//                screenX2, screenY2
//            );

            if (screenX1 < 0 || screenX1 >= Main.SCREEN_WIDTH ||
                screenY1 < 0 || screenY1 >= Main.SCREEN_HEIGHT ||
                screenX2 < 0 || screenX2 >= Main.SCREEN_WIDTH ||
                screenY2 < 0 || screenY2 >= Main.SCREEN_HEIGHT)
                throw new IllegalStateException("Screen coordinate out of bounds: (" + screenX1 + "," + screenY1 + "), (" + screenX2 + "," + screenY2 + ")");

            drawLine(screenX1, screenY1, screenX2, screenY2, entity.color.r, entity.color.g, entity.color.b);
        }


//        shapeRenderer.end();
    }


    private int SHClipEdges(int edgeCount, int[] edges) {
        int postClipEdgeCount = 0;

        for (int i = 0; i < edgeCount; i++) {
            int idx = visibleEdges[i];
            int a = edges[idx];
            int b = edges[idx + 1];
//            boolean discard = false;

            int[] result = clipEdgeAllPlanes(a, b);
            if (result == null) continue;  // fully outside

            postClipEdges[postClipEdgeCount * 2] = result[0];
            postClipEdges[postClipEdgeCount * 2 + 1] = result[1];
            postClipEdgeCount++;
        }
        return postClipEdgeCount;
    }

    private int[] clipEdgeAllPlanes(int idx1, int idx2) {
//        int[] result = {idx1, idx2};
        clipResult[0] = idx1;
        clipResult[1] = idx2;

        edgeDiscarded = false;

        for (int i = 0; i < planes.length; i++) {
            int[] plane = planes[i];
            SHClipEdge(clipResult[0], clipResult[1], plane[0], plane[1]);
            if (edgeDiscarded) return null;  // discarded
        }
        return clipResult;
    }

    private void SHClipEdge(int idx1, int idx2, int component, int sign) {
        float v1 = clipVertexBufferWF[idx1 * Renderer.clipStride + component];
        float v2 = clipVertexBufferWF[idx2 * Renderer.clipStride + component];
        float w1 = clipVertexBufferWF[idx1 * Renderer.clipStride + 3];
        float w2 = clipVertexBufferWF[idx2 * Renderer.clipStride + 3];

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

        int f = frontIdx * Renderer.clipStride;
        int bh = behindIdx * Renderer.clipStride;

        float d1 = clipVertexBufferWF[f + 3] + sign * clipVertexBufferWF[f + component];
        float d2 = clipVertexBufferWF[bh + 3] + sign * clipVertexBufferWF[bh + component];
        float t = d1 / (d1 - d2);

        clipVertexBufferWF[totalVertices * Renderer.clipStride] = clipVertexBufferWF[f] + t * (clipVertexBufferWF[bh] - clipVertexBufferWF[f]);
        clipVertexBufferWF[totalVertices * Renderer.clipStride + 1] = clipVertexBufferWF[f + 1] + t * (clipVertexBufferWF[bh + 1] - clipVertexBufferWF[f + 1]);
        clipVertexBufferWF[totalVertices * Renderer.clipStride + 2] = clipVertexBufferWF[f + 2] + t * (clipVertexBufferWF[bh + 2] - clipVertexBufferWF[f + 2]);
        clipVertexBufferWF[totalVertices * Renderer.clipStride + 3] = clipVertexBufferWF[f + 3] + t * (clipVertexBufferWF[bh + 3] - clipVertexBufferWF[f + 3]);

//            postClipEdges[postClipEdgeCount * 2]     = frontIdx;
//            postClipEdges[postClipEdgeCount * 2 + 1] = totalVertices;
        totalVertices++;
        clipResult[0] = frontIdx;
        clipResult[1] = totalVertices - 1;

    }

    private int cullOuterEdges(int[] edges, float[] clipVertices, int[] out) {
        int edgeCount = 0;
        for (int i = 0; i < edges.length; i += 2) {
            int idx1 = edges[i];
            int idx2 = edges[i + 1];

            float x1 = clipVertices[idx1 * Renderer.clipStride], y1 = clipVertices[idx1 * Renderer.clipStride + 1], z1 = clipVertices[idx1 * Renderer.clipStride + 2];
            float x2 = clipVertices[idx2 * Renderer.clipStride], y2 = clipVertices[idx2 * Renderer.clipStride + 1], z2 = clipVertices[idx2 * Renderer.clipStride + 2];
            float w1 = clipVertices[idx1 * Renderer.clipStride + 3], w2 = clipVertices[idx2 * Renderer.clipStride + 3];

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
//            try {
            screenRenderer.setPixel(x0, Main.SCREEN_HEIGHT - y0 - 1, (byte) (r * 255), (byte) (g * 255), (byte) (b * 255));
//            }
//            catch (Exception e) {
////                System.out.println(e);
//                System.out.println(x0+" "+(Main.SCREEN_HEIGHT - y0));
//            }
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
