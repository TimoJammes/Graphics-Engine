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

    float[] frontV2 = null, intersectionV1 = null, intersectionV2 = null; //for SH-clipping with 1 negative-w vertex
    float[] screenFrontV2 = null, screenIntersection1 = null, screenIntersection2 = null;

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
//                     wireFrameRender(scene, camera);
                    wireFrameRender(entity, VP);
                    break;
                case Scene.RenderMode.SOLID:
//                     solidRender(scene, camera);
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

        //culls triangles behind camera or out of frustum & stores triangle indices (first idx of triangle vertex index in indices) in triangleOrder
        int triangleCount = cullOuterTriangles(indices);

        computeAvgClipZ(triangleCount, indices); //stores to avgZValues

        for (int i = 0; i < triangleCount; i++) positions[i] = i; //to sort triangles by avgZValues

        Arrays.sort(positions, 0, triangleCount, (a, b) -> Float.compare(avgZValues[a], avgZValues[b]));


        if (!options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < triangleCount; i++) {
            int index = triangleOrder[positions[i]];


            float[] clipV1 = getClipVertex(indices[index]);
            float[] clipV2 = getClipVertex(indices[index+1]);
            float[] clipV3 = getClipVertex(indices[index+2]);


            //if 1 vertex behind camera, new triangle created with vertices screenFrontV2, screenIntersection1, screenIntersection2
            //if 2 vertices behind camera, these are modified in place to become intersection vertices
            screenFrontV2 = null; screenIntersection1 = null; screenIntersection2 = null;
            shNearPlaneClip(clipV1, clipV2, clipV3);


            float[] screenV1 = ndcToScreen(clipToNdc(clipV1));
            float[] screenV2 = ndcToScreen(clipToNdc(clipV2));
            float[] screenV3 = ndcToScreen(clipToNdc(clipV3));

            setRenderColor(entity, options, indices[index]); //pass indices[index] for randomizeTexture

            //draws 1 triangle (first triplet of vertices) or 2 if SH-clipping with 2 vertices behind camera (second triplet of vertices)
            drawTriangles(options, screenV1, screenV2, screenV3, screenFrontV2, screenIntersection1, screenIntersection2);
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
    private void shNearPlaneClip(float[] clipV1, float[] clipV2, float[] clipV3) {
        frontV2 = null; intersectionV1 = null; intersectionV2 = null;
        int negWCount = 0;

        if (Matrix.getW(clipV1) <= 0) negWCount++;
        if (Matrix.getW(clipV2) <= 0) negWCount++;
        if (Matrix.getW(clipV3) <= 0) negWCount++;

        if (negWCount == 2) {
            shCullingTwoBehind(clipV1, clipV2, clipV3);
        }


        if (negWCount == 1) {
            shCullingOneBehind(clipV1, clipV2, clipV3); //assigns to frontV2, intersectionV1 & intersectionV2 directly
        }

        if (intersectionV2 != null) {
            screenFrontV2 = ndcToScreen(clipToNdc(frontV2));
            screenIntersection1 = ndcToScreen(clipToNdc(intersectionV1));
            screenIntersection2 = ndcToScreen(clipToNdc(intersectionV2));
        }
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

            float[] clipSpaceV = Matrix.matmul(MVP, new float[]{x, y, z, 1});


            clipVertices[i * clipStride] = Matrix.getX(clipSpaceV);
            clipVertices[i * clipStride + 1] = Matrix.getY(clipSpaceV);
            clipVertices[i * clipStride + 2] = Matrix.getZ(clipSpaceV);
            clipVertices[i * clipStride + 3] = Matrix.getW(clipSpaceV);
        }
    }

    private void shCullingOneBehind(float[] v1, float[] v2, float[] v3) {
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


        Matrix.setX(behindV, intersectionX1);
        Matrix.setY(behindV, intersectionY1);
        Matrix.setZ(behindV, intersectionZ1);
        Matrix.setW(behindV, NEAR_PLANE_EPSILON);

        float[] newVertex = new float[]{intersectionX2,  intersectionY2, intersectionZ2, NEAR_PLANE_EPSILON};

//        List<float[]> res = new ArrayList<>();
        this.frontV2 = frontV2;
        intersectionV1 = behindV;
        intersectionV2 = newVertex;

//        return res;
    }

    private static void shCullingTwoBehind(float[] v1, float[] v2, float[] v3) {
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

        Matrix.setX(behindV1, intersectionX1);
        Matrix.setY(behindV1, intersectionY1);
        Matrix.setZ(behindV1, intersectionZ1);
        Matrix.setW(behindV1, NEAR_PLANE_EPSILON);

        Matrix.setX(behindV2, intersectionX2);
        Matrix.setY(behindV2, intersectionY2);
        Matrix.setZ(behindV2, intersectionZ2);
        Matrix.setW(behindV2, NEAR_PLANE_EPSILON);
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


            //TODO Sutherland-Hodgman clipping

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

    final float avgZ(int index, int[] indices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * clipStride + 3];
        z2 = -clipVertices[(indices[index + 1]) * clipStride + 3];
        z3 = -clipVertices[(indices[index + 2]) * clipStride + 3];

        return (z1 + z2 + z3) / 3f;
    }
}
