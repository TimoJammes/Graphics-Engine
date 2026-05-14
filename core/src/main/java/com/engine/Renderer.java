package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;

//import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//import java.util.Comparator;


public class Renderer {

    private final ShapeRenderer shapeRenderer;

    private static final float NEAR_PLANE_EPSILON = 0.0001f;

    private static final int MAX_VERTICES = 1_000_000;
    private static final int clipStride = 4; //stride of 4 to store w (4th coord) of vertices (clip w = -view z)

    private static final int vertexStride = 3;

    private final float[] clipVertices = new float[MAX_VERTICES * clipStride];
    private final Integer[] triangleOrder = new Integer[MAX_VERTICES * 2];

    Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
    }

    void renderScene(Scene scene, Camera camera) {

        Matrix V = camera.getViewMatrix();
        Matrix P = camera.getProjectionMatrix();

        ScreenUtils.clear(scene.backgroundColor);

        for (Entity entity : scene.entities) {
            RenderOptions options = scene.renderOptions.getOrDefault(entity, new RenderOptions());

            switch (options.renderMode) {
                case Scene.RenderMode.WIRE_FRAME:
//                     wireFrameRender(scene, camera);
                    wireFrameRender(entity, V, P);
                    break;
                case Scene.RenderMode.SOLID:
//                     solidRender(scene, camera);
                    solidRender(entity, V, P, options);
                    break;
                default:
                    throw new IllegalStateException("Unsupported render mode");

            }

        }
    }

//     final void wireFrameRender(Scene scene, Camera camera) {
//        Matrix VP = camera.getProjectionMatrix().matmul(camera.getViewMatrix());
//
//        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
//        for (Entity entity : scene.entities) {
//            wireFrameRender(entity, VP);
//        }
//        shapeRenderer.end();
//    }

//     final void solidRender(Scene scene, Camera camera) {
//
//         Matrix V = camera.getViewMatrix();
//        Matrix P = camera.getProjectionMatrix();
//
////        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
//        for (Entity entity : scene.entities) {
//            solidRender(entity, V, P, scene.showWireFrame.getOrDefault(entity, true));
////            wireFrameRender(entity, VP, Color.BLACK);
//        }

    /// /        shapeRenderer.end();
//    }

    void solidRender(Entity entity, Matrix V, Matrix P, RenderOptions options) {

        Matrix M = entity.transform.toMatrix();

//        Matrix MV = V.matmul(M);
        Matrix MVP = P.matmul(V.matmul(M));

        final float[] vertices = entity.mesh.vertices;

        final int[] indices = entity.mesh.indices;

        for (int i = 0; i < vertices.length / vertexStride; i++) {

            float x = vertices[i * vertexStride];
            float y = vertices[i * vertexStride + 1];
            float z = vertices[i * vertexStride + 2];

//            Vector4 viewSpaceV = (Vector4) MV.matmul(new Vector4(x, y, z, 1));
            Vector4 clipSpaceV = (Vector4) MVP.matmul(new Vector4(x, y, z, 1));


            clipVertices[i * clipStride] = clipSpaceV.get(0);
            clipVertices[i * clipStride + 1] = clipSpaceV.get(1);
            clipVertices[i * clipStride + 2] = clipSpaceV.get(2);
            clipVertices[i * clipStride + 3] = clipSpaceV.get(3);
        }

        int triangleCount = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int idx1 = indices[i];
            int idx2 = indices[i + 1];
            int idx3 = indices[i + 2];

            float w1 = clipVertices[idx1 * 4 + 3];
            float w2 = clipVertices[idx2 * 4 + 3];
            float w3 = clipVertices[idx3 * 4 + 3];

            //TODO Sutherland-Hodgman clipping


            // behind camera check
            if (w1 <= 0 && w2 <= 0 && w3 <= 0) continue;
//            if (w1 <= 0 || w2 <= 0 || w3 <= 0) continue;

            // basic NDC bounds check
            if (!inFrustum(idx1) && !inFrustum(idx2) && !inFrustum(idx3)) continue;
//            if (!inFrustum(idx1) || !inFrustum(idx2) || !inFrustum(idx3)) continue;

            triangleOrder[triangleCount++] = i;  // only add visible triangles
        }

        Arrays.sort(triangleOrder, 0, triangleCount, (a, b) -> {
            float zA = avgZ(a, indices);
            float zB = avgZ(b, indices);
            return Float.compare(zA, zB);
        });

        if (!options.showWireFrame)
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < triangleCount; i++) {
            int index = triangleOrder[i];

            int idx1, idx2, idx3;
            idx1 = indices[index];
            idx2 = indices[index + 1];
            idx3 = indices[index + 2];

            Vector4 v1 = new Vector4(
                clipVertices[idx1 * clipStride],
                clipVertices[idx1 * clipStride + 1],
                clipVertices[idx1 * clipStride + 2],
                clipVertices[idx1 * clipStride + 3]);
            Vector4 v2 = new Vector4(
                clipVertices[idx2 * clipStride],
                clipVertices[idx2 * clipStride + 1],
                clipVertices[idx2 * clipStride + 2],
                clipVertices[idx2 * clipStride + 3]);
            Vector4 v3 = new Vector4(
                clipVertices[idx3 * clipStride],
                clipVertices[idx3 * clipStride + 1],
                clipVertices[idx3 * clipStride + 2],
                clipVertices[idx3 * clipStride + 3]);


            int negWCount = 0;

            if (v1.getW() <= 0) negWCount++;
            if (v2.getW() <= 0) negWCount++;
            if (v3.getW() <= 0) negWCount++;

            if (negWCount == 2) {

                shCullingTwoBehind(v1, v2, v3);
            }

            Vector4 frontV1 = null, frontV2 = null, newV = null;

            if (negWCount == 1) {


                List<Vector4> res = shCullingOneBehind(v1, v2, v3);

                frontV1 = res.get(0);
                frontV2 = res.get(1);
                newV = res.get(2);

            }

            Vector2 screenV1 = ndcToScreen(clipToNdc(v1));
            Vector2 screenV2 = ndcToScreen(clipToNdc(v2));
            Vector2 screenV3 = ndcToScreen(clipToNdc(v3));

            Vector2 screenFrontV1 = null, screenFrontV2 = null, screenNewV = null;

            if (newV != null) {
                screenFrontV1 = ndcToScreen(clipToNdc(frontV1));
                screenFrontV2 = ndcToScreen(clipToNdc(frontV2));
                screenNewV = ndcToScreen(clipToNdc(newV));
            }

            if (options.showWireFrame)
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            Color color = entity.color;
            if (options.randomizeTexture) {
                float[] offsets = {0f, 0.03f, -0.03f, 0.06f, -0.06f};
                float offset = offsets[idx1 % offsets.length];

                color = new Color(
                    Math.clamp(entity.color.r + offset, 0, 1),
                    Math.clamp(entity.color.g + offset, 0, 1),
                    Math.clamp(entity.color.b + offset, 0, 1),
                    1f
                );
            }

            shapeRenderer.setColor(color);

            shapeRenderer.triangle(
                screenV1.get(0), screenV1.get(1),
                screenV2.get(0), screenV2.get(1),
                screenV3.get(0), screenV3.get(1)
            );

            if (newV != null)
                shapeRenderer.triangle(
                    screenFrontV1.get(0), screenFrontV1.get(1),
                    screenFrontV2.get(0), screenFrontV2.get(1),
                    screenNewV.get(0), screenNewV.get(1)
                );


            if (options.showWireFrame) {
                shapeRenderer.end();

                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.BLACK);
                shapeRenderer.line(
                    screenV1.get(0), screenV1.get(1),
                    screenV2.get(0), screenV2.get(1)
                );
                shapeRenderer.line(
                    screenV1.get(0), screenV1.get(1),
                    screenV3.get(0), screenV3.get(1)
                );
                shapeRenderer.line(
                    screenV2.get(0), screenV2.get(1),
                    screenV3.get(0), screenV3.get(1)
                );
                if (newV != null) {
                    shapeRenderer.line(
                        screenFrontV1.get(0), screenFrontV1.get(1),
                        screenFrontV2.get(0), screenFrontV2.get(1)
                    );
                    shapeRenderer.line(
                        screenFrontV1.get(0), screenFrontV1.get(1),
                        screenNewV.get(0), screenNewV.get(1)
                    );
                    shapeRenderer.line(
                        screenFrontV2.get(0), screenFrontV2.get(1),
                        screenNewV.get(0), screenNewV.get(1)
                    );
                }
                shapeRenderer.end();
            }


//            System.out.println("ba");
        }
        if (!options.showWireFrame)
            shapeRenderer.end();
    }

    private static List<Vector4> shCullingOneBehind(Vector4 v1, Vector4 v2, Vector4 v3) {
        Vector4 behindV, frontV1, frontV2;

        if (v1.getW() <= 0) {
            behindV = v1;
            frontV1 = v2;
            frontV2 = v3;
        } else if (v2.getW() <= 0) {
            behindV = v2;
            frontV1 = v1;
            frontV2 = v3;
        } else {
            behindV = v3;
            frontV1 = v1;
            frontV2 = v2;
        }

        float t1 = frontV1.getW() / (frontV1.getW() - behindV.getW());
        float t2 = frontV2.getW() / (frontV2.getW() - behindV.getW());

        float intersectionX1 = frontV1.getX() + t1 * (behindV.getX() - frontV1.getX());
        float intersectionY1 = frontV1.getY() + t1 * (behindV.getY() - frontV1.getY());
        float intersectionZ1 = frontV1.getZ() + t1 * (behindV.getZ() - frontV1.getZ());

        float intersectionX2 = frontV2.getX() + t2 * (behindV.getX() - frontV2.getX());
        float intersectionY2 = frontV2.getY() + t2 * (behindV.getY() - frontV2.getY());
        float intersectionZ2 = frontV2.getZ() + t2 * (behindV.getZ() - frontV2.getZ());

        behindV.setX(intersectionX1);
        behindV.setY(intersectionY1);
        behindV.setZ(intersectionZ1);
        behindV.setW(NEAR_PLANE_EPSILON);

        Vector4 newVertex = new Vector4(intersectionX2,  intersectionY2, intersectionZ2, NEAR_PLANE_EPSILON);

        List<Vector4> res = new ArrayList<>();
        res.add(frontV1);
        res.add(frontV2);
        res.add(newVertex);

        return res;
    }

    private static void shCullingTwoBehind(Vector4 v1, Vector4 v2, Vector4 v3) {
        Vector4 frontV, behindV1, behindV2;

        if (v1.getW() > 0) {
            frontV = v1;
            behindV1 = v2;
            behindV2 = v3;
        } else if (v2.getW() > 0) {
            frontV = v2;
            behindV1 = v1;
            behindV2 = v3;
        } else {
            frontV = v3;
            behindV1 = v1;
            behindV2 = v2;
        }

        float t1 = frontV.getW() / (frontV.getW() - behindV1.getW());
        float t2 = frontV.getW() / (frontV.getW() - behindV2.getW());

        float intersectionX1 = frontV.getX() + t1 * (behindV1.getX() - frontV.getX());
        float intersectionY1 = frontV.getY() + t1 * (behindV1.getY() - frontV.getY());
        float intersectionZ1 = frontV.getZ() + t1 * (behindV1.getZ() - frontV.getZ());
        float intersectionW1 = NEAR_PLANE_EPSILON;

        float intersectionX2 = frontV.getX() + t2 * (behindV2.getX() - frontV.getX());
        float intersectionY2 = frontV.getY() + t2 * (behindV2.getY() - frontV.getY());
        float intersectionZ2 = frontV.getZ() + t2 * (behindV2.getZ() - frontV.getZ());
        float  intersectionW2 = NEAR_PLANE_EPSILON;

        behindV1.setX(intersectionX1);
        behindV1.setY(intersectionY1);
        behindV1.setZ(intersectionZ1);
        behindV1.setW(intersectionW1);

        behindV2.setX(intersectionX2);
        behindV2.setY(intersectionY2);
        behindV2.setZ(intersectionZ2);
        behindV2.setW(intersectionW2);
    }

    boolean inFrustum(int idx) {
        float x = clipVertices[idx * clipStride];
        float y = clipVertices[idx * clipStride + 1];
        float w = clipVertices[idx * clipStride + 3];

        return Math.abs(x) <= w && Math.abs(y) <= w;
    }


    void wireFrameRender(Entity entity, Matrix V, Matrix P) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(entity.color);

//        Matrix VP = P.matmul(V);

        Matrix MVP = P.matmul(V).matmul(entity.transform.toMatrix());

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

            Vector4 clipSpaceV1 = (Vector4) MVP.matmul(new Vector4(x1, y1, z1, 1));
            Vector4 clipSpaceV2 = (Vector4) MVP.matmul(new Vector4(x2, y2, z2, 1));


            if (clipSpaceV1.get(3) <= 0 && clipSpaceV2.get(3) <= 0) continue;

            Vector3 ndcSpaceV1 = clipToNdc(clipSpaceV1);
            Vector3 ndcSpaceV2 = clipToNdc(clipSpaceV2);


            if (!inNDC(ndcSpaceV1) && !inNDC(ndcSpaceV2)) continue;

            Vector2 screenV1 = ndcToScreen(ndcSpaceV1);
            Vector2 screenV2 = ndcToScreen(ndcSpaceV2);

            shapeRenderer.line(
                screenV1.get(0), screenV1.get(1),
                screenV2.get(0), screenV2.get(1)
            );
        }

        shapeRenderer.end();
    }

    boolean inNDC(Vector3 v) {
        return v.get(0) >= -1 && v.get(0) <= 1 && v.get(1) >= -1 && v.get(1) <= 1;
    }

    final Vector3 clipToNdc(Vector4 clipSpaceV) {
        final float w = clipSpaceV.get(3);
        final float ndcX = clipSpaceV.get(0) / w;
        final float ndcY = clipSpaceV.get(1) / w;
        final float ndcZ = clipSpaceV.get(2) / w;

        return new Vector3(ndcX, ndcY, ndcZ);
    }

    final Vector2 ndcToScreen(Vector3 ndcSpaceV) {
        final float screenX = (ndcSpaceV.get(0) + 1) / 2 * Main.SCREEN_WIDTH;
        final float screenY = (ndcSpaceV.get(1) + 1) / 2 * Main.SCREEN_HEIGHT;

        return new Vector2(screenX, screenY);
    }

    final float avgZ(int index, int[] indices) {
        float z1, z2, z3;

        z1 = -clipVertices[indices[index] * clipStride + 2];
        z2 = -clipVertices[(indices[index + 1]) * clipStride + 2];
        z3 = -clipVertices[(indices[index + 2]) * clipStride + 2];

        return (z1 + z2 + z3) / 3f;
    }
}
