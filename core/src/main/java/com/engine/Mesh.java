package com.engine;

import java.util.*;

public class Mesh {
     final float[] vertices;
     final int[] indices;
     final int[] edges;
//     final int stride;

    public Mesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
//        this.stride = stride;
        this.edges = getDeduplicatedEdges(indices);

//        System.out.println("Vertices: " + vertices.length);
//        System.out.println("Indices: " + edges.length);
//        System.out.println("Edges: " + edges.length);
    }

    static int[] getDeduplicatedEdges(int[] indices) {

        Set<Long> seenEdges = new HashSet<>();
        List<Integer> edgeList = new ArrayList<>();

        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i], b = indices[i+1], c = indices[i+2];
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
