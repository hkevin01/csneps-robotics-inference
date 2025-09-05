package com.csri.kg.service.visualization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplified subgraph renderer that fetches CSNePS subgraphs and generates
 * layout coordinates without GUI dependencies for server environments.
 */
@Service
public class SubgraphLayoutService {
    private static final Logger logger = LoggerFactory.getLogger(SubgraphLayoutService.class);

    @Autowired
    private com.csri.kg.service.client.CsnepsBridgeClient csnepsBridgeClient;

    private final ObjectMapper objectMapper;

    public SubgraphLayoutService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate layout coordinates for a subgraph concept.
     */
    public SubgraphLayout generateLayout(String concept, LayoutType layoutType, int maxDepth) {
        try {
            logger.info("Generating layout for concept: {} with type: {}", concept, layoutType);

            // Fetch real subgraph from CSNePS
            Object subgraphResponse = csnepsBridgeClient.getSubgraph(concept, maxDepth);
            JsonNode subgraphJson = objectMapper.valueToTree(subgraphResponse);

            // Parse the subgraph JSON
            SubgraphData subgraphData = parseSubgraphJson(subgraphJson);

            // Generate layout coordinates
            Map<String, NodeCoordinates> coordinates = generateCoordinates(subgraphData, layoutType);

            // Create layout result
            SubgraphLayout layout = new SubgraphLayout();
            layout.concept = concept;
            layout.layoutType = layoutType;
            layout.nodeCount = subgraphData.nodes.size();
            layout.edgeCount = subgraphData.edges.size();
            layout.coordinates = coordinates;
            layout.nodes = subgraphData.nodes;
            layout.edges = subgraphData.edges;
            layout.timestamp = System.currentTimeMillis();

            logger.info("Layout generated successfully: {} nodes, {} edges",
                       layout.nodeCount, layout.edgeCount);

            return layout;

        } catch (Exception e) {
            logger.error("Error generating layout for concept: {}", concept, e);
            throw new RuntimeException("Failed to generate subgraph layout", e);
        }
    }

    /**
     * Parse the CSNePS subgraph JSON response.
     */
    private SubgraphData parseSubgraphJson(JsonNode subgraphJson) {
        SubgraphData data = new SubgraphData();
        data.nodes = new HashMap<>();
        data.edges = new HashMap<>();

        // Parse nodes
        JsonNode nodesArray = subgraphJson.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode nodeJson : nodesArray) {
                GraphNode node = new GraphNode();
                node.id = nodeJson.get("id").asText();
                node.label = nodeJson.has("label") ? nodeJson.get("label").asText() : node.id;
                node.type = nodeJson.has("type") ? nodeJson.get("type").asText() : "concept";
                node.confidence = nodeJson.has("confidence") ? nodeJson.get("confidence").asDouble() : 1.0;

                // Parse metadata
                if (nodeJson.has("metadata")) {
                    JsonNode metadata = nodeJson.get("metadata");
                    node.metadata = new HashMap<>();
                    metadata.fields().forEachRemaining(entry ->
                        node.metadata.put(entry.getKey(), entry.getValue().asText())
                    );
                }

                data.nodes.put(node.id, node);
            }
        }

        // Parse edges
        JsonNode edgesArray = subgraphJson.get("edges");
        if (edgesArray != null && edgesArray.isArray()) {
            for (JsonNode edgeJson : edgesArray) {
                GraphEdge edge = new GraphEdge();
                edge.id = edgeJson.get("id").asText();
                edge.source = edgeJson.get("source").asText();
                edge.target = edgeJson.get("target").asText();
                edge.relation = edgeJson.has("relation") ? edgeJson.get("relation").asText() : "related";
                edge.weight = edgeJson.has("weight") ? edgeJson.get("weight").asDouble() : 1.0;

                // Parse metadata
                if (edgeJson.has("metadata")) {
                    JsonNode metadata = edgeJson.get("metadata");
                    edge.metadata = new HashMap<>();
                    metadata.fields().forEachRemaining(entry ->
                        edge.metadata.put(entry.getKey(), entry.getValue().asText())
                    );
                }

                data.edges.put(edge.id, edge);
            }
        }

        return data;
    }

    /**
     * Generate layout coordinates using simple algorithms.
     */
    private Map<String, NodeCoordinates> generateCoordinates(SubgraphData data, LayoutType layoutType) {
        Map<String, NodeCoordinates> coordinates = new HashMap<>();
        List<GraphNode> nodeList = new ArrayList<>(data.nodes.values());

        switch (layoutType) {
            case CIRCLE:
                generateCircleLayout(nodeList, coordinates);
                break;
            case GRID:
                generateGridLayout(nodeList, coordinates);
                break;
            case FORCE_DIRECTED:
                generateForceDirectedLayout(nodeList, data.edges, coordinates);
                break;
            case HIERARCHICAL:
                generateHierarchicalLayout(nodeList, data.edges, coordinates);
                break;
            default:
                generateCircleLayout(nodeList, coordinates);
        }

        return coordinates;
    }

    /**
     * Generate circle layout coordinates.
     */
    private void generateCircleLayout(List<GraphNode> nodes, Map<String, NodeCoordinates> coordinates) {
        double centerX = 400;
        double centerY = 300;
        double radius = Math.min(centerX, centerY) * 0.8;

        for (int i = 0; i < nodes.size(); i++) {
            GraphNode node = nodes.get(i);
            double angle = 2 * Math.PI * i / nodes.size();

            NodeCoordinates coord = new NodeCoordinates();
            coord.nodeId = node.id;
            coord.x = centerX + radius * Math.cos(angle);
            coord.y = centerY + radius * Math.sin(angle);

            coordinates.put(node.id, coord);
        }
    }

    /**
     * Generate grid layout coordinates.
     */
    private void generateGridLayout(List<GraphNode> nodes, Map<String, NodeCoordinates> coordinates) {
        int cols = (int) Math.ceil(Math.sqrt(nodes.size()));
        int rows = (int) Math.ceil((double) nodes.size() / cols);

        double cellWidth = 800.0 / cols;
        double cellHeight = 600.0 / rows;

        for (int i = 0; i < nodes.size(); i++) {
            GraphNode node = nodes.get(i);
            int row = i / cols;
            int col = i % cols;

            NodeCoordinates coord = new NodeCoordinates();
            coord.nodeId = node.id;
            coord.x = col * cellWidth + cellWidth / 2;
            coord.y = row * cellHeight + cellHeight / 2;

            coordinates.put(node.id, coord);
        }
    }

    /**
     * Generate simple force-directed layout using basic physics simulation.
     */
    private void generateForceDirectedLayout(List<GraphNode> nodes, Map<String, GraphEdge> edges,
                                           Map<String, NodeCoordinates> coordinates) {
        // Initialize random positions
        Map<String, Point> positions = new HashMap<>();
        for (GraphNode node : nodes) {
            positions.put(node.id, new Point(
                Math.random() * 800,
                Math.random() * 600
            ));
        }

        // Simple force-directed algorithm
        int iterations = 100;
        double k = Math.sqrt(800 * 600 / nodes.size()); // Optimal distance

        for (int iter = 0; iter < iterations; iter++) {
            Map<String, Point> forces = new HashMap<>();

            // Initialize forces
            for (GraphNode node : nodes) {
                forces.put(node.id, new Point(0, 0));
            }

            // Repulsive forces (all pairs)
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    GraphNode node1 = nodes.get(i);
                    GraphNode node2 = nodes.get(j);

                    Point pos1 = positions.get(node1.id);
                    Point pos2 = positions.get(node2.id);

                    double dx = pos1.x - pos2.x;
                    double dy = pos1.y - pos2.y;
                    double distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));

                    double repulsion = k * k / distance;
                    double fx = repulsion * dx / distance;
                    double fy = repulsion * dy / distance;

                    Point force1 = forces.get(node1.id);
                    Point force2 = forces.get(node2.id);

                    force1.x += fx;
                    force1.y += fy;
                    force2.x -= fx;
                    force2.y -= fy;
                }
            }

            // Attractive forces (connected nodes)
            for (GraphEdge edge : edges.values()) {
                Point pos1 = positions.get(edge.source);
                Point pos2 = positions.get(edge.target);

                if (pos1 != null && pos2 != null) {
                    double dx = pos2.x - pos1.x;
                    double dy = pos2.y - pos1.y;
                    double distance = Math.max(1, Math.sqrt(dx * dx + dy * dy));

                    double attraction = distance * distance / k;
                    double fx = attraction * dx / distance;
                    double fy = attraction * dy / distance;

                    Point force1 = forces.get(edge.source);
                    Point force2 = forces.get(edge.target);

                    if (force1 != null && force2 != null) {
                        force1.x += fx;
                        force1.y += fy;
                        force2.x -= fx;
                        force2.y -= fy;
                    }
                }
            }

            // Apply forces
            double temperature = 0.1 * (iterations - iter) / iterations;
            for (GraphNode node : nodes) {
                Point pos = positions.get(node.id);
                Point force = forces.get(node.id);

                pos.x += force.x * temperature;
                pos.y += force.y * temperature;

                // Keep within bounds
                pos.x = Math.max(50, Math.min(750, pos.x));
                pos.y = Math.max(50, Math.min(550, pos.y));
            }
        }

        // Convert to coordinates
        for (GraphNode node : nodes) {
            Point pos = positions.get(node.id);
            NodeCoordinates coord = new NodeCoordinates();
            coord.nodeId = node.id;
            coord.x = pos.x;
            coord.y = pos.y;

            coordinates.put(node.id, coord);
        }
    }

    /**
     * Generate hierarchical layout based on node types and relationships.
     */
    private void generateHierarchicalLayout(List<GraphNode> nodes, Map<String, GraphEdge> edges,
                                          Map<String, NodeCoordinates> coordinates) {
        // Group nodes by type
        Map<String, List<GraphNode>> typeGroups = new HashMap<>();
        for (GraphNode node : nodes) {
            typeGroups.computeIfAbsent(node.type, k -> new ArrayList<>()).add(node);
        }

        // Assign Y levels based on type hierarchy
        Map<String, Integer> typeLevels = new HashMap<>();
        typeLevels.put("concept", 0);
        typeLevels.put("individual", 1);
        typeLevels.put("property", 2);
        typeLevels.put("rule", 3);

        double levelHeight = 600.0 / Math.max(1, typeLevels.size());

        for (Map.Entry<String, List<GraphNode>> entry : typeGroups.entrySet()) {
            String type = entry.getKey();
            List<GraphNode> typeNodes = entry.getValue();

            int level = typeLevels.getOrDefault(type, 0);
            double y = level * levelHeight + levelHeight / 2;

            // Distribute nodes horizontally within level
            double nodeWidth = 800.0 / Math.max(1, typeNodes.size());

            for (int i = 0; i < typeNodes.size(); i++) {
                GraphNode node = typeNodes.get(i);
                NodeCoordinates coord = new NodeCoordinates();
                coord.nodeId = node.id;
                coord.x = i * nodeWidth + nodeWidth / 2;
                coord.y = y;

                coordinates.put(node.id, coord);
            }
        }
    }

    /**
     * Export layout to JSON format.
     */
    public String exportLayoutJson(SubgraphLayout layout) {
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("concept", layout.concept);
            export.put("layoutType", layout.layoutType.toString());
            export.put("nodeCount", layout.nodeCount);
            export.put("edgeCount", layout.edgeCount);
            export.put("timestamp", layout.timestamp);
            export.put("coordinates", layout.coordinates);

            // Add graph structure
            Map<String, Object> graphData = new HashMap<>();
            graphData.put("nodes", layout.nodes);
            graphData.put("edges", layout.edges);
            export.put("graph", graphData);

            return objectMapper.writeValueAsString(export);
        } catch (Exception e) {
            logger.error("Error exporting layout JSON", e);
            throw new RuntimeException("Failed to export layout", e);
        }
    }

    // Data structures
    public enum LayoutType {
        CIRCLE,
        GRID,
        FORCE_DIRECTED,
        HIERARCHICAL
    }

    public static class SubgraphData {
        public Map<String, GraphNode> nodes;
        public Map<String, GraphEdge> edges;
    }

    public static class GraphNode {
        public String id;
        public String label;
        public String type;
        public double confidence;
        public Map<String, String> metadata;

        @Override
        public String toString() {
            return label != null ? label : id;
        }
    }

    public static class GraphEdge {
        public String id;
        public String source;
        public String target;
        public String relation;
        public double weight;
        public Map<String, String> metadata;

        @Override
        public String toString() {
            return relation != null ? relation : id;
        }
    }

    public static class SubgraphLayout {
        public String concept;
        public LayoutType layoutType;
        public int nodeCount;
        public int edgeCount;
        public long timestamp;
        public Map<String, NodeCoordinates> coordinates;
        public Map<String, GraphNode> nodes;
        public Map<String, GraphEdge> edges;
    }

    public static class NodeCoordinates {
        public String nodeId;
        public double x;
        public double y;
    }

    private static class Point {
        public double x;
        public double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
