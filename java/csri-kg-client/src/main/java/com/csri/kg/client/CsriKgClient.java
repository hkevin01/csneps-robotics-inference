package com.csri.kg.client;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.csri.kg.proto.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

/**
 * Java client for CSNePS Knowledge Graph operations.
 * Provides a high-level API for interacting with the CSNePS inference engine.
 */
public class CsriKgClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CsriKgClient.class);

    private final ManagedChannel channel;
    private final GraphServiceGrpc.GraphServiceBlockingStub graphStub;
    private final HealthServiceGrpc.HealthServiceBlockingStub healthStub;

    public CsriKgClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.graphStub = GraphServiceGrpc.newBlockingStub(channel);
        this.healthStub = HealthServiceGrpc.newBlockingStub(channel);

        logger.info("Created CSNePS client for {}:{}", host, port);
    }

    /**
     * Assert facts into the knowledge base.
     */
    public AssertResponse assertFacts(List<Assertion> assertions) {
        return assertFacts(assertions, true);
    }

    /**
     * Assert facts into the knowledge base with validation option.
     */
    public AssertResponse assertFacts(List<Assertion> assertions, boolean validate) {
        try {
            AssertRequest request = AssertRequest.newBuilder()
                    .addAllAssertions(assertions)
                    .setValidateShacl(validate)
                    .build();

            AssertResponse response = graphStub.assert_(request);
            logger.debug("Asserted {} facts, {} processed",
                        assertions.size(), response.getAssertionsAccepted());
            return response;

        } catch (StatusRuntimeException e) {
            logger.error("Failed to assert facts: {}", e.getMessage());
            throw new CsriKgClientException("Assertion failed", e);
        }
    }

    /**
     * Query the knowledge base.
     */
    public QueryResponse query(String pattern) {
        try {
            QueryRequest request = QueryRequest.newBuilder()
                    .setPattern(pattern)
                    .build();

            QueryResponse response = graphStub.query(request);
            logger.debug("Query returned {} results", response.getResultsCount());
            return response;

        } catch (StatusRuntimeException e) {
            logger.error("Failed to execute query: {}", e.getMessage());
            throw new CsriKgClientException("Query failed", e);
        }
    }

    /**
     * Get justification for a conclusion.
     */
    public WhyResponse why(String conclusion) {
        return why(conclusion, 10);
    }

    /**
     * Get justification for a conclusion with maximum depth.
     */
    public WhyResponse why(String conclusion, int maxDepth) {
        try {
            WhyRequest request = WhyRequest.newBuilder()
                    .setConclusion(conclusion)
                    .setMaxDepth(maxDepth)
                    .build();

            WhyResponse response = graphStub.why(request);
            logger.debug("Found {} justifications for: {}",
                        response.getJustificationsCount(), conclusion);
            return response;

        } catch (StatusRuntimeException e) {
            logger.error("Failed to get justification: {}", e.getMessage());
            throw new CsriKgClientException("Justification query failed", e);
        }
    }

    /**
     * Search the knowledge base.
     */
    public SearchResponse search(SearchRequest searchRequest) {
        try {
            SearchResponse response = graphStub.search(searchRequest);
            logger.debug("Search returned {} results", response.getResultsCount());
            return response;

        } catch (StatusRuntimeException e) {
            logger.error("Failed to execute search: {}", e.getMessage());
            throw new CsriKgClientException("Search failed", e);
        }
    }

    /**
     * Search the knowledge base with simple text query.
     */
    public SearchResponse search(String queryText, int limit) {
        SearchRequest request = SearchRequest.newBuilder()
                .setQueryText(queryText)
                .setLimit(limit)
                .setFuzzyMatch(false)
                .build();
        return search(request);
    }

    /**
     * Check service health.
     */
    public boolean isHealthy() {
        try {
            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setService("GraphService")
                    .build();

            HealthCheckResponse response = healthStub.check(request);
            boolean healthy = response.getStatus() == HealthCheckResponse.ServingStatus.SERVING;

            logger.debug("Health check result: {}", healthy ? "HEALTHY" : "UNHEALTHY");
            return healthy;

        } catch (StatusRuntimeException e) {
            logger.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Shutdown the client gracefully.
     */
    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.info("CSNePS client closed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while closing client");
        }
    }

    /**
     * Create a client with default localhost connection.
     */
    public static CsriKgClient createLocal() {
        return new CsriKgClient("localhost", 9090);
    }

    /**
     * Exception thrown by client operations.
     */
    public static class CsriKgClientException extends RuntimeException {
        public CsriKgClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
