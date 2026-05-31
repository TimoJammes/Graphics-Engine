package com.engine;

import com.badlogic.gdx.graphics.Color;

public class SolidRenderer {

    private static final RenderOptions DEFAULT_RENDER_OPTIONS = new RenderOptions();
    private static final float WIRE_FRAME_DEPTH_EPSILON = 1e-5f;
    private static final int WORLD_STRIDE = 3;
    private static final int RGB_STRIDE = 3;

    private final FrameBuffer frameBuffer;
    /**
     * stores clip-space vertices
     */
    private float[] clipBuffer;
    private float[] worldBuffer;
    private float[] worldNormalsBuffer;

    private float[] triangleLightBuffer; //flat face shading
    private float[] vertexLightBuffer;
    /**
     * stores indices of non-culled triangles (each index points to the first index of a triangle in indices)
     */
    private int[] triangleBuffer;
    /**
     * stores the
     */
    private int[] postClipTriangleIndicesBuffer;
    private float[] postClipTriangleLightBuffer;

    private int currentMaxVertices = 0;

    private int totalVertices;

    private int[] polyIn = new int[9];
    private int[] polyOut = new int[9];

    private final Color scratchColor = new Color();
    private final Color scratchColorRaster1 = new Color();
    private final Color scratchColorRaster2 = new Color();
    private final Color scratchColorRaster3 = new Color();
    private final Color scratchColorRaster4 = new Color();
    //for fill___FlatTriangle
    private final Color slopeColor1 = new Color();
    private final Color slopeColor2 = new Color();
    private final Color curColor1 = new Color();
    private final Color curColor2 = new Color();
    //for drawHorizLine
    private final Color slopeColor = new Color();
    private final Color curColor = new Color();

    private int vertexStride;

    private Scene currScene;

    private Camera currCam;
    private Entity currEntity;

    private float[] scratchPhong = new float[3];


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
        worldBuffer = new float[currentMaxVertices * Renderer.CLIP_STRIDE * 4];
        clipBuffer = new float[currentMaxVertices * Renderer.CLIP_STRIDE * 4];
        triangleBuffer = new int[currentMaxVertices * 2];
        postClipTriangleIndicesBuffer = new int[currentMaxVertices * 2 * 7 * 3];

        if (currScene.hasLight() || currEntity.hasNormals)
            worldNormalsBuffer = new float[currentMaxVertices * WORLD_STRIDE * 4];
        if (currScene.hasLight()) {
            triangleLightBuffer = new float[currentMaxVertices * 2 * 3];
            vertexLightBuffer = new float[currentMaxVertices * 4 * 3];
            postClipTriangleLightBuffer = new float[currentMaxVertices * 2 * RGB_STRIDE * 7];
        }
    }

    void render(Scene scene, Camera currCam, float[][] VP) {
        currScene = scene;
        this.currCam = currCam;
        for (Entity entity : scene.entities) {
            RenderOptions options = scene.renderOptions.getOrDefault(entity, DEFAULT_RENDER_OPTIONS);

            render(entity, VP, options);
        }

        currScene = null;
        this.currCam = null;
    }

    void render(Entity entity, float[][] VP, RenderOptions options) {

        currEntity = entity;

        vertexStride = entity.mesh.stride;

        final float[] vertices = currEntity.mesh.vertices;
        final int[] indices = currEntity.mesh.indices;
        totalVertices = vertices.length / vertexStride;

        ensureCapacity(vertices.length / vertexStride);

        float[][] M = currEntity.transform.toMatrix();


        if (currScene.hasLight() || currEntity.hasNormals) {

            computeWorldVertices(vertices, M, worldBuffer, vertexStride);

            if (currEntity.hasNormals)
                //matrix recomputed and stored when toMatrix() called for M computation above
                computeWorldNormals(vertices, currEntity.transform.rotation.matrix, worldNormalsBuffer, vertexStride);

            computeWorldToClipVertices(worldBuffer, totalVertices, VP, clipBuffer, Renderer.CLIP_STRIDE);

        } else {
            float[][] MVP = Matrix.matmul(VP, M); //Model-View-Projection Matrix
            Renderer.computeLocalToClipVertices(vertices, MVP, clipBuffer, vertexStride);
        }


        int triangleCount = cullOutsideTriangles(indices, clipBuffer, triangleBuffer, currEntity.mesh.isClosed);


        if (currScene.hasLight() && currEntity.hasNormals)

            if (currScene.lightingType == LightingType.FLAT) {
                computeFlatLighting(triangleCount, indices, triangleLightBuffer);
            } else if (currScene.lightingType == LightingType.GOURAUD) {
                computeGouraudLighting(totalVertices, vertexLightBuffer);
            }

        int postClipTriangleCount = shClipTriangles(triangleCount, indices, postClipTriangleIndicesBuffer);

        displayTriangles(options, postClipTriangleCount);

        currEntity = null;
    }


    private void displayTriangles(RenderOptions options, int postClipTriangleCount) {

        float scaleX = (Main.SCREEN_WIDTH - 1) / 2f;
        float scaleY = (Main.SCREEN_HEIGHT - 1) / 2f;
        for (int i = 0; i < postClipTriangleCount; i++) {

            int idx1 = postClipTriangleIndicesBuffer[i * 3];
            int idx2 = postClipTriangleIndicesBuffer[i * 3+1];
            int idx3 = postClipTriangleIndicesBuffer[i * 3+2];

            float w1 = clipBuffer[idx1 * Renderer.CLIP_STRIDE + 3];
            float ndcX1 = Math.clamp(clipBuffer[idx1 * Renderer.CLIP_STRIDE] / w1, -1, 1);
            float ndcY1 = Math.clamp(clipBuffer[idx1 * Renderer.CLIP_STRIDE + 1] / w1, -1, 1);
            float w2 = clipBuffer[idx2 * Renderer.CLIP_STRIDE + 3];
            float ndcX2 = Math.clamp(clipBuffer[idx2 * Renderer.CLIP_STRIDE] / w2, -1, 1);
            float ndcY2 = Math.clamp(clipBuffer[idx2 * Renderer.CLIP_STRIDE + 1] / w2, -1, 1);
            float w3 = clipBuffer[idx3 * Renderer.CLIP_STRIDE + 3];
            float ndcX3 = Math.clamp(clipBuffer[idx3 * Renderer.CLIP_STRIDE] / w3, -1, 1);
            float ndcY3 = Math.clamp(clipBuffer[idx3 * Renderer.CLIP_STRIDE + 1] / w3, -1, 1);

            int screenX1 = (int) ((ndcX1 + 1) * scaleX);
            int screenY1 = (int) ((ndcY1 + 1) * scaleY);
            float invW1 = 1/w1;
            int screenX2 = (int) ((ndcX2 + 1) * scaleX);
            int screenY2 = (int) ((ndcY2 + 1) * scaleY);
            float invW2 = 1/w2;
            int screenX3 = (int) ((ndcX3 + 1) * scaleX);
            int screenY3 = (int) ((ndcY3 + 1) * scaleY);
            float invW3 = 1/w3;

            if (!currEntity.isLightObj && currScene.hasLight() && currScene.lightingType == LightingType.GOURAUD)
                rasterizeTriangleGouraud(options, i, screenX1, screenY1, invW1, screenX2, screenY2, invW2, screenX3, screenY3, invW3);
            else if (!currEntity.isLightObj && currScene.hasLight() && currScene.lightingType == LightingType.PHONG)
                rasterizeTrianglePhong(options, i, screenX1, screenY1, invW1, screenX2, screenY2, invW2, screenX3, screenY3, invW3);
            else
                rasterizeTriangleFlat(options, getRenderColor(i), screenX1, screenY1, invW1, screenX2, screenY2, invW2, screenX3, screenY3, invW3);

        }
    }

    void computeFlatLighting(int triangleCount, int[] indices, float[] out) {
        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleBuffer[i];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            float ax = worldBuffer[a * Renderer.CLIP_STRIDE];
            float ay = worldBuffer[a * Renderer.CLIP_STRIDE + 1];
            float az = worldBuffer[a * Renderer.CLIP_STRIDE + 2];

            float bx = worldBuffer[b * Renderer.CLIP_STRIDE];
            float by = worldBuffer[b * Renderer.CLIP_STRIDE + 1];
            float bz = worldBuffer[b * Renderer.CLIP_STRIDE + 2];

            float cx = worldBuffer[c * Renderer.CLIP_STRIDE];
            float cy = worldBuffer[c * Renderer.CLIP_STRIDE + 1];
            float cz = worldBuffer[c * Renderer.CLIP_STRIDE + 2];

            float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
            float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;

            float surfaceNormalX = e1y * e2z - e1z * e2y;
            float surfaceNormalY = e1z * e2x - e1x * e2z;
            float surfaceNormalZ = e1x * e2y - e1y * e2x;

            float vnx = worldNormalsBuffer[a * WORLD_STRIDE];
            float vny = worldNormalsBuffer[a * WORLD_STRIDE + 1];
            float vnz = worldNormalsBuffer[a * WORLD_STRIDE + 2];

            if (vnx * surfaceNormalX + vny * surfaceNormalY + vnz * surfaceNormalZ < 0) {
                surfaceNormalX = -surfaceNormalX;
                surfaceNormalY = -surfaceNormalY;
                surfaceNormalZ = -surfaceNormalZ;
            }

            float len = (float) Math.sqrt(surfaceNormalX * surfaceNormalX + surfaceNormalY * surfaceNormalY + surfaceNormalZ * surfaceNormalZ);
            surfaceNormalX /= len;
            surfaceNormalY /= len;
            surfaceNormalZ /= len;

            float baryX = (ax + bx + cx) / 3f;
            float baryY = (ay + by + cy) / 3f;
            float baryZ = (az + bz + cz) / 3f;

            computePhong(baryX, baryY, baryZ, surfaceNormalX, surfaceNormalY, surfaceNormalZ, currScene.light, currCam, currEntity, i, out);
        }
    }

    void computeGouraudLighting(int vertexCount, float[] out) {
        for (int i = 0; i < vertexCount; i++) {

            float nx = worldNormalsBuffer[i * WORLD_STRIDE];
            float ny = worldNormalsBuffer[i * WORLD_STRIDE + 1];
            float nz = worldNormalsBuffer[i * WORLD_STRIDE + 2];


            float x = worldBuffer[i * Renderer.CLIP_STRIDE];
            float y = worldBuffer[i * Renderer.CLIP_STRIDE + 1];
            float z = worldBuffer[i * Renderer.CLIP_STRIDE + 2];

            computePhong(x, y, z, nx, ny, nz, currScene.light, currCam, currEntity, i, out);

        }
    }

    static void computePhong(float x, float y, float z, float nx, float ny, float nz, Light light, Camera camera, Entity entity, int outIndex, float[] out) {
        float ambientColorR = light.ambient.r * entity.material.ambient.r;
        float ambientColorG = light.ambient.g * entity.material.ambient.g;
        float ambientColorB = light.ambient.b * entity.material.ambient.b;

        float vertexToLightX = light.getX() - x;
        float vertexToLightY = light.getY() - y;
        float vertexToLightZ = light.getZ() - z;

        float vertexToLightNorm = (float) Math.sqrt(vertexToLightX * vertexToLightX + vertexToLightY * vertexToLightY + vertexToLightZ * vertexToLightZ);
        vertexToLightX /= vertexToLightNorm;
        vertexToLightY /= vertexToLightNorm;
        vertexToLightZ /= vertexToLightNorm;

        float diffuseDot = vertexToLightX * nx + vertexToLightY * ny + vertexToLightZ * nz;

        if (diffuseDot <= 0) {
            out[outIndex * RGB_STRIDE] = Math.min(ambientColorR, 0);
            out[outIndex * RGB_STRIDE + 1] = Math.min(ambientColorG, 0);
            out[outIndex * RGB_STRIDE + 2] = Math.min(ambientColorB, 0);
            return;
        }
        float lightToX = -vertexToLightX, lightToY = -vertexToLightY, lightToZ = -vertexToLightZ;
        float dotLightNormal = -diffuseDot;
        //reflection pointing out of vertex
        float reflectionX = lightToX - 2 * dotLightNormal * nx;
        float reflectionY = lightToY - 2 * dotLightNormal * ny;
        float reflectionZ = lightToZ - 2 * dotLightNormal * nz;

        //cam to vertex
        float vertexToViewX = camera.transform.position[0] - x;
        float vertexToViewY = camera.transform.position[1] - y;
        float vertexToViewZ = camera.transform.position[2] - z;
        float vertexToViewNorm = (float) Math.sqrt(vertexToViewX * vertexToViewX + vertexToViewY * vertexToViewY + vertexToViewZ * vertexToViewZ);
        vertexToViewX /= vertexToViewNorm;
        vertexToViewY /= vertexToViewNorm;
        vertexToViewZ /= vertexToViewNorm;

        float specDot = vertexToViewX * reflectionX + vertexToViewY * reflectionY + vertexToViewZ * reflectionZ;

        float specular;
        if (specDot <= 0)
            specular = 0;
        else if (entity.material.shininess == (int) entity.material.shininess)
            specular = powInt(specDot, (int) entity.material.shininess);
        else
            specular = (float) Math.pow(specDot, entity.material.shininess);

        float diffuse = diffuseDot; //diffuseDot <= 0 guarded above

        float diffuseColorR = light.diffuse.r * diffuse * entity.material.diffuse.r;
        float diffuseColorG = light.diffuse.g * diffuse * entity.material.diffuse.g;
        float diffuseColorB = light.diffuse.b * diffuse * entity.material.diffuse.b;

        float specularColorR = light.specular.r * specular * entity.material.specular.r;
        float specularColorG = light.specular.g * specular * entity.material.specular.g;
        float specularColorB = light.specular.b * specular * entity.material.specular.b;


        float resultColorR = Math.min(diffuseColorR + specularColorR + ambientColorR, 1f);
        float resultColorG = Math.min(diffuseColorG + specularColorG + ambientColorG, 1f);
        float resultColorB = Math.min(diffuseColorB + specularColorB + ambientColorB, 1f);

        out[outIndex * RGB_STRIDE] = resultColorR;
        out[outIndex * RGB_STRIDE + 1] = resultColorG;
        out[outIndex * RGB_STRIDE + 2] = resultColorB;
    }

    static void computeWorldToClipVertices(float[] vertices, int head, float[][] VP, float[] out, int stride) {
        for (int i = 0; i < head; i++) {

            float x = vertices[i * stride];
            float y = vertices[i * stride + 1];
            float z = vertices[i * stride + 2];
            float w = vertices[i * stride + 3];

            //to avoid float[] creation through Matrix.matmul
            Renderer.directMatmul4(out, i * Renderer.CLIP_STRIDE, VP, x, y, z, w);
        }
    }

    static void computeWorldNormals(float[] vertices, float[][] rotationMatrix, float[] out, int stride) {
        assert stride == 6 : "incorrect entity mesh stride for world normals computation";
        for (int i = 0; i < vertices.length / stride; i++) {

            float nx = vertices[i * stride + 3];
            float ny = vertices[i * stride + 4];
            float nz = vertices[i * stride + 5];

            //to avoid float[] creation through Matrix.matmul
            Renderer.directMatmul3(out, i * WORLD_STRIDE, rotationMatrix, nx, ny, nz);
        }
    }

    static void computeWorldVertices(float[] vertices, float[][] M, float[] out, int stride) {
        for (int i = 0; i < vertices.length / stride; i++) {

            float x = vertices[i * stride];
            float y = vertices[i * stride + 1];
            float z = vertices[i * stride + 2];

            //to avoid float[] creation through Matrix.matmul
            Renderer.directMatmul4(out, i * Renderer.CLIP_STRIDE, M, x, y, z, 1);
        }
    }

    private int shClipTriangles(int triangleCount, int[] indices, int[] out) {
        int postClipTriangleCount = 0;

        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleBuffer[i];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            int polyVertexCount = clipTriangleAllPlanes(a, b, c);
            if (polyVertexCount == 0) continue;  // fully outside

            float parentColorR = 0;
            float parentColorG = 0;
            float parentColorB = 0;
            if (currScene.hasLight()) {
                if (currScene.lightingType == LightingType.FLAT) {
                    parentColorR = triangleLightBuffer[i * RGB_STRIDE];
                    parentColorG = triangleLightBuffer[i * RGB_STRIDE + 1];
                    parentColorB = triangleLightBuffer[i * RGB_STRIDE + 2];
                }
            }

            for (int j = 1; j < polyVertexCount - 1; j++) {
                //read from polyIn because of last swap of polyOut and polyIn
                out[postClipTriangleCount * 3] = polyIn[0];
                out[postClipTriangleCount * 3 + 1] = polyIn[j];
                out[postClipTriangleCount * 3 + 2] = polyIn[j + 1];

                if (currScene.hasLight() && currScene.lightingType == LightingType.FLAT) {
                    postClipTriangleLightBuffer[postClipTriangleCount * RGB_STRIDE] = parentColorR;
                    postClipTriangleLightBuffer[postClipTriangleCount * RGB_STRIDE + 1] = parentColorG;
                    postClipTriangleLightBuffer[postClipTriangleCount * RGB_STRIDE + 2] = parentColorB;
                }
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
            int outCount = shClipPoly(polyIn, inCount, plane[0], plane[1], polyOut);

            if (outCount == 0) return 0;  // discarded

            int[] tmp = polyIn;
            polyIn = polyOut;
            polyOut = tmp;
            inCount = outCount;
        }

        return inCount;
    }

    private int shClipPoly(int[] polyIn, int inCount, int component, int sign, int[] polyOut) {
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

            if (!v1Outside && !v2Outside) { //both inside
                polyOut[outCount++] = edgeIdx2;
            } else if (v1Outside && v2Outside) {
            } //both outside - skip
            else { //one outside one inside
                //compute intersection point
                int i1 = edgeIdx1 * Renderer.CLIP_STRIDE;
                int i2 = edgeIdx2 * Renderer.CLIP_STRIDE;

                float d1 = clipBuffer[i1 + 3] + sign * clipBuffer[i1 + component];
                float d2 = clipBuffer[i2 + 3] + sign * clipBuffer[i2 + component];

                float t = d1 / (d1 - d2);

                clipBuffer[totalVertices * Renderer.CLIP_STRIDE] = clipBuffer[i1] + t * (clipBuffer[i2] - clipBuffer[i1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = clipBuffer[i1 + 1] + t * (clipBuffer[i2 + 1] - clipBuffer[i1 + 1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = clipBuffer[i1 + 2] + t * (clipBuffer[i2 + 2] - clipBuffer[i1 + 2]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 3] = clipBuffer[i1 + 3] + t * (clipBuffer[i2 + 3] - clipBuffer[i1 + 3]);

                if (currScene.hasLight()) {
                    if (currScene.lightingType == LightingType.GOURAUD) {
                        vertexLightBuffer[totalVertices * RGB_STRIDE] = vertexLightBuffer[edgeIdx1 * RGB_STRIDE] +
                            t * (vertexLightBuffer[edgeIdx2 * RGB_STRIDE] - vertexLightBuffer[edgeIdx1 * RGB_STRIDE]);
                        vertexLightBuffer[totalVertices * RGB_STRIDE + 1] = vertexLightBuffer[edgeIdx1 * RGB_STRIDE + 1] +
                            t * (vertexLightBuffer[edgeIdx2 * RGB_STRIDE + 1] - vertexLightBuffer[edgeIdx1 * RGB_STRIDE + 1]);
                        vertexLightBuffer[totalVertices * RGB_STRIDE + 2] = vertexLightBuffer[edgeIdx1 * RGB_STRIDE + 2] +
                            t * (vertexLightBuffer[edgeIdx2 * RGB_STRIDE + 2] - vertexLightBuffer[edgeIdx1 * RGB_STRIDE + 2]);
                    } else if (currScene.lightingType == LightingType.PHONG) {
                        worldNormalsBuffer[totalVertices * WORLD_STRIDE] = worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE] +
                            t * (worldNormalsBuffer[edgeIdx2 * WORLD_STRIDE] - worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE]);
                        worldNormalsBuffer[totalVertices * WORLD_STRIDE + 1] = worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE + 1] +
                            t * (worldNormalsBuffer[edgeIdx2 * WORLD_STRIDE + 1] - worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE + 1]);
                        worldNormalsBuffer[totalVertices * WORLD_STRIDE + 2] = worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE + 2] +
                            t * (worldNormalsBuffer[edgeIdx2 * WORLD_STRIDE + 2] - worldNormalsBuffer[edgeIdx1 * WORLD_STRIDE + 2]);

                        worldBuffer[totalVertices * Renderer.CLIP_STRIDE] = worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE] +
                            t * (worldBuffer[edgeIdx2 * Renderer.CLIP_STRIDE] - worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE]);
                        worldBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 1] +
                            t * (worldBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + 1] - worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 1]);
                        worldBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 2] +
                            t * (worldBuffer[edgeIdx2 * Renderer.CLIP_STRIDE + 2] - worldBuffer[edgeIdx1 * Renderer.CLIP_STRIDE + 2]);
                    }
                }

                if (!v1Outside) { //first inside
                    polyOut[outCount++] = totalVertices;
                } else { //second inside
                    polyOut[outCount++] = totalVertices;
                    polyOut[outCount++] = edgeIdx2;
                }

                totalVertices++;
            }
        }

        return outCount;
    }

    /*https://www.sunshine2k.de/coding/java/TriangleRasterization/TriangleRasterization.html*/
    private void rasterizeTrianglePhong(RenderOptions options, int i, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {

        int idx1 = postClipTriangleIndicesBuffer[i * 3];
        int idx2 = postClipTriangleIndicesBuffer[i * 3 + 1];
        int idx3 = postClipTriangleIndicesBuffer[i * 3 + 2];

        float aNx = worldNormalsBuffer[idx1 * WORLD_STRIDE];
        float aNy = worldNormalsBuffer[idx1 * WORLD_STRIDE + 1];
        float aNz = worldNormalsBuffer[idx1 * WORLD_STRIDE + 2];
        float bNx = worldNormalsBuffer[idx2 * WORLD_STRIDE];
        float bNy = worldNormalsBuffer[idx2 * WORLD_STRIDE + 1];
        float bNz = worldNormalsBuffer[idx2 * WORLD_STRIDE + 2];
        float cNx = worldNormalsBuffer[idx3 * WORLD_STRIDE];
        float cNy = worldNormalsBuffer[idx3 * WORLD_STRIDE + 1];
        float cNz = worldNormalsBuffer[idx3 * WORLD_STRIDE + 2];

        float aXWorld = worldBuffer[idx1 * Renderer.CLIP_STRIDE];
        float aYWorld = worldBuffer[idx1 * Renderer.CLIP_STRIDE + 1];
        float aZWorld = worldBuffer[idx1 * Renderer.CLIP_STRIDE + 2];
        float bXWorld = worldBuffer[idx2 * Renderer.CLIP_STRIDE];
        float bYWorld = worldBuffer[idx2 * Renderer.CLIP_STRIDE + 1];
        float bZWorld = worldBuffer[idx2 * Renderer.CLIP_STRIDE + 2];
        float cXWorld = worldBuffer[idx3 * Renderer.CLIP_STRIDE];
        float cYWorld = worldBuffer[idx3 * Renderer.CLIP_STRIDE + 1];
        float cZWorld = worldBuffer[idx3 * Renderer.CLIP_STRIDE + 2];

        //a top, b middle, c bottom vertex
        int aX = v1X, aY = v1Y;
        float aInvW = invW1;
        int bX = v2X, bY = v2Y;
        float bInvW = invW2;
        int cX = v3X, cY = v3Y;
        float cInvW = invW3;
        int tX, tY;
        float tInvW;
        float tNx, tNy, tNz;
        float tXWorld, tYWorld, tZWorld;


        if (aY > bY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            tNx = aNx;
            tNy = aNy;
            tNz = aNz;
            tXWorld = aXWorld;
            tYWorld = aYWorld;
            tZWorld = aZWorld;
            aX = bX;
            aY = bY;
            aInvW = bInvW;
            aNx = bNx;
            aNy = bNy;
            aNz = bNz;
            aXWorld = bXWorld;
            aYWorld = bYWorld;
            aZWorld = bZWorld;
            bX = tX;
            bY = tY;
            bInvW = tInvW;
            bNx = tNx;
            bNy = tNy;
            bNz = tNz;
            bXWorld = tXWorld;
            bYWorld = tYWorld;
            bZWorld = tZWorld;
        }
        if (aY > cY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            tNx = aNx;
            tNy = aNy;
            tNz = aNz;
            tXWorld = aXWorld;
            tYWorld = aYWorld;
            tZWorld = aZWorld;
            aX = cX;
            aY = cY;
            aInvW = cInvW;
            aNx = cNx;
            aNy = cNy;
            aNz = cNz;
            aXWorld = cXWorld;
            aYWorld = cYWorld;
            aZWorld = cZWorld;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
            cNx = tNx;
            cNy = tNy;
            cNz = tNz;
            cXWorld = tXWorld;
            cYWorld = tYWorld;
            cZWorld = tZWorld;
        }
        if (bY > cY) {
            tX = bX;
            tY = bY;
            tInvW = bInvW;
            tNx = bNx;
            tNy = bNy;
            tNz = bNz;
            tXWorld = bXWorld;
            tYWorld = bYWorld;
            tZWorld = bZWorld;
            bX = cX;
            bY = cY;
            bInvW = cInvW;
            bNx = cNx;
            bNy = cNy;
            bNz = cNz;
            bXWorld = cXWorld;
            bYWorld = cYWorld;
            bZWorld = cZWorld;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
            cNx = tNx;
            cNy = tNy;
            cNz = tNz;
            cXWorld = tXWorld;
            cYWorld = tYWorld;
            cZWorld = tZWorld;
        }

        if (cY == aY) return;

        float t = (float) (bY - aY) / (cY - aY);
        //interpolated x value for triangle separation between top and bottom
        int midX = (int) (aX + t * (cX - aX));
        float midInvW = aInvW + t * (cInvW - aInvW);

        float midNx = aNx + t * (cNx - aNx);
        float midNy = aNy + t * (cNy - aNy);
        float midNz = aNz + t * (cNz - aNz);
        float midXWorld = aXWorld + t * (cXWorld - aXWorld);
        float midYWorld = aYWorld + t * (cYWorld - aYWorld);
        float midZWorld = aZWorld + t * (cZWorld - aZWorld);

        /* here we know that aY <= bY <= cY */
        /* check for trivial case of bottom-flat triangle */
        if (bY == cY) {
            fillBottomFlatTriangle(aX, aY, aInvW, aNx, aNy, aNz, aXWorld, aYWorld, aZWorld, bX, bY, bInvW, bNx, bNy, bNz, bXWorld, bYWorld, bZWorld, cX, cY, cInvW, cNx, cNy, cNz, cXWorld, cYWorld, cZWorld);
        }
        /* check for trivial case of top-flat triangle */
        else if (aY == bY) {
            fillTopFlatTriangle(aX, aY, aInvW, aNx, aNy, aNz, aXWorld, aYWorld, aZWorld, bX, bY, bInvW, bNx, bNy, bNz, bXWorld, bYWorld, bZWorld, cX, cY, cInvW, cNx, cNy, cNz, cXWorld, cYWorld, cZWorld);
        } else {
            /* general case - split the triangle in a topflat and bottom-flat one */
            fillBottomFlatTriangle(aX, aY, aInvW, aNx, aNy, aNz, aXWorld, aYWorld, aZWorld, bX, bY, bInvW, bNx, bNy, bNz, bXWorld, bYWorld, bZWorld, midX, bY, midInvW, midNx, midNy, midNz, midXWorld, midYWorld, midZWorld);
            fillTopFlatTriangle(bX, bY, bInvW, bNx, bNy, bNz, bXWorld, bYWorld, bZWorld, midX, bY, midInvW, midNx, midNy, midNz, midXWorld, midYWorld, midZWorld, cX, cY, cInvW, cNx, cNy, cNz, cXWorld, cYWorld, cZWorld);
        }

        if (options.showWireFrame) {
            drawLine(v1X, v1Y, invW1, v2X, v2Y, invW2, 0, 0, 0);
            drawLine(v1X, v1Y, invW1, v3X, v3Y, invW3, 0, 0, 0);
            drawLine(v2X, v2Y, invW2, v3X, v3Y, invW3, 0, 0, 0);
        }
    }

    /*https://www.sunshine2k.de/coding/java/TriangleRasterization/TriangleRasterization.html*/
    private void rasterizeTriangleGouraud(RenderOptions options, int i, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {


        int idx1 = postClipTriangleIndicesBuffer[i * 3];
        int idx2 = postClipTriangleIndicesBuffer[i * 3 + 1];
        int idx3 = postClipTriangleIndicesBuffer[i * 3 + 2];

        float color1R = vertexLightBuffer[idx1 * RGB_STRIDE];
        float color1G = vertexLightBuffer[idx1 * RGB_STRIDE + 1];
        float color1B = vertexLightBuffer[idx1 * RGB_STRIDE + 2];
        float color2R = vertexLightBuffer[idx2 * RGB_STRIDE];
        float color2G = vertexLightBuffer[idx2 * RGB_STRIDE + 1];
        float color2B = vertexLightBuffer[idx2 * RGB_STRIDE + 2];
        float color3R = vertexLightBuffer[idx3 * RGB_STRIDE];
        float color3G = vertexLightBuffer[idx3 * RGB_STRIDE + 1];
        float color3B = vertexLightBuffer[idx3 * RGB_STRIDE + 2];

        scratchColorRaster1.r = color1R;
        scratchColorRaster1.g = color1G;
        scratchColorRaster1.b = color1B;
        scratchColorRaster2.r = color2R;
        scratchColorRaster2.g = color2G;
        scratchColorRaster2.b = color2B;
        scratchColorRaster3.r = color3R;
        scratchColorRaster3.g = color3G;
        scratchColorRaster3.b = color3B;

        //a top, b middle, c bottom vertex
        int aX = v1X, aY = v1Y;
        float aInvW = invW1;
        Color colorA = scratchColorRaster1;
        int bX = v2X, bY = v2Y;
        float bInvW = invW2;
        Color colorB = scratchColorRaster2;
        int cX = v3X, cY = v3Y;
        float cInvW = invW3;
        Color colorC = scratchColorRaster3;
        int tX, tY;
        float tInvW;
        Color tColor;


        if (aY > bY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            tColor = colorA;
            aX = bX;
            aY = bY;
            aInvW = bInvW;
            colorA = colorB;
            bX = tX;
            bY = tY;
            bInvW = tInvW;
            colorB = tColor;
        }
        if (aY > cY) {
            tX = aX;
            tY = aY;
            tInvW = aInvW;
            tColor = colorA;
            aX = cX;
            aY = cY;
            aInvW = cInvW;
            colorA = colorC;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
            colorC = tColor;
        }
        if (bY > cY) {
            tX = bX;
            tY = bY;
            tInvW = bInvW;
            tColor = colorB;
            bX = cX;
            bY = cY;
            bInvW = cInvW;
            colorB = colorC;
            cX = tX;
            cY = tY;
            cInvW = tInvW;
            colorC = tColor;
        }

        if (cY == aY) return;

        float t = (float) (bY - aY) / (cY - aY);
        //interpolated x value for triangle separation between top and bottom
        int midX = (int) (aX + t * (cX - aX));
        float midInvW = aInvW + t * (cInvW - aInvW);

        Color colorMid = scratchColorRaster4;
        colorMid.r = colorA.r + t * (colorC.r - colorA.r);
        colorMid.g = colorA.g + t * (colorC.g - colorA.g);
        colorMid.b = colorA.b + t * (colorC.b - colorA.b);


        /* here we know that aY <= bY <= cY */
        /* check for trivial case of bottom-flat triangle */
        if (bY == cY) {

            fillBottomFlatTriangle(colorA, aX, aY, aInvW, colorB, bX, bY, bInvW, colorC, cX, cY, cInvW);
        }
        /* check for trivial case of top-flat triangle */
        else if (aY == bY) {
            fillTopFlatTriangle(colorA, aX, aY, aInvW, colorB, bX, bY, bInvW, colorC, cX, cY, cInvW);
        } else {
            /* general case - split the triangle in a topflat and bottom-flat one */
            fillBottomFlatTriangle(colorA, aX, aY, aInvW, colorB, bX, bY, bInvW, colorMid, midX, bY, midInvW);
            fillTopFlatTriangle(colorB, bX, bY, bInvW, colorMid, midX, bY, midInvW, colorC, cX, cY, cInvW);
        }

        if (options.showWireFrame) {
            drawLine(v1X, v1Y, invW1, v2X, v2Y, invW2, 0, 0, 0);
            drawLine(v1X, v1Y, invW1, v3X, v3Y, invW3, 0, 0, 0);
            drawLine(v2X, v2Y, invW2, v3X, v3Y, invW3, 0, 0, 0);
        }
    }

    private void rasterizeTriangleFlat(RenderOptions options, Color color, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {


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

        byte rb = (byte) (r * 255);
        byte gb = (byte) (g * 255);
        byte bb = (byte) (b * 255);

        while (true) {
            int yFlip = Main.SCREEN_HEIGHT - y0 - 1;
            if (curInvW + WIRE_FRAME_DEPTH_EPSILON > frameBuffer.getDepth(x0, yFlip)) {
                frameBuffer.setPixel(x0, yFlip, rb, gb, bb);
            }
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

    private void fillBottomFlatTriangle(Color color1, int v1X, int v1Y, float invW1, Color color2, int v2X, int v2Y, float invW2, Color color3, int v3X, int v3Y, float invW3) {
        float dY1 = (v2Y - v1Y);
        float dY2 = (v3Y - v1Y);

        float invSlopeX1 = (float) (v2X - v1X) / dY1;
        float invSlopeX2 = (float) (v3X - v1X) / dY2;
        float invSlopeW1 = (invW2 - invW1) / dY1;
        float invSlopeW2 = (invW3 - invW1) / dY2;


        slopeColor1.r = (color2.r - color1.r) / dY1;
        slopeColor1.g = (color2.g - color1.g) / dY1;
        slopeColor1.b = (color2.b - color1.b) / dY1;

        slopeColor2.r = (color3.r - color1.r) / dY2;
        slopeColor2.g = (color3.g - color1.g) / dY2;
        slopeColor2.b = (color3.b - color1.b) / dY2;

        float curX1 = v1X;
        float curX2 = v1X;
        float curInvW1 = invW1;
        float curInvW2 = invW1;

        curColor1.set(color1);
        curColor2.set(color1);

        for (int scanlineY = v1Y; scanlineY <= v2Y; scanlineY++) {
            drawHorizLine((int) curX1, curInvW1, curColor1, (int) curX2, curInvW2, curColor2, scanlineY);

            curX1 += invSlopeX1;
            curX2 += invSlopeX2;
            curInvW1 += invSlopeW1;
            curInvW2 += invSlopeW2;

            curColor1.r += slopeColor1.r;
            curColor1.g += slopeColor1.g;
            curColor1.b += slopeColor1.b;
            curColor2.r += slopeColor2.r;
            curColor2.g += slopeColor2.g;
            curColor2.b += slopeColor2.b;
        }
    }

    private void fillBottomFlatTriangle(int v1X, int v1Y, float invW1, float nX1, float nY1, float nZ1, float worldX1, float worldY1, float worldZ1,
                                        int v2X, int v2Y, float invW2, float nX2, float nY2, float nZ2, float worldX2, float worldY2, float worldZ2,
                                        int v3X, int v3Y, float invW3, float nX3, float nY3, float nZ3, float worldX3, float worldY3, float worldZ3) {
        float worldXOverW1 = worldX1 * invW1;
        float worldYOverW1 = worldY1 * invW1;
        float worldZOverW1 = worldZ1 * invW1;
        float nXOverW1 = nX1 * invW1;
        float nYOverW1 = nY1 * invW1;
        float nZOverW1 = nZ1 * invW1;
        float worldXOverW2 = worldX2 * invW2;
        float worldYOverW2 = worldY2 * invW2;
        float worldZOverW2 = worldZ2 * invW2;
        float nXOverW2 = nX2 * invW2;
        float nYOverW2 = nY2 * invW2;
        float nZOverW2 = nZ2 * invW2;
        float worldXOverW3 = worldX3 * invW3;
        float worldYOverW3 = worldY3 * invW3;
        float worldZOverW3 = worldZ3 * invW3;
        float nXOverW3 = nX3 * invW3;
        float nYOverW3 = nY3 * invW3;
        float nZOverW3 = nZ3 * invW3;

        float dY1 = (v2Y - v1Y);
        float dY2 = (v3Y - v1Y);

        float dX1dY = (float) (v2X - v1X) / dY1;
        float dX2dY = (float) (v3X - v1X) / dY2;
        float dInvW1dY = (invW2 - invW1) / dY1;
        float dInvW2dY = (invW3 - invW1) / dY2;

        float dWorldX1dY = (worldXOverW2 - worldXOverW1) / dY1;
        float dWorldY1dY = (worldYOverW2 - worldYOverW1) / dY1;
        float dWorldZ1dY = (worldZOverW2 - worldZOverW1) / dY1;
        float dNX1dY = (nXOverW2 - nXOverW1) / dY1;
        float dNY1dY = (nYOverW2 - nYOverW1) / dY1;
        float dNZ1dY = (nZOverW2 - nZOverW1) / dY1;

        float dWorldX2dY = (worldXOverW3 - worldXOverW1) / dY2;
        float dWorldY2dY = (worldYOverW3 - worldYOverW1) / dY2;
        float dWorldZ2dY = (worldZOverW3 - worldZOverW1) / dY2;
        float dNX2dY = (nXOverW3 - nXOverW1) / dY2;
        float dNY2dY = (nYOverW3 - nYOverW1) / dY2;
        float dNZ2dY = (nZOverW3 - nZOverW1) / dY2;

        float curX1 = v1X, curX2 = v1X;
        float curInvW1 = invW1, curInvW2 = invW1;
        float curWorldX1 = worldXOverW1, curWorldY1 = worldYOverW1, curWorldZ1 = worldZOverW1;
        float curWorldX2 = worldXOverW1, curWorldY2 = worldYOverW1, curWorldZ2 = worldZOverW1;
        float curNX1 = nXOverW1, curNY1 = nYOverW1, curNZ1 = nZOverW1;
        float curNX2 = nXOverW1, curNY2 = nYOverW1, curNZ2 = nZOverW1;

        for (int scanlineY = v1Y; scanlineY <= v2Y; scanlineY++) {
            drawHorizLine((int) curX1, curInvW1, curWorldX1, curWorldY1, curWorldZ1, curNX1, curNY1, curNZ1,
                (int) curX2, curInvW2, curWorldX2, curWorldY2, curWorldZ2, curNX2, curNY2, curNZ2, scanlineY);

            curX1 += dX1dY;
            curX2 += dX2dY;
            curInvW1 += dInvW1dY;
            curInvW2 += dInvW2dY;
            curWorldX1 += dWorldX1dY;
            curWorldY1 += dWorldY1dY;
            curWorldZ1 += dWorldZ1dY;
            curWorldX2 += dWorldX2dY;
            curWorldY2 += dWorldY2dY;
            curWorldZ2 += dWorldZ2dY;
            curNX1 += dNX1dY;
            curNY1 += dNY1dY;
            curNZ1 += dNZ1dY;
            curNX2 += dNX2dY;
            curNY2 += dNY2dY;
            curNZ2 += dNZ2dY;
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

    private void fillTopFlatTriangle(Color color1, int v1X, int v1Y, float invW1, Color color2, int v2X, int v2Y, float invW2, Color color3, int v3X, int v3Y, float invW3) {
        float invSlopeX1 = (float) (v3X - v1X) / (v3Y - v1Y);
        float invSlopeX2 = (float) (v3X - v2X) / (v3Y - v2Y);
        float invSlopeW1 = (invW3 - invW1) / (v3Y - v1Y);
        float invSlopeW2 = (invW3 - invW2) / (v3Y - v2Y);

        float dY1 = (v3Y - v1Y);
        float dY2 = (v3Y - v2Y);

        slopeColor1.r = (color3.r - color1.r) / dY1;
        slopeColor1.g = (color3.g - color1.g) / dY1;
        slopeColor1.b = (color3.b - color1.b) / dY1;

        slopeColor2.r = (color3.r - color2.r) / dY2;
        slopeColor2.g = (color3.g - color2.g) / dY2;
        slopeColor2.b = (color3.b - color2.b) / dY2;

        float curX1 = v3X;
        float curX2 = v3X;
        float curInvW1 = invW3;
        float curInvW2 = invW3;

        curColor1.set(color3);
        curColor2.set(color3);

        for (int scanlineY = v3Y; scanlineY > v1Y; scanlineY--) {
            drawHorizLine((int) curX1, curInvW1, curColor1, (int) curX2, curInvW2, curColor2, scanlineY);
            curX1 -= invSlopeX1;
            curX2 -= invSlopeX2;
            curInvW1 -= invSlopeW1;
            curInvW2 -= invSlopeW2;

            curColor1.r -= slopeColor1.r;
            curColor1.g -= slopeColor1.g;
            curColor1.b -= slopeColor1.b;
            curColor2.r -= slopeColor2.r;
            curColor2.g -= slopeColor2.g;
            curColor2.b -= slopeColor2.b;
        }
    }

    private void fillTopFlatTriangle(int v1X, int v1Y, float invW1, float nX1, float nY1, float nZ1, float worldX1, float worldY1, float worldZ1,
                                     int v2X, int v2Y, float invW2, float nX2, float nY2, float nZ2, float worldX2, float worldY2, float worldZ2,
                                     int v3X, int v3Y, float invW3, float nX3, float nY3, float nZ3, float worldX3, float worldY3, float worldZ3) {

        float worldXOverW1 = worldX1 * invW1, worldYOverW1 = worldY1 * invW1, worldZOverW1 = worldZ1 * invW1;
        float nXOverW1 = nX1 * invW1, nYOverW1 = nY1 * invW1, nZOverW1 = nZ1 * invW1;
        float worldXOverW2 = worldX2 * invW2, worldYOverW2 = worldY2 * invW2, worldZOverW2 = worldZ2 * invW2;
        float nXOverW2 = nX2 * invW2, nYOverW2 = nY2 * invW2, nZOverW2 = nZ2 * invW2;
        float worldXOverW3 = worldX3 * invW3, worldYOverW3 = worldY3 * invW3, worldZOverW3 = worldZ3 * invW3;
        float nXOverW3 = nX3 * invW3, nYOverW3 = nY3 * invW3, nZOverW3 = nZ3 * invW3;

        float dY1 = (v3Y - v1Y);
        float dY2 = (v3Y - v2Y);

        float dX1dY = (float) (v3X - v1X) / dY1;
        float dX2dY = (float) (v3X - v2X) / dY2;
        float dInvW1dY = (invW3 - invW1) / dY1;
        float dInvW2dY = (invW3 - invW2) / dY2;

        float dWorldX1dY = (worldXOverW3 - worldXOverW1) / dY1;
        float dWorldY1dY = (worldYOverW3 - worldYOverW1) / dY1;
        float dWorldZ1dY = (worldZOverW3 - worldZOverW1) / dY1;
        float dNX1dY = (nXOverW3 - nXOverW1) / dY1;
        float dNY1dY = (nYOverW3 - nYOverW1) / dY1;
        float dNZ1dY = (nZOverW3 - nZOverW1) / dY1;

        float dWorldX2dY = (worldXOverW3 - worldXOverW2) / dY2;
        float dWorldY2dY = (worldYOverW3 - worldYOverW2) / dY2;
        float dWorldZ2dY = (worldZOverW3 - worldZOverW2) / dY2;
        float dNX2dY = (nXOverW3 - nXOverW2) / dY2;
        float dNY2dY = (nYOverW3 - nYOverW2) / dY2;
        float dNZ2dY = (nZOverW3 - nZOverW2) / dY2;

        float curX1 = v3X, curX2 = v3X;
        float curInvW1 = invW3, curInvW2 = invW3;
        float curWorldX1 = worldXOverW3, curWorldY1 = worldYOverW3, curWorldZ1 = worldZOverW3;
        float curWorldX2 = worldXOverW3, curWorldY2 = worldYOverW3, curWorldZ2 = worldZOverW3;
        float curNX1 = nXOverW3, curNY1 = nYOverW3, curNZ1 = nZOverW3;
        float curNX2 = nXOverW3, curNY2 = nYOverW3, curNZ2 = nZOverW3;

        for (int scanlineY = v3Y; scanlineY > v1Y; scanlineY--) {
            drawHorizLine((int) curX1, curInvW1, curWorldX1, curWorldY1, curWorldZ1, curNX1, curNY1, curNZ1,
                (int) curX2, curInvW2, curWorldX2, curWorldY2, curWorldZ2, curNX2, curNY2, curNZ2, scanlineY);

            curX1 -= dX1dY;
            curX2 -= dX2dY;
            curInvW1 -= dInvW1dY;
            curInvW2 -= dInvW2dY;
            curWorldX1 -= dWorldX1dY;
            curWorldY1 -= dWorldY1dY;
            curWorldZ1 -= dWorldZ1dY;
            curWorldX2 -= dWorldX2dY;
            curWorldY2 -= dWorldY2dY;
            curWorldZ2 -= dWorldZ2dY;
            curNX1 -= dNX1dY;
            curNY1 -= dNY1dY;
            curNZ1 -= dNZ1dY;
            curNX2 -= dNX2dY;
            curNY2 -= dNY2dY;
            curNZ2 -= dNZ2dY;
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

        byte r = (byte) (color.r * 255);
        byte g = (byte) (color.g * 255);
        byte b = (byte) (color.b * 255);

        for (int x = xStart; x <= xEnd; x++) {

            if (curInvW > frameBuffer.getDepth(x, yFlip)) {
                frameBuffer.setDepth(x, yFlip, curInvW);
                frameBuffer.setPixel(x, yFlip, r, g, b);
            }
            curInvW += invWSlope;
        }
    }

    private void drawHorizLine(int x1, float invW1, Color color1, int x2, float invW2, Color color2, int y) {
        int xStart = Math.min(x1, x2);
        int xEnd = Math.max(x1, x2);

        if (x1 > x2) {
            float tmpW = invW1;
            Color tmpColor = color1;
            invW1 = invW2;
            invW2 = tmpW;
            color1 = color2;
            color2 = tmpColor;
        }

        float invWSlope = (xEnd > xStart) ? (invW2 - invW1) / (xEnd - xStart) : 0;
        slopeColor.r = (xEnd > xStart) ? (color2.r - color1.r) / (xEnd - xStart) : 0;
        slopeColor.g = (xEnd > xStart) ? (color2.g - color1.g) / (xEnd - xStart) : 0;
        slopeColor.b = (xEnd > xStart) ? (color2.b - color1.b) / (xEnd - xStart) : 0;
        float curInvW = invW1;
        curColor.set(color1);

        int yFlip = Main.SCREEN_HEIGHT - y - 1;

        for (int x = xStart; x <= xEnd; x++) {

            if (curInvW > frameBuffer.getDepth(x, yFlip)) {
                byte r = (byte) (curColor.r * 255);
                byte g = (byte) (curColor.g * 255);
                byte b = (byte) (curColor.b * 255);
                frameBuffer.setDepth(x, yFlip, curInvW);
                frameBuffer.setPixel(x, yFlip, r, g, b);
            }
            curInvW += invWSlope;
            curColor.r += slopeColor.r;
            curColor.g += slopeColor.g;
            curColor.b += slopeColor.b;
        }
    }

    private void drawHorizLine(int x1, float invW1, float worldX1, float worldY1, float worldZ1, float nX1, float nY1, float nZ1,
                               int x2, float invW2, float worldX2, float worldY2, float worldZ2, float nX2, float nY2, float nZ2, int y) {
        int xStart = Math.min(x1, x2);
        int xEnd = Math.max(x1, x2);

        if (x1 > x2) {
            float tmpW = invW1;
            invW1 = invW2;
            invW2 = tmpW;
            float tmpWX = worldX1;
            worldX1 = worldX2;
            worldX2 = tmpWX;
            float tmpWY = worldY1;
            worldY1 = worldY2;
            worldY2 = tmpWY;
            float tmpWZ = worldZ1;
            worldZ1 = worldZ2;
            worldZ2 = tmpWZ;
            float tmpNX = nX1;
            nX1 = nX2;
            nX2 = tmpNX;
            float tmpNY = nY1;
            nY1 = nY2;
            nY2 = tmpNY;
            float tmpNZ = nZ1;
            nZ1 = nZ2;
            nZ2 = tmpNZ;
        }

        float dx = (xEnd > xStart) ? (xEnd - xStart) : 1;
        float dInvWdX = (invW2 - invW1) / dx;
        float dWorldXdX = (worldX2 - worldX1) / dx;
        float dWorldYdX = (worldY2 - worldY1) / dx;
        float dWorldZdX = (worldZ2 - worldZ1) / dx;
        float dNXdX = (nX2 - nX1) / dx;
        float dNYdX = (nY2 - nY1) / dx;
        float dNZdX = (nZ2 - nZ1) / dx;

        float curInvW = invW1;
        float curWorldX = worldX1, curWorldY = worldY1, curWorldZ = worldZ1;
        float curNX = nX1, curNY = nY1, curNZ = nZ1;

        int yFlip = Main.SCREEN_HEIGHT - y - 1;

        for (int x = xStart; x <= xEnd; x++) {
            if (curInvW > frameBuffer.getDepth(x, yFlip)) {
                // recover perspective-correct world position and normal
                float w = 1f / curInvW;
                float worldX = curWorldX * w;
                float worldY = curWorldY * w;
                float worldZ = curWorldZ * w;
                float nX = curNX * w;
                float nY = curNY * w;
                float nZ = curNZ * w;

                // renormalize
                float nLen = (float) Math.sqrt(nX * nX + nY * nY + nZ * nZ);
                nX /= nLen;
                nY /= nLen;
                nZ /= nLen;

                computePhong(worldX, worldY, worldZ, nX, nY, nZ, currScene.light, currCam, currEntity, 0, scratchPhong);

                byte r = (byte) (scratchPhong[0] * 255);
                byte g = (byte) (scratchPhong[1] * 255);
                byte b = (byte) (scratchPhong[2] * 255);
                frameBuffer.setDepth(x, yFlip, curInvW);
                frameBuffer.setPixel(x, yFlip, r, g, b);
            }
            curInvW += dInvWdX;
            curWorldX += dWorldXdX;
            curWorldY += dWorldYdX;
            curWorldZ += dWorldZdX;
            curNX += dNXdX;
            curNY += dNYdX;
            curNZ += dNZdX;
        }
    }

    private Color getRenderColor(int idx) {

        if (!currScene.hasLight() || currEntity.isLightObj) {
            scratchColor.set(currEntity.material.diffuse);
            return scratchColor;
        }
        float triangleColorR = postClipTriangleLightBuffer[idx * RGB_STRIDE];
        float triangleColorG = postClipTriangleLightBuffer[idx * RGB_STRIDE + 1];
        float triangleColorB = postClipTriangleLightBuffer[idx * RGB_STRIDE + 2];

        scratchColor.r = triangleColorR;
        scratchColor.g = triangleColorG;
        scratchColor.b = triangleColorB;

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
    private int cullOutsideTriangles(int[] indices, float[] clipVertices, int[] out, boolean closedMesh) {
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

            if (closedMesh) {

                if (currEntity.hasNormals) {
                    float nx1 = worldNormalsBuffer[idx1 * WORLD_STRIDE];
                    float ny1 = worldNormalsBuffer[idx1 * WORLD_STRIDE + 1];
                    float nz1 = worldNormalsBuffer[idx1 * WORLD_STRIDE + 2];

                    float vx = currCam.transform.position[0] - worldBuffer[idx1 * Renderer.CLIP_STRIDE];
                    float vy = currCam.transform.position[1] - worldBuffer[idx1 * Renderer.CLIP_STRIDE + 1];
                    float vz = currCam.transform.position[2] - worldBuffer[idx1 * Renderer.CLIP_STRIDE + 2];

                    float dot1 = nx1 * vx + ny1 * vy + nz1 * vz;

                    if (dot1 <= 0) continue;
                } else {
                    // compute face normal from two edges
                    float e1x = x2 - x1, e1y = y2 - y1, e1z = z2 - z1;
                    float e2x = x3 - x1, e2y = y3 - y1, e2z = z3 - z1;

                    // cross product → face normal
                    float fnx = e1y * e2z - e1z * e2y;
                    float fny = e1z * e2x - e1x * e2z;
                    float fnz = e1x * e2y - e1y * e2x;

                    // dot with view direction
                    float vx = -x1, vy = -y1, vz = -z1;
                    float dot = fnx * vx + fny * vy + fnz * vz;

                    if (dot >= 0) continue; //backface cull
                }

            }

            out[triangleCount++] = i;  // only add visible triangles
        }
        return triangleCount;
    }

    static float powInt(float base, int exp) {
        float result = 1f;
        while (exp > 0) {
            if ((exp & 1) == 1) result *= base;
            base *= base;
            exp >>= 1;
        }
        return result;
    }
}
