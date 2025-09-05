package com.csri.kg.service.grpc;

import com.csri.kg.proto.GraphServiceGrpc;
import com.csri.kg.proto.GraphProtos.*;
import com.csri.kg.service.core.CsnepsIntegrationService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * gRPC server for Knowledge Graph operations.
 * Bridges Java gRPC calls to CSNePS Clojure core.
 */
@Component
public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    
    private final int port;
    private final CsnepsIntegrationService csnepsService;
    private Server server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public GrpcServer(@Value("${grpc.port:50061}") int port, 
                      CsnepsIntegrationService csnepsService) {
        this.port = port;
        this.csnepsService = csnepsService;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
            .addService(new GraphServiceImpl(csnepsService))
            .addService(new HealthServiceImpl())
            .executor(executor)
            .build()
            .start();
        
        logger.info("gRPC server started on port {}", port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            logger.info("Shutting down gRPC server...");
            server.shutdown();
        }
        executor.shutdown();
    }

    /**
     * Graph service implementation - bridges to CSNePS.
     */
    static class GraphServiceImpl extends GraphServiceGrpc.GraphServiceImplBase {
        
        private final CsnepsIntegrationService csnepsService;
        
        public GraphServiceImpl(CsnepsIntegrationService csnepsService) {
            this.csnepsService = csnepsService;
        }

        @Override
        public void assert_(AssertRequest request,
                           io.grpc.stub.StreamObserver<AssertResponse> responseObserver) {
            try {
                logger.debug("Processing assertion request with {} assertions", 
                           request.getAssertionsCount());
                
                var result = csnepsService.processAssertions(request.getAssertionsList());
                
                var response = AssertResponse.newBuilder()
                    .setOk(result.isSuccess())
                    .setMessage(result.getMessage())
                    .setAssertionsAccepted(result.getAcceptedCount())
                    .addAllValidationErrors(result.getValidationErrors())
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("Error processing assertions", e);
                var errorResponse = AssertResponse.newBuilder()
                    .setOk(false)
                    .setMessage("Internal error: " + e.getMessage())
                    .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void query(QueryRequest request,
                         io.grpc.stub.StreamObserver<QueryResponse> responseObserver) {
            try {
                logger.debug("Processing query: {}", request.getPattern());
                
                var result = csnepsService.executeQuery(
                    request.getPattern(), 
                    request.getLimit(), 
                    request.getIncludeJustification()
                );
                
                var response = QueryResponse.newBuilder()
                    .addAllResults(result.getResults())
                    .setSuccess(result.isSuccess())
                    .setMessage(result.getMessage())
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("Error processing query", e);
                var errorResponse = QueryResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Query error: " + e.getMessage())
                    .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void why(WhyRequest request,
                       io.grpc.stub.StreamObserver<WhyResponse> responseObserver) {
            try {
                logger.debug("Processing justification request for node: {}", request.getNodeId());
                
                var result = csnepsService.getJustification(
                    request.getNodeId(), 
                    request.getMaxDepth()
                );
                
                var response = WhyResponse.newBuilder()
                    .setJustificationJson(result.getJustificationJson())
                    .addAllJustificationTree(result.getJustificationNodes())
                    .setSuccess(result.isSuccess())
                    .setMessage(result.getMessage())
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("Error getting justification", e);
                var errorResponse = WhyResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Justification error: " + e.getMessage())
                    .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }

        @Override
        public void search(SearchRequest request,
                          io.grpc.stub.StreamObserver<SearchResponse> responseObserver) {
            try {
                logger.debug("Processing search request: {}", request.getQueryText());
                
                var result = csnepsService.searchKnowledgeGraph(
                    request.getQueryText(),
                    request.getConceptFilter(),
                    request.getLimit(),
                    request.getFuzzyMatch()
                );
                
                var response = SearchResponse.newBuilder()
                    .addAllResults(result.getResults())
                    .setSuccess(result.isSuccess())
                    .setMessage(result.getMessage())
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("Error processing search", e);
                var errorResponse = SearchResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Search error: " + e.getMessage())
                    .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * Health service implementation.
     */
    static class HealthServiceImpl extends com.csri.kg.proto.HealthServiceGrpc.HealthServiceImplBase {
        
        @Override
        public void check(HealthRequest request,
                         io.grpc.stub.StreamObserver<HealthResponse> responseObserver) {
            var response = HealthResponse.newBuilder()
                .setHealthy(true)
                .setVersion("0.1.0-SNAPSHOT")
                .putStatus("grpc", "active")
                .putStatus("csneps_connection", "active")
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
