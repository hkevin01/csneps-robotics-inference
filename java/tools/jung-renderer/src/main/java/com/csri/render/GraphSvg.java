package com.csri.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;

/**
 * Enhanced JUNG renderer supporting:
 * - Fetching subgraph JSON from /subgraph endpoint
 * - SVG and JSON layout output modes
 * - Visual cues for asserted/derived/collapsed nodes and edges
 * - Multiple layout algorithms (FR, KK)
 * - Layout persistence for stable positioning
 */
public class GraphSvg {

    private static class Args {
        String subgraphUrl = null;
        String inJson = null;
        String layout = "fr";
        String outSvg = null;
        String outJson = null;
        boolean collapse = true;
        String focus = "Focus";
        int radius = 2;

        @Override
        public String toString() {
            return String.format("Args{subgraphUrl='%s', layout='%s', outSvg='%s', outJson='%s', collapse=%s}",
                                subgraphUrl, layout, outSvg, outJson, collapse);
        }
    }

    private static class GraphNode {
        String id;
        String label;
        String kind;
        boolean asserted;
        double confidence;
        int degree;

        @Override
        public String toString() {
            return label != null ? label : id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GraphNode node = (GraphNode) obj;
            return Objects.equals(id, node.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class GraphEdge {
        String id;
        String src;
        String dst;
        String label;
        String kind;
        boolean asserted;
        boolean collapsed;

        @Override
        public String toString() {
            return label != null ? label : id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GraphEdge edge = (GraphEdge) obj;
            return Objects.equals(id, edge.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class SubgraphData {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Map<String, Object> meta = new HashMap<>();
    }

    private static class Point2D {
        double x, y;
        Point2D(double x, double y) { this.x = x; this.y = y; }
    }

    public static void main(String[] args) throws Exception {
        Args a = parseArgs(args);
        System.out.println("Enhanced JUNG renderer starting with: " + a);

        // Fetch or load subgraph data
        SubgraphData data;
        if (a.subgraphUrl != null) {
            data = fetchSubgraphFromUrl(a.subgraphUrl);
        } else if (a.inJson != null) {
            data = loadSubgraphFromFile(a.inJson);
        } else {
            data = createToySubgraph(a.focus, a.radius);
        }

        System.out.println(String.format("Loaded subgraph: %d nodes, %d edges",
                                        data.nodes.size(), data.edges.size()));

        // Build JUNG graph
        Graph<GraphNode, GraphEdge> graph = buildJungGraph(data);

        // Create layout
        Layout<GraphNode, GraphEdge> layout = createLayout(graph, a.layout);

        // Generate outputs
        if (a.outSvg != null) {
            generateSimpleSvg(graph, layout, data, a.outSvg);
        }

        if (a.outJson != null) {
            generateJsonLayout(graph, layout, a.outJson);
        }

        if (a.outSvg == null && a.outJson == null) {
            System.out.println("No output specified. Use --out-svg or --out-json");
        }
    }

    private static SubgraphData fetchSubgraphFromUrl(String url) throws Exception {
        System.out.println("Fetching subgraph from: " + url);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            return client.execute(request, response -> {
                try {
                    if (response.getCode() != 200) {
                        throw new RuntimeException("Failed to fetch subgraph: " + response.getCode());
                    }
                    String json = EntityUtils.toString(response.getEntity());
                    return parseSubgraphJson(json);
                } catch (Exception e) {
                    throw new RuntimeException("Error processing subgraph response", e);
                }
            });
        }
    }

    private static SubgraphData loadSubgraphFromFile(String filename) throws Exception {
        System.out.println("Loading subgraph from file: " + filename);
        String json = Files.readString(Path.of(filename));
        return parseSubgraphJson(json);
    }

    private static SubgraphData parseSubgraphJson(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        SubgraphData data = new SubgraphData();

        // Parse nodes
        JsonNode nodesArray = root.get("nodes");
        if (nodesArray != null && nodesArray.isArray()) {
            for (JsonNode nodeJson : nodesArray) {
                GraphNode node = new GraphNode();
                node.id = nodeJson.get("id").asText();
                node.label = nodeJson.has("label") ? nodeJson.get("label").asText() : node.id;
                node.kind = nodeJson.has("kind") ? nodeJson.get("kind").asText() : "Frame";
                node.asserted = nodeJson.has("asserted") ? nodeJson.get("asserted").asBoolean() : true;
                node.confidence = nodeJson.has("confidence") ? nodeJson.get("confidence").asDouble() : 1.0;
                node.degree = nodeJson.has("degree") ? nodeJson.get("degree").asInt() : 0;

                data.nodes.add(node);
            }
        }

        // Parse edges
        JsonNode edgesArray = root.get("edges");
        if (edgesArray != null && edgesArray.isArray()) {
            for (JsonNode edgeJson : edgesArray) {
                GraphEdge edge = new GraphEdge();
                edge.id = edgeJson.get("id").asText();
                edge.src = edgeJson.get("src").asText();
                edge.dst = edgeJson.get("dst").asText();
                edge.label = edgeJson.has("label") ? edgeJson.get("label").asText() : "edge";
                edge.kind = edgeJson.has("kind") ? edgeJson.get("kind").asText() : "FrameEdge";
                edge.asserted = edgeJson.has("asserted") ? edgeJson.get("asserted").asBoolean() : true;
                edge.collapsed = edgeJson.has("collapsed") ? edgeJson.get("collapsed").asBoolean() : false;

                data.edges.add(edge);
            }
        }

        // Parse meta
        JsonNode metaNode = root.get("meta");
        if (metaNode != null) {
            metaNode.fields().forEachRemaining(entry ->
                data.meta.put(entry.getKey(), entry.getValue().asText())
            );
        }

        return data;
    }

    private static SubgraphData createToySubgraph(String focus, int radius) {
        System.out.println("Creating toy subgraph for focus: " + focus);

        SubgraphData data = new SubgraphData();

        // Create nodes
        GraphNode focusNode = new GraphNode();
        focusNode.id = focus;
        focusNode.label = focus;
        focusNode.kind = "Frame";
        focusNode.asserted = true;
        focusNode.confidence = 1.0;
        focusNode.degree = 3;
        data.nodes.add(focusNode);

        for (int i = 1; i <= radius + 1; i++) {
            GraphNode node = new GraphNode();
            node.id = focus + "-n" + i;
            node.label = focus + "-node" + i;
            node.kind = i % 2 == 0 ? "Individual" : "Frame";
            node.asserted = i <= 2;
            node.confidence = 1.0 - (i * 0.1);
            node.degree = Math.max(1, 4 - i);
            data.nodes.add(node);
        }

        // Create edges
        for (int i = 1; i <= radius + 1; i++) {
            GraphEdge edge = new GraphEdge();
            edge.id = "e" + i;
            edge.src = focus;
            edge.dst = focus + "-n" + i;
            edge.label = "relates" + i;
            edge.kind = i % 2 == 0 ? "Collapsed" : "FrameEdge";
            edge.asserted = i <= 2;
            edge.collapsed = i % 2 == 0;
            data.edges.add(edge);
        }

        return data;
    }

    private static Graph<GraphNode, GraphEdge> buildJungGraph(SubgraphData data) {
        Graph<GraphNode, GraphEdge> graph = new DirectedSparseGraph<>();

        // Create node lookup
        Map<String, GraphNode> nodeMap = new HashMap<>();
        for (GraphNode node : data.nodes) {
            graph.addVertex(node);
            nodeMap.put(node.id, node);
        }

        // Add edges
        for (GraphEdge edge : data.edges) {
            GraphNode srcNode = nodeMap.get(edge.src);
            GraphNode dstNode = nodeMap.get(edge.dst);

            if (srcNode != null && dstNode != null) {
                graph.addEdge(edge, srcNode, dstNode);
            } else {
                System.err.println("Warning: Skipping edge " + edge.id + " due to missing nodes: " +
                                 edge.src + " -> " + edge.dst);
            }
        }

        return graph;
    }

    private static Layout<GraphNode, GraphEdge> createLayout(Graph<GraphNode, GraphEdge> graph,
                                                           String layoutType) {
        Layout<GraphNode, GraphEdge> layout;

        switch (layoutType.toLowerCase()) {
            case "kk":
                layout = new KKLayout<>(graph);
                break;
            case "fr":
            default:
                layout = new FRLayout<>(graph);
                break;
        }

        // Set a reasonable size (800x600) - use reflection to avoid AWT import issues
        try {
            Class<?> dimensionClass = Class.forName("java.awt.Dimension");
            Object dimension = dimensionClass.getConstructor(int.class, int.class).newInstance(800, 600);
            layout.getClass().getMethod("setSize", dimensionClass).invoke(layout, dimension);
        } catch (Exception e) {
            System.err.println("Warning: Could not set layout size: " + e.getMessage());
        }

        return layout;
    }

    private static void generateSimpleSvg(Graph<GraphNode, GraphEdge> graph,
                                        Layout<GraphNode, GraphEdge> layout,
                                        SubgraphData data,
                                        String outputPath) throws IOException {
        System.out.println("Generating simple SVG to: " + outputPath);

        // Create a basic SVG manually
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"600\" viewBox=\"0 0 800 600\">\n");
        svg.append("<rect width=\"800\" height=\"600\" fill=\"white\"/>\n");

        // Get node positions using reflection to avoid AWT dependencies
        Map<GraphNode, Point2D> positions = new HashMap<>();
        try {
            for (GraphNode node : graph.getVertices()) {
                Object point = layout.getClass().getMethod("apply", Object.class).invoke(layout, node);
                double x = (Double) point.getClass().getMethod("getX").invoke(point);
                double y = (Double) point.getClass().getMethod("getY").invoke(point);
                positions.put(node, new Point2D(x, y));
            }
        } catch (Exception e) {
            System.err.println("Error getting positions, using fallback: " + e.getMessage());
            // Fallback: position nodes in a circle
            int i = 0;
            for (GraphNode node : graph.getVertices()) {
                double angle = 2 * Math.PI * i / graph.getVertexCount();
                double x = 400 + 200 * Math.cos(angle);
                double y = 300 + 200 * Math.sin(angle);
                positions.put(node, new Point2D(x, y));
                i++;
            }
        }

        // Draw edges first (so they appear behind nodes)
        for (GraphEdge edge : graph.getEdges()) {
            GraphNode src = graph.getEndpoints(edge).getFirst();
            GraphNode dst = graph.getEndpoints(edge).getSecond();

            Point2D srcPos = positions.get(src);
            Point2D dstPos = positions.get(dst);

            if (srcPos != null && dstPos != null) {
                String strokeColor = edge.collapsed ? "blue" : (edge.asserted ? "black" : "gray");
                String dashArray = edge.asserted ? "" : " stroke-dasharray=\"5,5\"";

                svg.append(String.format("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                                       "stroke=\"%s\" stroke-width=\"1.5\"%s/>\n",
                                       srcPos.x, srcPos.y, dstPos.x, dstPos.y,
                                       strokeColor, dashArray));

                // Add edge label
                double midX = (srcPos.x + dstPos.x) / 2;
                double midY = (srcPos.y + dstPos.y) / 2;
                svg.append(String.format("<text x=\"%.1f\" y=\"%.1f\" font-family=\"Arial\" font-size=\"10\" " +
                                       "text-anchor=\"middle\" fill=\"%s\">%s</text>\n",
                                       midX, midY - 5, strokeColor, edge.label));
            }
        }

        // Draw nodes
        for (GraphNode node : graph.getVertices()) {
            Point2D pos = positions.get(node);
            if (pos != null) {
                double size = 10 + node.confidence * 20;
                String fillColor = getNodeColor(node);
                String strokeColor = node.asserted ? "black" : "gray";
                String dashArray = node.asserted ? "" : " stroke-dasharray=\"3,3\"";

                double alpha = node.confidence;

                svg.append(String.format("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
                                       "fill=\"%s\" fill-opacity=\"%.2f\" stroke=\"%s\" stroke-width=\"2\"%s/>\n",
                                       pos.x - size/2, pos.y - size/2, size, size,
                                       fillColor, alpha, strokeColor, dashArray));

                // Add node label
                svg.append(String.format("<text x=\"%.1f\" y=\"%.1f\" font-family=\"Arial\" font-size=\"12\" " +
                                       "text-anchor=\"middle\" fill=\"black\">%s</text>\n",
                                       pos.x, pos.y + 4, node.label));
            }
        }

        svg.append("</svg>\n");

        // Write SVG file
        Path outPath = Path.of(outputPath);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        Files.writeString(outPath, svg.toString());

        System.out.println("SVG written successfully");
    }

    private static String getNodeColor(GraphNode node) {
        switch (node.kind.toLowerCase()) {
            case "individual": return "cyan";
            case "proposition": return "yellow";
            case "role": return "orange";
            default: return "lightgray";
        }
    }

    private static void generateJsonLayout(Graph<GraphNode, GraphEdge> graph,
                                         Layout<GraphNode, GraphEdge> layout,
                                         String outputPath) throws IOException {
        System.out.println("Generating JSON layout to: " + outputPath);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> result = new HashMap<>();

        // Extract node positions
        List<Map<String, Object>> nodes = new ArrayList<>();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        try {
            for (GraphNode node : graph.getVertices()) {
                Object point = layout.getClass().getMethod("apply", Object.class).invoke(layout, node);
                double x = (Double) point.getClass().getMethod("getX").invoke(point);
                double y = (Double) point.getClass().getMethod("getY").invoke(point);

                Map<String, Object> nodePos = new HashMap<>();
                nodePos.put("id", node.id);
                nodePos.put("x", x);
                nodePos.put("y", y);
                nodePos.put("label", node.label);
                nodePos.put("kind", node.kind);
                nodePos.put("asserted", node.asserted);
                nodePos.put("confidence", node.confidence);
                nodes.add(nodePos);

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        } catch (Exception e) {
            System.err.println("Error getting positions for JSON: " + e.getMessage());
            // Fallback positioning
            int i = 0;
            for (GraphNode node : graph.getVertices()) {
                double angle = 2 * Math.PI * i / graph.getVertexCount();
                double x = 400 + 200 * Math.cos(angle);
                double y = 300 + 200 * Math.sin(angle);

                Map<String, Object> nodePos = new HashMap<>();
                nodePos.put("id", node.id);
                nodePos.put("x", x);
                nodePos.put("y", y);
                nodePos.put("label", node.label);
                nodePos.put("kind", node.kind);
                nodePos.put("asserted", node.asserted);
                nodePos.put("confidence", node.confidence);
                nodes.add(nodePos);

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                i++;
            }
        }

        result.put("nodes", nodes);

        // Extract edge information
        List<Map<String, Object>> edges = new ArrayList<>();
        for (GraphEdge edge : graph.getEdges()) {
            Map<String, Object> edgeInfo = new HashMap<>();
            edgeInfo.put("id", edge.id);
            edgeInfo.put("src", edge.src);
            edgeInfo.put("dst", edge.dst);
            edgeInfo.put("label", edge.label);
            edgeInfo.put("kind", edge.kind);
            edgeInfo.put("asserted", edge.asserted);
            edgeInfo.put("collapsed", edge.collapsed);
            edges.add(edgeInfo);
        }

        result.put("edges", edges);

        // Meta information
        Map<String, Object> meta = new HashMap<>();
        meta.put("viewBox", Arrays.asList(minX, minY, maxX - minX, maxY - minY));
        meta.put("layout", layout.getClass().getSimpleName());
        meta.put("nodeCount", nodes.size());
        meta.put("edgeCount", edges.size());
        meta.put("timestamp", System.currentTimeMillis());
        result.put("meta", meta);

        // Write JSON file
        Path outPath = Path.of(outputPath);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), result);

        System.out.println("JSON layout written successfully");
    }

    private static Args parseArgs(String[] argv) {
        Args args = new Args();

        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--subgraph-url":
                    args.subgraphUrl = argv[++i];
                    break;
                case "--in-json":
                    args.inJson = argv[++i];
                    break;
                case "--layout":
                    args.layout = argv[++i];
                    break;
                case "--out-svg":
                    args.outSvg = argv[++i];
                    break;
                case "--out-json":
                    args.outJson = argv[++i];
                    break;
                case "--collapse":
                    args.collapse = Boolean.parseBoolean(argv[++i]);
                    break;
                case "--focus":
                    args.focus = argv[++i];
                    break;
                case "--radius":
                    args.radius = Integer.parseInt(argv[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + argv[i]);
            }
        }

        return args;
    }
}
