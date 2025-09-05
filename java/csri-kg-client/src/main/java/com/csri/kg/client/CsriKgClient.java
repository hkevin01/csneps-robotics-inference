package com.csri.kg.client;

import com.csri.kg.proto.GraphServiceGrpc;
import com.csri.kg.proto.HealthServiceGrpc;
import com.csri.kg.proto.GraphProto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
                    .setValidate(validate)
                    .build();
            
            AssertResponse response = graphStub.assertRequest(request);
            logger.debug("Asserted {} facts, {} processed", 
                        assertions.size(), response.getProcessedCount());
            return response;
            
        } catch (StatusRuntimeException e) {
            logger.error("Failed to assert facts: {}", e.getMessage());
            throw new CsriKgClientException("Assertion failed", e);
        }
    }
    
    /**
     * Query the knowledge base.
     */
    public QueryResponse query(Query query) {
        try {
            QueryRequest request = QueryRequest.newBuilder()
                    .setQuery(query)
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
    public SearchResponse search(SearchCriteria criteria) {
        return search(criteria, 100, 0);
    }
    
    /**
     * Search the knowledge base with pagination.
     */
    public SearchResponse search(SearchCriteria criteria, int limit, int offset) {
        try {
            SearchRequest request = SearchRequest.newBuilder()
                    .setCriteria(criteria)
                    .setLimit(limit)
                    .setOffset(offset)
                    .build();
            
            SearchResponse response = graphStub.search(request);
            logger.debug("Search returned {} of {} total results", 
                        response.getResultsCount(), response.getTotalCount());
            return response;
            
        } catch (StatusRuntimeException e) {
            logger.error("Failed to execute search: {}", e.getMessage());
            throw new CsriKgClientException("Search failed", e);
        }
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
