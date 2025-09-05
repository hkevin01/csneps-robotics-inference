package com.csri.kg.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP client for communicating with CSNePS Clojure bridge.
 * Handles assertion, query, and justification operations.
 */
@Service
public class CsnepsBridgeClient {
    private static final Logger logger = LoggerFactory.getLogger(CsnepsBridgeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CsnepsBridgeClient(@Value("${csneps.bridge.url:http://localhost:3000}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();

        logger.info("CSNePS Bridge Client initialized for: {}", baseUrl);
    }

    /**
     * Assert a single statement via CSNePS bridge.
     */
    public Map<String, Object> assertStatement(Map<String, Object> assertion) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(assertion, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/assert", entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.debug("Assertion successful: {}", assertion.get("predicate"));
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                logger.error("Assertion failed with status {}", response.getStatusCode());
                return createErrorResponse("Assertion failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error asserting statement", e);
            return createErrorResponse("Connection error: " + e.getMessage());
        }
    }

    /**
     * Query CSNePS with pattern.
     */
    public Map<String, Object> query(String pattern) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/query")
                .queryParam("pattern", pattern)
                .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.debug("Query successful for pattern: {}", pattern);
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                logger.error("Query failed with status {}", response.getStatusCode());
                return createErrorResponse("Query failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error querying pattern: {}", pattern, e);
            return createErrorResponse("Connection error: " + e.getMessage());
        }
    }

    /**
     * Get justification for a node.
     */
    public Map<String, Object> why(String nodeId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/why")
                .queryParam("nodeId", nodeId)
                .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.debug("Why query successful for node: {}", nodeId);
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                logger.error("Why query failed with status {}", response.getStatusCode());
                return createErrorResponse("Why query failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error getting justification for node: {}", nodeId, e);
            return createErrorResponse("Connection error: " + e.getMessage());
        }
    }

    /**
     * Get subgraph around focus node.
     */
    public Map<String, Object> getSubgraph(String focus, Integer radius) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/subgraph")
                .queryParam("focus", focus);

            if (radius != null) {
                builder.queryParam("radius", radius);
            }

            ResponseEntity<Map> response = restTemplate.getForEntity(builder.toUriString(), Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.debug("Subgraph query successful for focus: {}", focus);
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                logger.error("Subgraph query failed with status {}", response.getStatusCode());
                return createErrorResponse("Subgraph query failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error getting subgraph for focus: {}", focus, e);
            return createErrorResponse("Connection error: " + e.getMessage());
        }
    }

    /**
     * Check health of CSNePS bridge.
     */
    public Map<String, Object> checkHealth() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/health", Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                return createErrorResponse("Health check failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.warn("CSNePS bridge health check failed", e);
            return createErrorResponse("Health check error: " + e.getMessage());
        }
    }

    /**
     * Load rules into CSNePS.
     */
    public Map<String, Object> loadRules(Map<String, Object> rulesData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rulesData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/rules/load", entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Rules loaded successfully");
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.getBody();
                return result;
            } else {
                logger.error("Rules loading failed with status {}", response.getStatusCode());
                return createErrorResponse("Rules loading failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error loading rules", e);
            return createErrorResponse("Connection error: " + e.getMessage());
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", java.time.Instant.now().toString());
        return error;
    }
}
