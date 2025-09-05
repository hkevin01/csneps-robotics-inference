package com.csri.kg.service.visualization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.SpringLayout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.Renderer;
import org.apache.commons.collections15.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Enhanced JUNG-based subgraph renderer that fetches real CSNePS subgraphs
 * and generates interactive visualizations with layout coordinates export.
 */
@Service
public class EnhancedSubgraphRenderer {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSubgraphRenderer.class);

    @Autowired
    private com.csri.kg.service.client.CsnepsBridgeClient csnepsBridgeClient;

    private final ObjectMapper objectMapper;

    public EnhancedSubgraphRenderer() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Render a subgraph for the given concept using CSNePS bridge.
     */
    public SubgraphVisualization renderSubgraph(String concept, LayoutType layoutType, int maxDepth) {
        try {
            logger.info("Rendering subgraph for concept: {} with layout: {}", concept, layoutType);

            // Fetch real subgraph from CSNePS
            Object subgraphResponse = csnepsBridgeClient.getSubgraph(concept, maxDepth);
            JsonNode subgraphJson = objectMapper.valueToTree(subgraphResponse);

            // Parse the subgraph JSON
            SubgraphData subgraphData = parseSubgraphJson(subgraphJson);

            // Create JUNG graph
            Graph<GraphNode, GraphEdge> graph = buildJungGraph(subgraphData);

            // Apply layout
            Layout<GraphNode, GraphEdge> layout = createLayout(graph, layoutType);

            // Generate visualization
            SubgraphVisualization visualization = generateVisualization(graph, layout, concept);

            logger.info("Subgraph rendered successfully: {} nodes, {} edges",
                       subgraphData.nodes.size(), subgraphData.edges.size());

            return visualization;

        } catch (Exception e) {
            logger.error("Error rendering subgraph for concept: {}", concept, e);
            throw new RuntimeException("Failed to render subgraph", e);
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
     * Build JUNG graph from parsed subgraph data.
     */
    private Graph<GraphNode, GraphEdge> buildJungGraph(SubgraphData data) {
        Graph<GraphNode, GraphEdge> graph = new DirectedSparseGraph<>();

        // Add all nodes
        for (GraphNode node : data.nodes.values()) {
            graph.addVertex(node);
        }

        // Add all edges
        for (GraphEdge edge : data.edges.values()) {
            GraphNode sourceNode = data.nodes.get(edge.source);
            GraphNode targetNode = data.nodes.get(edge.target);

            if (sourceNode != null && targetNode != null) {
                graph.addEdge(edge, sourceNode, targetNode);
            } else {
                logger.warn("Skipping edge {} due to missing nodes: {} -> {}",
                           edge.id, edge.source, edge.target);
            }
        }

        return graph;
    }

    /**
     * Create the specified layout for the graph.
     */
    private Layout<GraphNode, GraphEdge> createLayout(Graph<GraphNode, GraphEdge> graph, LayoutType layoutType) {
        Dimension size = new Dimension(800, 600);

        switch (layoutType) {
            case FORCE_DIRECTED:
                return new FRLayout<>(graph, size);
            case SPRING:
                return new SpringLayout<>(graph);
            case KAMADA_KAWAI:
                return new KKLayout<>(graph);
            case CIRCLE:
                return new CircleLayout<>(graph);
            default:
                return new FRLayout<>(graph, size);
        }
    }

    /**
     * Generate the complete visualization with coordinates.
     */
    private SubgraphVisualization generateVisualization(Graph<GraphNode, GraphEdge> graph,
                                                        Layout<GraphNode, GraphEdge> layout,
                                                        String concept) {
        SubgraphVisualization visualization = new SubgraphVisualization();
        visualization.concept = concept;
        visualization.nodeCount = graph.getVertexCount();
        visualization.edgeCount = graph.getEdgeCount();
        visualization.coordinates = new HashMap<>();
        visualization.graphData = extractGraphData(graph);

        // Extract coordinates from layout
        for (GraphNode node : graph.getVertices()) {
            Point2D point = layout.transform(node);
            NodeCoordinates coords = new NodeCoordinates();
            coords.x = point.getX();
            coords.y = point.getY();
            coords.nodeId = node.id;

            visualization.coordinates.put(node.id, coords);
        }

        // Create interactive visualization component
        visualization.visualizationComponent = createVisualizationComponent(graph, layout);

        return visualization;
    }

    /**
     * Create Swing component for interactive visualization.
     */
    private JComponent createVisualizationComponent(Graph<GraphNode, GraphEdge> graph,
                                                   Layout<GraphNode, GraphEdge> layout) {
        BasicVisualizationServer<GraphNode, GraphEdge> vv =
            new BasicVisualizationServer<>(layout);

        vv.setPreferredSize(new Dimension(800, 600));

        // Configure node rendering
        vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller<GraphNode>() {
            @Override
            public String transform(GraphNode node) {
                return node.label;
            }
        });

        // Configure edge rendering
        vv.getRenderContext().setEdgeLabelTransformer(new ToStringLabeller<GraphEdge>() {
            @Override
            public String transform(GraphEdge edge) {
                return edge.relation;
            }
        });

        // Configure node colors based on type
        vv.getRenderContext().setVertexFillPaintTransformer(new Transformer<GraphNode, Paint>() {
            @Override
            public Paint transform(GraphNode node) {
                switch (node.type.toLowerCase()) {
                    case "concept": return Color.LIGHT_GRAY;
                    case "individual": return Color.CYAN;
                    case "property": return Color.YELLOW;
                    case "rule": return Color.ORANGE;
                    default: return Color.WHITE;
                }
            }
        });

        // Configure edge colors based on confidence/weight
        vv.getRenderContext().setEdgeDrawPaintTransformer(new Transformer<GraphEdge, Paint>() {
            @Override
            public Paint transform(GraphEdge edge) {
                if (edge.weight > 0.8) return Color.GREEN;
                if (edge.weight > 0.5) return Color.BLUE;
                if (edge.weight > 0.2) return Color.ORANGE;
                return Color.RED;
            }
        });

        // Configure node size based on confidence
        vv.getRenderContext().setVertexShapeTransformer(new Transformer<GraphNode, Shape>() {
            @Override
            public Shape transform(GraphNode node) {
                int size = (int) (10 + node.confidence * 20);
                return new java.awt.geom.Ellipse2D.Double(-size/2, -size/2, size, size);
            }
        });

        // Center labels
        vv.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.CNTR);

        return vv;
    }

    /**
     * Extract graph data for export.
     */
    private GraphData extractGraphData(Graph<GraphNode, GraphEdge> graph) {
        GraphData data = new GraphData();
        data.nodes = new HashMap<>();
        data.edges = new HashMap<>();

        // Extract nodes
        for (GraphNode node : graph.getVertices()) {
            data.nodes.put(node.id, node);
        }

        // Extract edges
        for (GraphEdge edge : graph.getEdges()) {
            data.edges.put(edge.id, edge);
        }

        return data;
    }

    /**
     * Export visualization coordinates to JSON format.
     */
    public String exportCoordinates(SubgraphVisualization visualization) {
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("concept", visualization.concept);
            export.put("nodeCount", visualization.nodeCount);
            export.put("edgeCount", visualization.edgeCount);
            export.put("coordinates", visualization.coordinates);
            export.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(export);
        } catch (Exception e) {
            logger.error("Error exporting coordinates", e);
            throw new RuntimeException("Failed to export coordinates", e);
        }
    }

    // Data structures
    public enum LayoutType {
        FORCE_DIRECTED,
        SPRING,
        KAMADA_KAWAI,
        CIRCLE
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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GraphNode node = (GraphNode) obj;
            return id.equals(node.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GraphEdge edge = (GraphEdge) obj;
            return id.equals(edge.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static class SubgraphVisualization {
        public String concept;
        public int nodeCount;
        public int edgeCount;
        public Map<String, NodeCoordinates> coordinates;
        public GraphData graphData;
        public JComponent visualizationComponent;
    }

    public static class NodeCoordinates {
        public String nodeId;
        public double x;
        public double y;
    }

    public static class GraphData {
        public Map<String, GraphNode> nodes;
        public Map<String, GraphEdge> edges;
    }
}
