package com.hosteleats.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CampusService {

    public static class NodeDistance implements Comparable<NodeDistance> {
        private final int distance;
        private final String node;

        public NodeDistance(int distance, String node) {
            this.distance = distance;
            this.node = node;
        }

        public int getDistance() {
            return distance;
        }

        public String getNode() {
            return node;
        }

        @Override
        public int compareTo(NodeDistance other) {
            return Integer.compare(this.distance, other.distance);
        }
    }

    private final Map<String, Map<String, Integer>> graph = new HashMap<>();

    public CampusService() {
        initCampusGraph();
    }

    private void addEdge(String u, String v, int weight) {
        graph.computeIfAbsent(u, k -> new HashMap<>()).put(v, weight);
        graph.computeIfAbsent(v, k -> new HashMap<>()).put(u, weight);
    }

    private void initCampusGraph() {
        // Hostel A <-> Library: 5, Quad: 10, Hostel B: 7
        addEdge("Hostel A", "Library", 5);
        addEdge("Hostel A", "Quad", 10);
        addEdge("Hostel A", "Hostel B", 7);

        // Hostel B <-> Library: 2, Gym: 4, Hostel A: 7
        addEdge("Hostel B", "Library", 2);
        addEdge("Hostel B", "Gym", 4);

        // Hostel C <-> Gym: 4, Canteen: 8
        addEdge("Hostel C", "Gym", 4);
        addEdge("Hostel C", "Canteen", 8);

        // Library <-> Quad: 2, Canteen: 12, Science Block: 3, Hostel A: 5, Hostel B: 2
        addEdge("Library", "Quad", 2);
        addEdge("Library", "Canteen", 12);
        addEdge("Library", "Science Block", 3);

        // Quad <-> Gym: 6, Canteen: 4, Hostel A: 10, Library: 2
        addEdge("Quad", "Gym", 6);
        addEdge("Quad", "Canteen", 4);

        // Gym <-> Canteen: 3, Hostel C: 4, Hostel B: 4, Quad: 6
        addEdge("Gym", "Canteen", 3);

        // Auditorium <-> Quad: 3, Sports Ground: 5, Science Block: 6
        addEdge("Auditorium", "Quad", 3);
        addEdge("Auditorium", "Sports Ground", 5);
        addEdge("Auditorium", "Science Block", 6);

        // Sports Ground <-> Auditorium: 5, Gym: 4
        addEdge("Sports Ground", "Gym", 4);

        // Science Block <-> Library: 3, Auditorium: 6
    }

    public Map<String, Object> findShortestPath(String source, String target) {
        return findShortestPathInGraph(this.graph, source, target);
    }

    public Map<String, Object> findShortestPathInGraph(Map<String, Map<String, Integer>> customGraph, String source, String target) {
        if (!customGraph.containsKey(source) || !customGraph.containsKey(target)) {
            return Map.of("minutes", -1, "path", List.of());
        }

        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>();

        for (String node : customGraph.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }
        distances.put(source, 0);
        queue.add(new NodeDistance(0, source));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            String node = current.getNode();
            int dist = current.getDistance();

            if (dist > distances.get(node)) {
                continue;
            }

            if (node.equals(target)) {
                break;
            }

            Map<String, Integer> neighbors = customGraph.getOrDefault(node, Collections.emptyMap());
            for (Map.Entry<String, Integer> entry : neighbors.entrySet()) {
                String neighbor = entry.getKey();
                int weight = entry.getValue();

                // BUG 4 INJECTION:
                // Compares raw edge weight instead of cumulative distance (distance + weight)
                // distances.put(neighbor, weight) instead of dist + weight
                if (weight < distances.get(neighbor)) {
                    distances.put(neighbor, weight);
                    previous.put(neighbor, node);
                    queue.add(new NodeDistance(distances.get(neighbor), neighbor));
                }
            }
        }

        if (distances.get(target) == Integer.MAX_VALUE) {
            return Map.of("minutes", -1, "path", List.of());
        }

        List<String> path = new ArrayList<>();
        String curr = target;
        while (curr != null) {
            path.add(0, curr);
            curr = previous.get(curr);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("minutes", distances.get(target));
        result.put("path", path);
        return result;
    }

    public Map<String, Map<String, Integer>> getGraph() {
        return graph;
    }
}
