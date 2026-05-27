package com.engine;

import com.badlogic.gdx.graphics.Color;

public class SolidRenderer {

    private static final RenderOptions DEFAULT_RENDER_OPTIONS = new RenderOptions();
    private static final float WIRE_FRAME_DEPTH_EPSILON = 1e-5f;
    private static final int WORLD_STRIDE = 3;
    private static final int TRIANGLE_LIGHT_STRIDE = 3; //for each component of triangle color
    private static final int VERTEX_LIGHT_STRIDE = 3; //for each component of vertex color
    private static final int POST_CLIP_TRIANGLE_LIGHT_STRIDE = 3; //for each component of vertex color

    private final FrameBuffer frameBuffer;
    /**
     * stores clip-space vertices
     */
    private float[] clipBuffer;
    private float[] worldBuffer;
    private float[] worldNormalsBuffer;

    private float[] triangleLightBuffer; //flat face shading
    //    private float[] triangleVerticesLightBuffer; //gouraud shading
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
    private float[] screenBuffer;

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
        worldBuffer = new float[currentMaxVertices * Renderer.CLIP_STRIDE];
        clipBuffer = new float[currentMaxVertices * Renderer.CLIP_STRIDE * 4];
        triangleBuffer = new int[currentMaxVertices * 2];
        postClipTriangleIndicesBuffer = new int[currentMaxVertices * 2 * 7 * 3];
        screenBuffer = new float[currentMaxVertices * 2 * 7 * 3 * 3];

        if (currScene.hasLight()) {
            worldNormalsBuffer = new float[currentMaxVertices * WORLD_STRIDE];
//            if (currScene.lightingType == LightingType.FLAT) {
            triangleLightBuffer = new float[currentMaxVertices * 2 * 3];
//            }
//            if (currScene.lightingType == LightingType.GOURAUD) {
            vertexLightBuffer = new float[currentMaxVertices * 4 * 3];
//            }
//            else if  (currScene.lightingType == LightingType.GOURAUD) {
            postClipTriangleLightBuffer = new float[currentMaxVertices * 2 * 3 * 3 * 7];
//            }
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


        if (currScene.hasLight()) {

            computeWorldVertices(vertices, M, worldBuffer, vertexStride);

            if (currEntity.hasNormals)
                //matrix recomputed and stored when toMatrix() called for M computation above
                computeWorldNormals(vertices, currEntity.transform.rotation.matrix, worldNormalsBuffer, vertexStride);

            computeWorldToClipVertices(worldBuffer, VP, clipBuffer, Renderer.CLIP_STRIDE);

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
            } else {
//            for (int i = 0; i < triangleCount; i++) {
//                //set diffusion to 1, spectral to 0 (no light)
//                triangleLightBuffer[i] = (i % 2 == 0) ? 1f : 0f;
//            }
//            Arrays.fill(triangleLightBuffer, 0, triangleCount, 1f);
            }

        int postClipTriangleCount = SHClipTriangles(triangleCount, indices, postClipTriangleIndicesBuffer, postClipTriangleLightBuffer);

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

        displayTriangles(options, postClipTriangleCount);

        currEntity = null;
    }


    private void displayTriangles(RenderOptions options, int postClipTriangleCount) {
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

            Color color = getRenderColor(options, i);
            rasterizeTriangle(options, color, i, screenX1, screenY1, invW1, screenX2, screenY2, invW2, screenX3, screenY3, invW3);

        }
    }

    void computeFlatLighting(int triangleCount, int[] indices, float[] out) {
        for (int i = 0; i < triangleCount; i++) {
            int idx = triangleBuffer[i];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            float n1x = worldNormalsBuffer[a * WORLD_STRIDE];
            float n1y = worldNormalsBuffer[a * WORLD_STRIDE + 1];
            float n1z = worldNormalsBuffer[a * WORLD_STRIDE + 2];
            float n2x = worldNormalsBuffer[b * WORLD_STRIDE];
            float n2y = worldNormalsBuffer[b * WORLD_STRIDE + 1];
            float n2z = worldNormalsBuffer[b * WORLD_STRIDE + 2];
            float n3x = worldNormalsBuffer[c * WORLD_STRIDE];
            float n3y = worldNormalsBuffer[c * WORLD_STRIDE + 1];
            float n3z = worldNormalsBuffer[c * WORLD_STRIDE + 2];

            //triangle surface normal
            float surfaceNormalX = (n1x + n2x + n3x) / 3f;
            float surfaceNormalY = (n1y + n2y + n3y) / 3f;
            float surfaceNormalZ = (n1z + n2z + n3z) / 3f;

            float len = (float) Math.sqrt(surfaceNormalX * surfaceNormalX + surfaceNormalY * surfaceNormalY + surfaceNormalZ * surfaceNormalZ);
            surfaceNormalX /= len;
            surfaceNormalY /= len;
            surfaceNormalZ /= len;

            float baryX = (worldBuffer[a * Renderer.CLIP_STRIDE] + worldBuffer[b * Renderer.CLIP_STRIDE] + worldBuffer[c * Renderer.CLIP_STRIDE]) / 3f;
            float baryY = (worldBuffer[a * Renderer.CLIP_STRIDE + 1] + worldBuffer[b * Renderer.CLIP_STRIDE + 1] + worldBuffer[c * Renderer.CLIP_STRIDE + 1]) / 3f;
            float baryZ = (worldBuffer[a * Renderer.CLIP_STRIDE + 2] + worldBuffer[b * Renderer.CLIP_STRIDE + 2] + worldBuffer[c * Renderer.CLIP_STRIDE + 2]) / 3f;

            float lightX = currScene.light.getPosition()[0];
            float lightY = currScene.light.getPosition()[1];
            float lightZ = currScene.light.getPosition()[2];

            float baryToLightX = lightX - baryX;
            float baryToLightY = lightY - baryY;
            float baryToLightZ = lightZ - baryZ;

            float len2 = (float) Math.sqrt(baryToLightX * baryToLightX + baryToLightY * baryToLightY + baryToLightZ * baryToLightZ);
            baryToLightX /= len2;
            baryToLightY /= len2;
            baryToLightZ /= len2;

            float diffuseDot = baryToLightX * surfaceNormalX + baryToLightY * surfaceNormalY + baryToLightZ * surfaceNormalZ;
            float diffuse = Math.max(0f, diffuseDot);

            float lightToBaryX = -baryToLightX, lightToBaryY = -baryToLightY, lightToBaryZ = -baryToLightZ;
            float dotLightNormal = lightToBaryX * surfaceNormalX + lightToBaryY * surfaceNormalY + lightToBaryZ * surfaceNormalZ;
            //reflection pointing out of surface
            float reflectionX = lightToBaryX - 2 * dotLightNormal * surfaceNormalX;
            float reflectionY = lightToBaryY - 2 * dotLightNormal * surfaceNormalY;
            float reflectionZ = lightToBaryZ - 2 * dotLightNormal * surfaceNormalZ;

            //cam to barycenter
            float viewX = currCam.transform.position[0] - baryX;
            float viewY = currCam.transform.position[1] - baryY;
            float viewZ = currCam.transform.position[2] - baryZ;
            float vLen = (float) Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ);
            viewX /= vLen;
            viewY /= vLen;
            viewZ /= vLen;

            float specDot = viewX * reflectionX + viewY * reflectionY + viewZ * reflectionZ;
            float specular = (float) Math.pow(Math.max(specDot, 0f), currEntity.material.shininess);

            float diffuseColorR = currScene.light.diffuse.r * diffuse * currEntity.material.diffuse.r;
            float diffuseColorG = currScene.light.diffuse.g * diffuse * currEntity.material.diffuse.g;
            float diffuseColorB = currScene.light.diffuse.b * diffuse * currEntity.material.diffuse.b;

            float specularColorR = currScene.light.specular.r * specular * currEntity.material.specular.r;
            float specularColorG = currScene.light.specular.g * specular * currEntity.material.specular.g;
            float specularColorB = currScene.light.specular.b * specular * currEntity.material.specular.b;

            float ambientColorR = currScene.light.ambient.r * currEntity.material.ambient.r;
            float ambientColorG = currScene.light.ambient.g * currEntity.material.ambient.g;
            float ambientColorB = currScene.light.ambient.b * currEntity.material.ambient.b;

            float resultColorR = Math.min(diffuseColorR + specularColorR + ambientColorR, 1f);
            float resultColorG = Math.min(diffuseColorG + specularColorG + ambientColorG, 1f);
            float resultColorB = Math.min(diffuseColorB + specularColorB + ambientColorB, 1f);

            out[i * TRIANGLE_LIGHT_STRIDE] = resultColorR;
            out[i * TRIANGLE_LIGHT_STRIDE + 1] = resultColorG;
            out[i * TRIANGLE_LIGHT_STRIDE + 2] = resultColorB;
        }
    }

    void computeGouraudLighting(int vertexCount, float[] out) {
        float lightX = currScene.light.getPosition()[0];
        float lightY = currScene.light.getPosition()[1];
        float lightZ = currScene.light.getPosition()[2];
        for (int i = 0; i < vertexCount; i++) {

            float nx = worldNormalsBuffer[i * WORLD_STRIDE];
            float ny = worldNormalsBuffer[i * WORLD_STRIDE + 1];
            float nz = worldNormalsBuffer[i * WORLD_STRIDE + 2];


            float x = worldBuffer[i * Renderer.CLIP_STRIDE];
            float y = worldBuffer[i * Renderer.CLIP_STRIDE + 1];
            float z = worldBuffer[i * Renderer.CLIP_STRIDE + 2];


            float vertexToLightX = lightX - x;
            float vertexToLightY = lightY - y;
            float vertexToLightZ = lightZ - z;

            float vertexToLightNorm = (float) Math.sqrt(vertexToLightX * vertexToLightX + vertexToLightY * vertexToLightY + vertexToLightZ * vertexToLightZ);
            vertexToLightX /= vertexToLightNorm;
            vertexToLightY /= vertexToLightNorm;
            vertexToLightZ /= vertexToLightNorm;

            float diffuseDot = vertexToLightX * nx + vertexToLightY * ny + vertexToLightZ * nz;

            float lightToX = -vertexToLightX, lightToY = -vertexToLightY, lightToZ = -vertexToLightZ;
            float dotLightNormal = -diffuseDot;
            //reflection pointing out of vertex
            float reflectionX = lightToX - 2 * dotLightNormal * nx;
            float reflectionY = lightToY - 2 * dotLightNormal * ny;
            float reflectionZ = lightToZ - 2 * dotLightNormal * nz;

            //cam to vertex
            float vertexToViewX = currCam.transform.position[0] - x;
            float vertexToViewY = currCam.transform.position[1] - y;
            float vertexToViewZ = currCam.transform.position[2] - z;
            float vertexToViewNorm = (float) Math.sqrt(vertexToViewX * vertexToViewX + vertexToViewY * vertexToViewY + vertexToViewZ * vertexToViewZ);
            vertexToViewX /= vertexToViewNorm;
            vertexToViewY /= vertexToViewNorm;
            vertexToViewZ /= vertexToViewNorm;

            float specDot = vertexToViewX * reflectionX + vertexToViewY * reflectionY + vertexToViewZ * reflectionZ;

            float specular = (float) Math.pow(Math.max(specDot, 0f), currEntity.material.shininess);
            float diffuse = Math.max(diffuseDot, 0f);

            float diffuseColorR = currScene.light.diffuse.r * diffuse * currEntity.material.diffuse.r;
            float diffuseColorG = currScene.light.diffuse.g * diffuse * currEntity.material.diffuse.g;
            float diffuseColorB = currScene.light.diffuse.b * diffuse * currEntity.material.diffuse.b;

            float specularColorR = currScene.light.specular.r * specular * currEntity.material.specular.r;
            float specularColorG = currScene.light.specular.g * specular * currEntity.material.specular.g;
            float specularColorB = currScene.light.specular.b * specular * currEntity.material.specular.b;

            float ambientColorR = currScene.light.ambient.r * currEntity.material.ambient.r;
            float ambientColorG = currScene.light.ambient.g * currEntity.material.ambient.g;
            float ambientColorB = currScene.light.ambient.b * currEntity.material.ambient.b;

            float resultColorR = Math.min(diffuseColorR + specularColorR + ambientColorR, 1f);
            float resultColorG = Math.min(diffuseColorG + specularColorG + ambientColorG, 1f);
            float resultColorB = Math.min(diffuseColorB + specularColorB + ambientColorB, 1f);

            out[i * VERTEX_LIGHT_STRIDE] = resultColorR;
            out[i * VERTEX_LIGHT_STRIDE + 1] = resultColorG;
            out[i * VERTEX_LIGHT_STRIDE + 2] = resultColorB;
        }
    }

    static void computeWorldToClipVertices(float[] vertices, float[][] VP, float[] out, int stride) {
        for (int i = 0; i < vertices.length / stride; i++) {

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

    private int SHClipTriangles(int triangleCount, int[] indices, int[] out, float[] lightOut) {
        int postClipTriangleCount = 0;

        for (int i = 0; i < triangleCount; i++) {
//            int idx = triangleBuffer[triangleOrderBuffer[i]];
            int idx = triangleBuffer[i];
            int a = indices[idx];
            int b = indices[idx + 1];
            int c = indices[idx + 2];

            int polyVertexCount = clipTriangleAllPlanes(a, b, c);
            if (polyVertexCount == 0) continue;  // fully outside

//            float parentDiffusion = 0, parentSpecular = 0;
            float parentColorR = 0;
            float parentColorG = 0;
            float parentColorB = 0;
//            float parentIntensityA = 0;
//            float parentIntensityB = 0;
//            float parentIntensityC = 0;
            if (currScene.hasLight()) {
                if (currScene.lightingType == LightingType.FLAT) {
                    parentColorR = triangleLightBuffer[i * TRIANGLE_LIGHT_STRIDE];
                    parentColorG = triangleLightBuffer[i * TRIANGLE_LIGHT_STRIDE + 1];
                    parentColorB = triangleLightBuffer[i * TRIANGLE_LIGHT_STRIDE + 2];
                }
//                else if (currScene.lightingType == LightingType.GOURAUD) {
//                    parentIntensityA = vertexLightBuffer[a];
//                    parentIntensityB = vertexLightBuffer[b];
//                    parentIntensityC = vertexLightBuffer[c];
//                }
            }

            for (int j = 1; j < polyVertexCount - 1; j++) {
                //read from polyIn because of last swap of polyOut and polyIn
                out[postClipTriangleCount * 3] = polyIn[0];
                out[postClipTriangleCount * 3 + 1] = polyIn[j];
                out[postClipTriangleCount * 3 + 2] = polyIn[j + 1];

                if (currScene.hasLight()) {
                    if (currScene.lightingType == LightingType.FLAT) {
                        lightOut[postClipTriangleCount * TRIANGLE_LIGHT_STRIDE] = parentColorR;
                        lightOut[postClipTriangleCount * TRIANGLE_LIGHT_STRIDE + 1] = parentColorG;
                        lightOut[postClipTriangleCount * TRIANGLE_LIGHT_STRIDE + 2] = parentColorB;
                    } else if (currScene.lightingType == LightingType.GOURAUD) {
                        lightOut[(postClipTriangleCount * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE] = vertexLightBuffer[polyIn[0] * VERTEX_LIGHT_STRIDE];
                        lightOut[(postClipTriangleCount * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1] = vertexLightBuffer[polyIn[0] * VERTEX_LIGHT_STRIDE + 1];
                        lightOut[(postClipTriangleCount * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2] = vertexLightBuffer[polyIn[0] * VERTEX_LIGHT_STRIDE + 2];
                        lightOut[(postClipTriangleCount * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE] = vertexLightBuffer[polyIn[j] * VERTEX_LIGHT_STRIDE];
                        lightOut[(postClipTriangleCount * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1] = vertexLightBuffer[polyIn[j] * VERTEX_LIGHT_STRIDE + 1];
                        lightOut[(postClipTriangleCount * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2] = vertexLightBuffer[polyIn[j] * VERTEX_LIGHT_STRIDE + 2];
                        lightOut[(postClipTriangleCount * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE] = vertexLightBuffer[polyIn[j + 1] * VERTEX_LIGHT_STRIDE];
                        lightOut[(postClipTriangleCount * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1] = vertexLightBuffer[polyIn[j + 1] * VERTEX_LIGHT_STRIDE + 1];
                        lightOut[(postClipTriangleCount * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2] = vertexLightBuffer[polyIn[j + 1] * VERTEX_LIGHT_STRIDE + 2];
                    }
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

                float t = d1 / (d1 - d2);

                clipBuffer[totalVertices * Renderer.CLIP_STRIDE] = clipBuffer[i1] + t * (clipBuffer[i2] - clipBuffer[i1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 1] = clipBuffer[i1 + 1] + t * (clipBuffer[i2 + 1] - clipBuffer[i1 + 1]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 2] = clipBuffer[i1 + 2] + t * (clipBuffer[i2 + 2] - clipBuffer[i1 + 2]);
                clipBuffer[totalVertices * Renderer.CLIP_STRIDE + 3] = clipBuffer[i1 + 3] + t * (clipBuffer[i2 + 3] - clipBuffer[i1 + 3]);

                if (currScene.hasLight() && currScene.lightingType == LightingType.GOURAUD) {
                    vertexLightBuffer[totalVertices * VERTEX_LIGHT_STRIDE] = vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE] +
                        t * (vertexLightBuffer[edgeIdx2 * VERTEX_LIGHT_STRIDE] - vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE]);
                    vertexLightBuffer[totalVertices * VERTEX_LIGHT_STRIDE + 1] = vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE + 1] +
                        t * (vertexLightBuffer[edgeIdx2 * VERTEX_LIGHT_STRIDE + 1] - vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE + 1]);
                    vertexLightBuffer[totalVertices * VERTEX_LIGHT_STRIDE + 2] = vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE + 2] +
                        t * (vertexLightBuffer[edgeIdx2 * VERTEX_LIGHT_STRIDE + 2] - vertexLightBuffer[edgeIdx1 * VERTEX_LIGHT_STRIDE + 2]);
                }
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
    private void rasterizeTriangle(RenderOptions options, Color color, int idx, int v1X, int v1Y, float invW1, int v2X, int v2Y, float invW2, int v3X, int v3Y, float invW3) {


        Color color1 = scratchColorRaster1, color2 = scratchColorRaster2, color3 = scratchColorRaster3;

        if (!currEntity.isLightObj && currScene.hasLight() && currScene.lightingType == LightingType.GOURAUD) {
            float color1R = postClipTriangleLightBuffer[(idx * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE];
            float color1G = postClipTriangleLightBuffer[(idx * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1];
            float color1B = postClipTriangleLightBuffer[(idx * 3) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2];
            float color2R = postClipTriangleLightBuffer[(idx * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE];
            float color2G = postClipTriangleLightBuffer[(idx * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1];
            float color2B = postClipTriangleLightBuffer[(idx * 3 + 1) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2];
            float color3R = postClipTriangleLightBuffer[(idx * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE];
            float color3G = postClipTriangleLightBuffer[(idx * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1];
            float color3B = postClipTriangleLightBuffer[(idx * 3 + 2) * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2];

            scratchColorRaster1.r = color1R;
            scratchColorRaster1.g = color1G;
            scratchColorRaster1.b = color1B;
            scratchColorRaster2.r = color2R;
            scratchColorRaster2.g = color2G;
            scratchColorRaster2.b = color2B;
            scratchColorRaster3.r = color3R;
            scratchColorRaster3.g = color3G;
            scratchColorRaster3.b = color3B;
        }

        //a top, b middle, c bottom vertex
        int aX = v1X, aY = v1Y;
        float aInvW = invW1;
        Color colorA = color1;
        int bX = v2X, bY = v2Y;
        float bInvW = invW2;
        Color colorB = color2;
        int cX = v3X, cY = v3Y;
        float cInvW = invW3;
        Color colorC = color3;
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
        if (!currEntity.isLightObj && currScene.hasLight() && currScene.lightingType == LightingType.GOURAUD) {
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
        } else {
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
//                frameBuffer.setDepth(x0, yFlip, curInvW);
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

    private Color getRenderColor(RenderOptions options, int idx) {

        if (currEntity.isLightObj || !currScene.hasLight() || currScene.lightingType == LightingType.GOURAUD)
            scratchColor.set(currEntity.material.diffuse);
        else {

            float triangleColorR = postClipTriangleLightBuffer[idx * POST_CLIP_TRIANGLE_LIGHT_STRIDE];
            float triangleColorG = postClipTriangleLightBuffer[idx * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 1];
            float triangleColorB = postClipTriangleLightBuffer[idx * POST_CLIP_TRIANGLE_LIGHT_STRIDE + 2];

            scratchColor.r = triangleColorR;
            scratchColor.g = triangleColorG;
            scratchColor.b = triangleColorB;
        }

        return scratchColor;

//        if (!options.randomizeTexture) {
//            return color;
//        }
//
//        // deterministic pseudo-random hash from all 3 vertex indices
//        int hash = (idx1 * 92837111) ^ (idx2 * 689287499) ^ (idx3 * 283823481);
//        float offset = ((hash & 0xFF) / 255f - 0.5f) * 0.12f; // range [-0.06, 0.06]
//
//        scratchColor.set(Math.clamp(color.r + offset, 0, 1), Math.clamp(color.g + offset, 0, 1), Math.clamp(color.b + offset, 0, 1), 1f);
//        return scratchColor;
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

            out[triangleCount++] = i;  // only add visible triangles
        }
        return triangleCount;
    }
}
