package com.engine;

import java.util.*;

public class Mesh {
    final float[] vertices;
    final int[] indices;
    final int[] edges;
    final int stride;

    private Mesh(float[] vertices, int[] indices, int stride) {
        this.vertices = vertices;
        this.indices = indices;
        this.stride = stride;

        this.edges = getDeduplicatedEdges(indices);
    }

    public Mesh(float[] vertices, int[] indices) {
        this(vertices, indices, 3);
    }

    public Mesh(float[] vertices, float[] normals, int[] indices) {
        assert vertices.length == normals.length: "attempted to create mesh with incorrect # of normals";

        float[] mergedVerticesNormals = new float[vertices.length * 2];

        for (int i = 0; i < vertices.length / 3; i++) {
            mergedVerticesNormals[i * 6] = vertices[i * 3];
            mergedVerticesNormals[i * 6 + 1] = vertices[i * 3 + 1];
            mergedVerticesNormals[i * 6 + 2] = vertices[i * 3 + 2];
            mergedVerticesNormals[i * 6 + 3] = normals[i * 3];
            mergedVerticesNormals[i * 6 + 4] = normals[i * 3 + 1];
            mergedVerticesNormals[i * 6 + 5] = normals[i * 3 + 2];
        }

        this(mergedVerticesNormals, indices, 6);
    }

    static int[] getDeduplicatedEdges(int[] indices) {

        Set<Long> seenEdges = new HashSet<>();
        List<Integer> edgeList = new ArrayList<>();

        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            addIfNew(seenEdges, edgeList, a, b);
            addIfNew(seenEdges, edgeList, b, c);
            addIfNew(seenEdges, edgeList, a, c);
        }

        return edgeList.stream().mapToInt(Integer::intValue).toArray();
    }

    static void addIfNew(Set<Long> seen, List<Integer> edgeList, int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        long key = ((long) lo << 32) | hi;  // pack two ints into one long
        if (seen.add(key)) {
            edgeList.add(lo);
            edgeList.add(hi);
        }
    }
}
