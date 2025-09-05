package com.csri.kg.service.web;

import com.csri.kg.service.visualization.SubgraphLayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for subgraph visualization and layout services.
 */
@RestController
@RequestMapping("/api/visualization")
public class VisualizationController {
    private static final Logger logger = LoggerFactory.getLogger(VisualizationController.class);

    @Autowired
    private SubgraphLayoutService layoutService;

    /**
     * Generate layout coordinates for a concept subgraph.
     */
    @GetMapping("/layout/{concept}")
    public ResponseEntity<Map<String, Object>> generateLayout(
            @PathVariable String concept,
            @RequestParam(defaultValue = "FORCE_DIRECTED") String layoutType,
            @RequestParam(defaultValue = "3") int maxDepth) {

        try {
            logger.info("Generating layout for concept: {} with type: {}", concept, layoutType);

            SubgraphLayoutService.LayoutType layout = SubgraphLayoutService.LayoutType.valueOf(layoutType.toUpperCase());
            SubgraphLayoutService.SubgraphLayout result = layoutService.generateLayout(concept, layout, maxDepth);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("concept", result.concept);
            response.put("layoutType", result.layoutType);
            response.put("nodeCount", result.nodeCount);
            response.put("edgeCount", result.edgeCount);
            response.put("timestamp", result.timestamp);
            response.put("coordinates", result.coordinates);
            response.put("nodes", result.nodes);
            response.put("edges", result.edges);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating layout for concept: {}", concept, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("concept", concept);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Export layout coordinates as JSON.
     */
    @GetMapping("/layout/{concept}/export")
    public ResponseEntity<String> exportLayout(
            @PathVariable String concept,
            @RequestParam(defaultValue = "FORCE_DIRECTED") String layoutType,
            @RequestParam(defaultValue = "3") int maxDepth) {

        try {
            logger.info("Exporting layout for concept: {}", concept);

            SubgraphLayoutService.LayoutType layout = SubgraphLayoutService.LayoutType.valueOf(layoutType.toUpperCase());
            SubgraphLayoutService.SubgraphLayout result = layoutService.generateLayout(concept, layout, maxDepth);

            String jsonExport = layoutService.exportLayoutJson(result);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .header("Content-Disposition", "attachment; filename=\"" + concept + "_layout.json\"")
                    .body(jsonExport);

        } catch (Exception e) {
            logger.error("Error exporting layout for concept: {}", concept, e);
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Get available layout types.
     */
    @GetMapping("/layout-types")
    public ResponseEntity<Map<String, Object>> getLayoutTypes() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("layoutTypes", SubgraphLayoutService.LayoutType.values());
        response.put("descriptions", Map.of(
            "CIRCLE", "Arrange nodes in a circle",
            "GRID", "Arrange nodes in a grid pattern",
            "FORCE_DIRECTED", "Physics-based force-directed layout",
            "HIERARCHICAL", "Hierarchical layout based on node types"
        ));

        return ResponseEntity.ok(response);
    }
}
