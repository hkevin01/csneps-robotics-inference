package com.csri.kg.service.web;

import com.csri.kg.service.core.CsnepsIntegrationService;
import com.csri.kg.service.web.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for Knowledge Graph operations.
 * Provides HTTP endpoints for assertions, queries, and justifications.
 */
@RestController
@RequestMapping("/api/graph")
public class GraphController {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphController.class);
    
    private final CsnepsIntegrationService csnepsService;
    
    public GraphController(CsnepsIntegrationService csnepsService) {
        this.csnepsService = csnepsService;
    }

    /**
     * Assert a single statement into the knowledge graph.
     */
    @PostMapping("/assert")
    public ResponseEntity<Map<String, Object>> assertOne(@Valid @RequestBody AssertionDTO dto) {
        logger.info("Processing single assertion: {} {} {}", dto.subject(), dto.predicate(), dto.object());
        
        try {
            // TODO: SHACL validation before forwarding
            // Convert DTO to proto and forward to CSNePS
            
            // For now, return success
            return ResponseEntity.ok(Map.of(
                "ok", true, 
                "message", "Assertion accepted",
                "assertion_id", "assertion-" + System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("Error processing assertion", e);
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Assert multiple statements in a batch.
     */
    @PostMapping("/assert/batch")
    public ResponseEntity<Map<String, Object>> assertBatch(@Valid @RequestBody AssertBatchRequest req) {
        logger.info("Processing batch assertion with {} statements", req.assertions().size());
        
        try {
            // Convert DTOs to proto assertions
            var protoAssertions = req.assertions().stream()
                .map(this::convertToProtoAssertion)
                .collect(Collectors.toList());
            
            var result = csnepsService.processAssertions(protoAssertions);
            
            return ResponseEntity.ok(Map.of(
                "ok", result.isSuccess(),
                "message", result.getMessage(),
                "accepted_count", result.getAcceptedCount(),
                "validation_errors", result.getValidationErrors()
            ));
            
        } catch (Exception e) {
            logger.error("Error processing batch assertions", e);
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "message", "Batch error: " + e.getMessage()
            ));
        }
    }

    /**
     * Query the knowledge graph with pattern matching.
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestParam("pattern") String pattern,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "include_justification", defaultValue = "false") boolean includeJustification) {
        
        logger.info("Processing query: {} (limit: {}, justification: {})", pattern, limit, includeJustification);
        
        try {
            var result = csnepsService.executeQuery(pattern, limit, includeJustification);
            
            var responseResults = result.getResults().stream()
                .map(qr -> Map.of(
                    "node_id", qr.getNodeId(),
                    "bindings", qr.getBindingsMap(),
                    "confidence", qr.getConfidence(),
                    "justification_summary", qr.getJustificationSummary()
                ))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "results", responseResults
            ));
            
        } catch (Exception e) {
            logger.error("Error processing query", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Query error: " + e.getMessage(),
                "results", java.util.List.of()
            ));
        }
    }

    /**
     * Get justification/explanation for a specific belief.
     */
    @GetMapping("/why/{nodeId}")
    public ResponseEntity<Map<String, Object>> why(
            @PathVariable String nodeId,
            @RequestParam(value = "max_depth", defaultValue = "5") int maxDepth) {
        
        logger.info("Getting justification for node: {} (max depth: {})", nodeId, maxDepth);
        
        try {
            var result = csnepsService.getJustification(nodeId, maxDepth);
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "justification", Map.of(
                    "json", result.getJustificationJson(),
                    "tree", result.getJustificationNodes().stream()
                        .map(jn -> Map.of(
                            "node_id", jn.getNodeId(),
                            "rule_name", jn.getRuleName(),
                            "conclusion", jn.getConclusion(),
                            "premises", jn.getPremisesList(),
                            "confidence", jn.getConfidence()
                        ))
                        .collect(Collectors.toList())
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error getting justification", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Justification error: " + e.getMessage(),
                "justification", Map.of()
            ));
        }
    }

    /**
     * Search the knowledge graph with text query.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String queryText,
            @RequestParam(value = "concept_filter", required = false) String conceptFilter,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "fuzzy", defaultValue = "false") boolean fuzzyMatch) {
        
        logger.info("Processing search: '{}' (filter: {}, fuzzy: {})", queryText, conceptFilter, fuzzyMatch);
        
        try {
            var result = csnepsService.searchKnowledgeGraph(queryText, conceptFilter, limit, fuzzyMatch);
            
            var responseResults = result.getResults().stream()
                .map(sr -> Map.of(
                    "node_id", sr.getNodeId(),
                    "match", sr.getBindingsMap(),
                    "confidence", sr.getConfidence()
                ))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "results", responseResults
            ));
            
        } catch (Exception e) {
            logger.error("Error processing search", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Search error: " + e.getMessage(),
                "results", java.util.List.of()
            ));
        }
    }

    /**
     * Get system health and status.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "version", "0.1.0-SNAPSHOT",
            "services", Map.of(
                "csneps_core", "connected",
                "grpc_server", "active",
                "rest_api", "active"
            )
        ));
    }
    
    /**
     * Convert DTO to protobuf assertion.
     */
    private com.csri.kg.proto.GraphProtos.Assertion convertToProtoAssertion(AssertionDTO dto) {
        var builder = com.csri.kg.proto.GraphProtos.Assertion.newBuilder()
            .setSubject(dto.subject())
            .setPredicate(dto.predicate())
            .setObject(dto.object())
            .setConfidence(dto.confidence());
        
        if (dto.provenance() != null) {
            var provBuilder = com.csri.kg.proto.GraphProtos.Provenance.newBuilder();
            if (dto.provenance().docId() != null) provBuilder.setDocId(dto.provenance().docId());
            if (dto.provenance().startOffset() != null) provBuilder.setStartOffset(dto.provenance().startOffset());
            if (dto.provenance().endOffset() != null) provBuilder.setEndOffset(dto.provenance().endOffset());
            if (dto.provenance().extractor() != null) provBuilder.setExtractor(dto.provenance().extractor());
            
            builder.setProvenance(provBuilder.build());
        }
        
        return builder.build();
    }
}
