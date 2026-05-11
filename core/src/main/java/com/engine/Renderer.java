package com.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class Renderer {

    private final ShapeRenderer shapeRenderer;

     Renderer(ShapeRenderer shapeRenderer) {
        this.shapeRenderer = shapeRenderer;
    }

     final void wireFrameRender(List<Entity> entities, Camera camera) {
        Matrix VP = camera.getProjectionMatrix().matmul(camera.getViewMatrix());

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Entity entity : entities) {
            wireFrameRender(entity, VP);
        }
        shapeRenderer.end();
    }

     final void solidRender(List<Entity> entities, Camera camera) {
        Matrix V = camera.getViewMatrix();
        Matrix P = camera.getProjectionMatrix();

//        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity entity : entities) {
            solidRender(entity, V, P);
//            wireFrameRender(entity, VP, Color.BLACK);
        }
//        shapeRenderer.end();
    }

    void solidRender(Entity entity, Matrix V, Matrix P) {
//        shapeRenderer.setColor(entity.color);

//        Matrix MVP = VP.matmul(entity.transform.toMatrix());
        Matrix M = entity.transform.toMatrix();

        Matrix MV = V.matmul(M);

        final float[] vertices = entity.mesh.vertices;

        final int[] indices = entity.mesh.indices;

        final int stride = entity.mesh.stride;

        List<ViewTriangle> viewTriangles = new ArrayList<>();
        //TODO no objects
        for (int i = 0; i < indices.length; i += 3) {
            int idx1 = indices[i];
            int idx2 = indices[i + 1];
            int idx3 = indices[i + 2];

            float x1 = vertices[idx1 * stride];
            float y1 = vertices[idx1 * stride + 1];
            float z1 = vertices[idx1 * stride + 2];

            float x2 = vertices[idx2 * stride];
            float y2 = vertices[idx2 * stride + 1];
            float z2 = vertices[idx2 * stride + 2];

            float x3 = vertices[idx3 * stride];
            float y3 = vertices[idx3 * stride + 1];
            float z3 = vertices[idx3 * stride + 2];

            Vector4 viewSpaceV1 = (Vector4) MV.matmul(new Vector4(new float[]{x1, y1, z1, 1}));
            Vector4 viewSpaceV2 = (Vector4) MV.matmul(new Vector4(new float[]{x2, y2, z2, 1}));
            Vector4 viewSpaceV3 = (Vector4) MV.matmul(new Vector4(new float[]{x3, y3, z3, 1}));

            viewTriangles.add(new ViewTriangle(viewSpaceV1, viewSpaceV2, viewSpaceV3));
        }

        viewTriangles.sort(new TriangleAvgDepthComparator()); //sort for painter's algorithm
//        viewTriangles.sort(new TriangleMinDepthComparator()); //sort for painter's algorithm

        for (ViewTriangle viewTriangle : viewTriangles) {
            Vector4 viewSpaceV1 = viewTriangle.vertex1;
            Vector4 viewSpaceV2 = viewTriangle.vertex2;
            Vector4 viewSpaceV3 = viewTriangle.vertex3;

            Vector4 clipSpaceV1 = (Vector4) P.matmul(viewSpaceV1);
            Vector4 clipSpaceV2 = (Vector4) P.matmul(viewSpaceV2);
            Vector4 clipSpaceV3 = (Vector4) P.matmul(viewSpaceV3);

            if (clipSpaceV1.get(3) <= 0 && clipSpaceV2.get(3) <= 0 && clipSpaceV3.get(3) <= 0) continue;

            Vector3 ndcSpaceV1 = clipToNdc(clipSpaceV1);
            Vector3 ndcSpaceV2 = clipToNdc(clipSpaceV2);
            Vector3 ndcSpaceV3 = clipToNdc(clipSpaceV3);


            if (!inNDC(ndcSpaceV1) || !inNDC(ndcSpaceV2) || !inNDC(ndcSpaceV3)) continue;

            Vector2 screenV1 = ndcToScreen(ndcSpaceV1);
            Vector2 screenV2 = ndcToScreen(ndcSpaceV2);
            Vector2 screenV3 = ndcToScreen(ndcSpaceV3);

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(entity.color);
            shapeRenderer.triangle(
                screenV1.get(0), screenV1.get(1),
                screenV2.get(0), screenV2.get(1),
                screenV3.get(0), screenV3.get(1)
            );
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
            shapeRenderer.end();
        }

//        for (int i = 0; i < indices.length; i += 3) {
//            int idx1 = indices[i];
//            int idx2 = indices[i + 1];
//            int idx3 = indices[i + 2];
//
//            float x1 = vertices[idx1*stride];
//            float y1 = vertices[idx1*stride+1];
//            float z1 = vertices[idx1*stride+2];
//
//            float x2 = vertices[idx2*stride];
//            float y2 = vertices[idx2*stride+1];
//            float z2 = vertices[idx2*stride+2];
//
//            float x3 = vertices[idx3*stride];
//            float y3 = vertices[idx3*stride+1];
//            float z3 = vertices[idx3*stride+2];
//
//            //TODO Sutherland-Hodgman clipping
//
//            Vector4 clipSpaceV1 = (Vector4) MVP.matmul(new Vector4(new float[]{x1, y1, z1, 1}));
//            Vector4 clipSpaceV2 = (Vector4) MVP.matmul(new Vector4(new float[]{x2, y2, z2, 1}));
//            Vector4 clipSpaceV3 = (Vector4) MVP.matmul(new Vector4(new float[]{x3, y3, z3, 1}));
//
//            if (clipSpaceV1.get(3) <= 0 && clipSpaceV2.get(3) <= 0 && clipSpaceV3.get(3) <= 0) continue;
//
//            Vector3 ndcSpaceV1 = clipToNdc(clipSpaceV1);
//            Vector3 ndcSpaceV2 = clipToNdc(clipSpaceV2);
//            Vector3 ndcSpaceV3 = clipToNdc(clipSpaceV3);
//
//
//            if (!inNDC(ndcSpaceV1) || !inNDC(ndcSpaceV2) || !inNDC(ndcSpaceV3)) continue;
//
//            Vector2 screenV1 = ndcToScreen(ndcSpaceV1);
//            Vector2 screenV2 = ndcToScreen(ndcSpaceV2);
//            Vector2 screenV3 = ndcToScreen(ndcSpaceV3);
//
//            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
//            shapeRenderer.setColor(entity.color);
//            shapeRenderer.triangle(
//                screenV1.get(0), screenV1.get(1),
//                screenV2.get(0), screenV2.get(1),
//                screenV3.get(0), screenV3.get(1)
//            );
//            shapeRenderer.end();
//            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
//            shapeRenderer.setColor(Color.BLACK);
//            shapeRenderer.line(
//                screenV1.get(0), screenV1.get(1),
//                screenV2.get(0), screenV2.get(1)
//            );
//            shapeRenderer.line(
//                screenV1.get(0), screenV1.get(1),
//                screenV3.get(0), screenV3.get(1)
//            );
//            shapeRenderer.line(
//                screenV2.get(0), screenV2.get(1),
//                screenV3.get(0), screenV3.get(1)
//            );
//            shapeRenderer.end();
//        }
    }
    void wireFrameRender(Entity entity, Matrix VP) {
        shapeRenderer.setColor(entity.color);

        Matrix MVP = VP.matmul(entity.transform.toMatrix());

        final float[] vertices = entity.mesh.vertices;

        final int[] edges = entity.mesh.edges;

        final int stride = entity.mesh.stride;


        for (int i = 0; i < edges.length; i += 2) {
            int idx1 = edges[i];
            int idx2 = edges[i + 1];

            float x1 = vertices[idx1*stride];
            float y1 = vertices[idx1*stride+1];
            float z1 = vertices[idx1*stride+2];

            float x2 = vertices[idx2*stride];
            float y2 = vertices[idx2*stride+1];
            float z2 = vertices[idx2*stride+2];


            //TODO Sutherland-Hodgman clipping

            Vector4 clipSpaceV1 = (Vector4) MVP.matmul(new Vector4(new float[]{x1, y1, z1, 1}));
            Vector4 clipSpaceV2 = (Vector4) MVP.matmul(new Vector4(new float[]{x2, y2, z2, 1}));


            if (clipSpaceV1.get(3) <= 0 && clipSpaceV2.get(3) <= 0) continue;

            Vector3 ndcSpaceV1 = clipToNdc(clipSpaceV1);
            Vector3 ndcSpaceV2 = clipToNdc(clipSpaceV2);


            if (!inNDC(ndcSpaceV1) || !inNDC(ndcSpaceV2)) continue;

            Vector2 screenV1 = ndcToScreen(ndcSpaceV1);
            Vector2 screenV2 = ndcToScreen(ndcSpaceV2);

            shapeRenderer.line(
                screenV1.get(0), screenV1.get(1),
                screenV2.get(0), screenV2.get(1)
            );
        }
    }

    boolean inNDC(Vector3 v) {
         return v.get(0) >= -1 && v.get(0) <= 1 && v.get(1)  >= -1 && v.get(1) <= 1;
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
}

class ViewTriangle {

    Vector4 vertex1;
    Vector4 vertex2;
    Vector4 vertex3;

    ViewTriangle(Vector4 vertex1, Vector4 vertex2, Vector4 vertex3) {
        this.vertex1 = vertex1;
        this.vertex2 = vertex2;
        this.vertex3 = vertex3;
    }

}

class TriangleAvgDepthComparator implements Comparator<ViewTriangle> {
    @Override
    public int compare(ViewTriangle a, ViewTriangle b) {
        float avgZ1 = (a.vertex1.get(2)+a.vertex2.get(2)+a.vertex3.get(2))/3;
        float avgZ2 = (b.vertex1.get(2)+b.vertex2.get(2)+b.vertex3.get(2))/3;

        return Float.compare(avgZ1, avgZ2);
    }
}

class TriangleMinDepthComparator implements Comparator<ViewTriangle> {
    @Override
    public int compare(ViewTriangle a, ViewTriangle b) {
        float minZ1 = Math.max(Math.max(a.vertex1.get(2),  a.vertex2.get(2)), a.vertex3.get(2)); //min in abs val
        float minZ2 = Math.max(Math.max(b.vertex1.get(2),  b.vertex2.get(2)), b.vertex3.get(2));

        return Float.compare(minZ1, minZ2);
    }
}
