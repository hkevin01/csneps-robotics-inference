package com.csri.kg.service.web;

import com.csri.kg.service.client.CsnepsBridgeClient;
import com.csri.kg.service.validation.ShaclValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Enhanced REST controller for Knowledge Graph operations.
 * Integrates SHACL validation and CSNePS Clojure bridge.
 *
 * Features:
 * - SHACL validation gate for incoming assertions
 * - Direct integration with CSNePS bridge
 * - Comprehensive error handling and logging
 * - Production-ready endpoints with proper HTTP status codes
 */
@RestController
@RequestMapping("/api/graph")
public class EnhancedGraphController {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedGraphController.class);

    @Autowired
    private CsnepsBridgeClient csnepsBridge;

    @Autowired
    private ShaclValidationService shaclValidator;

    /**
     * Assert a single statement with SHACL validation.
     *
     * @param assertion The assertion to validate and assert
     * @return Response with validation and assertion results
     */
    @PostMapping("/assert")
    public ResponseEntity<Map<String, Object>> assertStatement(@RequestBody Map<String, Object> assertion) {
        try {
            logger.info("Received assertion request: {}", assertion.get("predicate"));

            // Step 1: SHACL Validation
            ShaclValidationService.ValidationResult validation = shaclValidator.validateAssertion(assertion);

            if (!validation.isValid()) {
                logger.warn("SHACL validation failed for assertion: {}", validation.getViolations());

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "SHACL validation failed");
                errorResponse.put("validation", validation.toResponseMap());
                errorResponse.put("timestamp", new Date());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.debug("SHACL validation passed for assertion");

            // Step 2: Assert via CSNePS bridge
            Map<String, Object> bridgeResponse = csnepsBridge.assertStatement(assertion);

            // Step 3: Combine validation and assertion results
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("validation", validation.toResponseMap());
            response.put("assertion_result", bridgeResponse);
            response.put("timestamp", new Date());

            Boolean bridgeSuccess = (Boolean) bridgeResponse.get("success");
            if (bridgeSuccess != null && !bridgeSuccess) {
                response.put("success", false);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing assertion", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            errorResponse.put("timestamp", new Date());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Batch assert multiple statements with validation.
     *
     * @param request Batch assertion request
     * @return Aggregated results
     */
    @PostMapping("/assert-batch")
    public ResponseEntity<Map<String, Object>> assertBatch(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assertions = (List<Map<String, Object>>) request.get("assertions");

            if (assertions == null || assertions.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "No assertions provided");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;

            for (Map<String, Object> assertion : assertions) {
                try {
                    // Validate each assertion
                    ShaclValidationService.ValidationResult validation = shaclValidator.validateAssertion(assertion);

                    Map<String, Object> itemResult = new HashMap<>();
                    itemResult.put("assertion", assertion);
                    itemResult.put("validation", validation.toResponseMap());

                    if (validation.isValid()) {
                        // Assert via bridge
                        Map<String, Object> bridgeResponse = csnepsBridge.assertStatement(assertion);
                        itemResult.put("assertion_result", bridgeResponse);

                        Boolean bridgeSuccess = (Boolean) bridgeResponse.get("success");
                        if (bridgeSuccess != null && bridgeSuccess) {
                            itemResult.put("success", true);
                            successCount++;
                        } else {
                            itemResult.put("success", false);
                            failureCount++;
                        }
                    } else {
                        itemResult.put("success", false);
                        itemResult.put("reason", "SHACL validation failed");
                        failureCount++;
                    }

                    results.add(itemResult);

                } catch (Exception e) {
                    logger.error("Error processing assertion in batch", e);
                    Map<String, Object> itemResult = new HashMap<>();
                    itemResult.put("assertion", assertion);
                    itemResult.put("success", false);
                    itemResult.put("error", e.getMessage());
                    results.add(itemResult);
                    failureCount++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", successCount > 0);
            response.put("total_assertions", assertions.size());
            response.put("successful", successCount);
            response.put("failed", failureCount);
            response.put("results", results);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing batch assertion", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Batch processing error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Query knowledge base with pattern.
     *
     * @param pattern Query pattern
     * @return Query results
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestParam String pattern) {
        try {
            if (pattern == null || pattern.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Pattern parameter is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.debug("Executing query with pattern: {}", pattern);

            Map<String, Object> bridgeResponse = csnepsBridge.query(pattern);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query_pattern", pattern);
            response.put("results", bridgeResponse);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error executing query", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Query error: " + e.getMessage());
            errorResponse.put("pattern", pattern);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get justification for a node.
     *
     * @param nodeId Node identifier
     * @return Justification response
     */
    @GetMapping("/why")
    public ResponseEntity<Map<String, Object>> why(@RequestParam String nodeId) {
        try {
            if (nodeId == null || nodeId.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "nodeId parameter is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.debug("Getting justification for node: {}", nodeId);

            Map<String, Object> bridgeResponse = csnepsBridge.why(nodeId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("node_id", nodeId);
            response.put("justification", bridgeResponse);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting justification", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Justification error: " + e.getMessage());
            errorResponse.put("node_id", nodeId);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get subgraph around focus node.
     *
     * @param focus Focus node
     * @param radius Traversal radius (optional, default 2)
     * @return Subgraph JSON
     */
    @GetMapping("/subgraph")
    public ResponseEntity<Map<String, Object>> getSubgraph(
            @RequestParam String focus,
            @RequestParam(required = false, defaultValue = "2") Integer radius) {

        try {
            if (focus == null || focus.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "focus parameter is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.debug("Getting subgraph for focus: {}, radius: {}", focus, radius);

            Map<String, Object> bridgeResponse = csnepsBridge.getSubgraph(focus, radius);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("focus", focus);
            response.put("radius", radius);
            response.put("subgraph", bridgeResponse);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting subgraph", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Subgraph error: " + e.getMessage());
            errorResponse.put("focus", focus);
            errorResponse.put("radius", radius);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Proxy render request to CSNePS bridge.
     * Returns SVG or delegates to subgraph for JSON.
     *
     * @param focus Focus node
     * @param radius Radius (optional)
     * @param format Format: svg or json (optional, default svg)
     * @return Rendered graph
     */
    @GetMapping("/render")
    public ResponseEntity<?> render(
            @RequestParam String focus,
            @RequestParam(required = false, defaultValue = "2") Integer radius,
            @RequestParam(required = false, defaultValue = "svg") String format) {

        try {
            if (focus == null || focus.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "focus parameter is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if ("json".equalsIgnoreCase(format)) {
                // Delegate to subgraph endpoint
                return getSubgraph(focus, radius);
            } else {
                // Return SVG (for now, this would need SVG rendering implementation)
                // TODO: Implement SVG rendering via JUNG or delegate to CSNePS bridge
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "SVG rendering not yet implemented");
                response.put("suggestion", "Use format=json for subgraph JSON data");

                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
            }

        } catch (Exception e) {
            logger.error("Error rendering graph", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Render error: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check with CSNePS bridge status.
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> bridgeHealth = csnepsBridge.checkHealth();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "csri-kg-service");
        response.put("version", "1.0.0");
        response.put("timestamp", new Date());

        Map<String, Object> components = new HashMap<>();
        components.put("shacl_validator", "UP");
        components.put("csneps_bridge", bridgeHealth.get("status"));
        components.put("memory", "UP");

        response.put("components", components);
        response.put("csneps_bridge_detail", bridgeHealth);

        return ResponseEntity.ok(response);
    }

    /**
     * Load OWL-generated rules into CSNePS.
     *
     * @param rulesData EDN rules data
     * @return Loading result
     */
    @PostMapping("/rules/load")
    public ResponseEntity<Map<String, Object>> loadRules(@RequestBody Map<String, Object> rulesData) {
        try {
            logger.info("Loading rules into CSNePS");

            Map<String, Object> bridgeResponse = csnepsBridge.loadRules(rulesData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("rules_load_result", bridgeResponse);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error loading rules", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Rules loading error: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
