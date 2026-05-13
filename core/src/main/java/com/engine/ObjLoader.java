package com.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;

import java.io.*;
import java.util.*;

/**
 * Parses a .obj file into flat float arrays ready for a Mesh.
 * Also parses the associated .mtl file for material colors.
 * Usage:
 *   ObjLoader.Result r = ObjLoader.load("path/to/model.obj");
 *   r.vertices   → [x, y, z,  x, y, z, ...]
 *   r.normals    → [nx, ny, nz,  nx, ny, nz, ...]  (may be empty)
 *   r.uvs        → [u, v,  u, v, ...]               (may be empty)
 *   r.indices    → [i0, i1, i2, ...]
 *   r.color      → diffuse color from .mtl (white if no .mtl found)
 */
public class ObjLoader {


    private static final Color DEFAULT_COLOR = Color.BLUE;

    public static class Result {
        public float[] vertices;
        public float[] normals;
        public float[] uvs;
        public int[]   indices;
        public Color   color = DEFAULT_COLOR;

        public boolean hasNormals() { return normals.length > 0; }
        public boolean hasUVs()     { return uvs.length > 0; }

        @Override
        public String toString() {
            return String.format(
                "ObjLoader.Result { vertices: %d, normals: %d, uvs: %d, indices: %d, color: %s }",
                vertices.length / 3,
                normals.length  / 3,
                uvs.length      / 2,
                indices.length  / 3,
                color.toString()
            );
        }
    }

    public static Result load(String path) throws IOException {

        path = "obj-mtl/"+path;

        List<float[]> rawPositions = new ArrayList<>();
        List<float[]> rawNormals   = new ArrayList<>();
        List<float[]> rawUVs       = new ArrayList<>();

        Map<String, Integer> indexMap = new LinkedHashMap<>();

        List<Float>   positions = new ArrayList<>();
        List<Float>   normals   = new ArrayList<>();
        List<Float>   uvs       = new ArrayList<>();
        List<Integer> indices   = new ArrayList<>();

        String mtlFile        = null;
        String activeMaterial = null;

        // --- Parse .obj ---
        try (BufferedReader br = new BufferedReader(Gdx.files.internal(path).reader())) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("mtllib ")) {
                    mtlFile = line.substring(7).trim();

                } else if (line.startsWith("usemtl ")) {
                    activeMaterial = line.substring(7).trim();

                } else if (line.startsWith("v ")) {
                    String[] t = line.split("\\s+");
                    rawPositions.add(new float[]{
                        Float.parseFloat(t[1]),
                        Float.parseFloat(t[2]),
                        Float.parseFloat(t[3])
                    });

                } else if (line.startsWith("vn ")) {
                    String[] t = line.split("\\s+");
                    rawNormals.add(new float[]{
                        Float.parseFloat(t[1]),
                        Float.parseFloat(t[2]),
                        Float.parseFloat(t[3])
                    });

                } else if (line.startsWith("vt ")) {
                    String[] t = line.split("\\s+");
                    rawUVs.add(new float[]{
                        Float.parseFloat(t[1]),
                        Float.parseFloat(t[2])
                    });

                } else if (line.startsWith("f ")) {
                    String[] t = line.split("\\s+");
                    int[] faceIndices = new int[t.length - 1];

                    for (int i = 1; i < t.length; i++) {
                        String key = t[i];
                        if (!indexMap.containsKey(key)) {
                            indexMap.put(key, indexMap.size());
                            String[] parts = key.split("/", -1);

                            int posIdx = Integer.parseInt(parts[0]) - 1;
                            float[] pos = rawPositions.get(posIdx);
                            positions.add(pos[0]);
                            positions.add(pos[1]);
                            positions.add(pos[2]);

                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                int uvIdx = Integer.parseInt(parts[1]) - 1;
                                float[] uv = rawUVs.get(uvIdx);
                                uvs.add(uv[0]);
                                uvs.add(uv[1]);
                            }

                            if (parts.length > 2 && !parts[2].isEmpty()) {
                                int normIdx = Integer.parseInt(parts[2]) - 1;
                                float[] norm = rawNormals.get(normIdx);
                                normals.add(norm[0]);
                                normals.add(norm[1]);
                                normals.add(norm[2]);
                            }
                        }
                        faceIndices[i - 1] = indexMap.get(key);
                    }

                    for (int i = 1; i < faceIndices.length - 1; i++) {
                        indices.add(faceIndices[0]);
                        indices.add(faceIndices[i]);
                        indices.add(faceIndices[i + 1]);
                    }
                }
            }
        }

        Result result   = new Result();
        result.vertices = toFloatArray(positions);
        result.normals  = toFloatArray(normals);
        result.uvs      = toFloatArray(uvs);
        result.indices  = toIntArray(indices);

        // --- Parse .mtl if referenced ---
//        System.out.println("mtllib: " + mtlFile);
//        System.out.println("mtlPath: " + parentDir(path) + mtlFile);

        if (mtlFile != null) {
            String mtlPath = parentDir(path) + mtlFile;
            result.color = parseMtl(mtlPath, activeMaterial);
        }

        return result;
    }

    /**
     * Parses a .mtl file and returns the diffuse color (Kd) of the given material.
     * Falls back to white if the material or Kd is not found.
     */
    private static Color parseMtl(String mtlPath, String targetMaterial) {
        if (!Gdx.files.internal(mtlPath).exists()) {
            System.out.println("File not found: " + mtlPath);
            return DEFAULT_COLOR;

        }
        try (BufferedReader br = new BufferedReader(Gdx.files.internal(mtlPath).reader())) {
            String line;
            boolean inTarget = (targetMaterial == null); // no usemtl → take first Kd

            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("newmtl ")) {
                    String name = line.substring(7).trim();
                    inTarget = targetMaterial == null || name.equals(targetMaterial);

                } else if (inTarget && line.startsWith("Kd ")) {
                    String[] t = line.split("\\s+");
                    float r = Float.parseFloat(t[1]);
                    float g = Float.parseFloat(t[2]);
                    float b = Float.parseFloat(t[3]);
                    return new Color(r, g, b, 1f);
                }
            }
        } catch (IOException e) {
            System.err.println("ObjLoader: could not read .mtl: " + mtlPath);
        }

        return DEFAULT_COLOR;
    }

    /** "models/cube.obj" → "models/"  |  "cube.obj" → "" */
    private static String parentDir(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "";
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
