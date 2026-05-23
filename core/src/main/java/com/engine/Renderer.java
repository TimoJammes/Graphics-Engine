package com.engine;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;


public class Renderer {

    private static final RenderOptions DEFAULT_RENDER_OPTIONS = new RenderOptions();
    static final float NEAR_PLANE_EPSILON = 0.01f;

    //    private static final int MAX_VERTICES = 1_000_000;
//    private static final int MAX_TRIANGLES = MAX_VERTICES * 2;
    static final int CLIP_STRIDE = 4; //stride of 4 to store w (4th coord) of vertices (clip w = -view z)

    static final int VERTEX_STRIDE = 3;

    private final ShapeRenderer shapeRenderer;
    private final ScreenRenderer screenRenderer = new ScreenRenderer(Main.SCREEN_WIDTH, Main.SCREEN_HEIGHT);
    private final WireFrameRenderer wireFrameRenderer = new WireFrameRenderer(screenRenderer);
    private final SolidRenderer solidRenderer;// = new SolidRenderer(screenRenderer);


    Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
        solidRenderer = new SolidRenderer(shapeRenderer);
    }

    void renderScene(Scene scene, Camera camera) {

        float[][] V = camera.getViewMatrix();
        float[][] P = camera.getProjectionMatrix();

        float[][] VP = Matrix.matmul(P, V);

        ScreenUtils.clear(scene.backgroundColor);
//        screenRenderer.clear();
//        screenRenderer.clear(scene.backgroundColor);

        for (Entity entity : scene.entities) {
            RenderOptions options = scene.renderOptions.getOrDefault(entity, DEFAULT_RENDER_OPTIONS);

            switch (options.renderMode) {
                case Scene.RenderMode.WIRE_FRAME:
                    wireFrameRenderer.render(entity, VP);
                    break;
                case Scene.RenderMode.SOLID:
                    solidRenderer.render(entity, VP, options);
                    break;
                default:
                    throw new IllegalStateException("Unsupported render mode");

            }
        }
//        screenRenderer.present();
    }

    /**
     * compute clip-space vertex coords.
     *
     * @param vertices: entity model-space coords (x, y, z)
     * @param MVP:      model-view-projection matrix
     * @param out:      store computed vertices here (x, y, z, w)
     */
    static void computeClipVertices(float[] vertices, float[][] MVP, float[] out) {
        for (int i = 0; i < vertices.length / Renderer.VERTEX_STRIDE; i++) {

            float x = vertices[i * Renderer.VERTEX_STRIDE];
            float y = vertices[i * Renderer.VERTEX_STRIDE + 1];
            float z = vertices[i * Renderer.VERTEX_STRIDE + 2];

            //to avoid float[] creation through Matrix.matmul
            directMatmul(out, i * Renderer.CLIP_STRIDE, MVP, x, y, z, 1);
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
    static void directMatmul(float[] storageArray, int startIdx, float[][] matrix, float x, float y, float z, float w) {

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

}
