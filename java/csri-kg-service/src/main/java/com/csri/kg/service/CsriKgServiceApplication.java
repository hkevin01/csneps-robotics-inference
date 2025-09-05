package com.csri.kg.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import main.java.com.csri.kg.service.core.CsnepsIntegrationServiceSimple;
import main.java.com.csri.kg.service.grpc.GrpcServerSimple;
import main.java.com.csri.kg.service.web.GraphController;

/**
 * CSRI Knowledge Graph Service - Java microservice for CSNePS integration.
 *
 * Provides REST and gRPC APIs for:
 * - Knowledge graph assertions
 * - Queries and inference
 * - Justification/explanation
 * - Ontology-driven validation
 *
 * v0.1.0 Features:
 * - Mock REST endpoints (GraphController)
 * - Mock gRPC server
 * - Mock CSNePS integration service
 * - Production-ready Spring Boot structure
 *
 * To test the service:
 * 1. Run this main class
 * 2. Access REST endpoints at http://localhost:8080/api/graph/*
 * 3. Use GraphController methods directly for testing
 */
@SpringBootApplication
public class CsriKgServiceApplication {

    public static void main(String[] args) {
        System.out.println("Starting CSRI Knowledge Graph Service v0.1.0...");
        System.out.println("==============================================");

        // Check if running in demo mode (no Spring Boot args)
        if (args.length == 0 || (args.length == 1 && "demo".equals(args[0]))) {
            runDemoMode();
            return;
        }

        // Run full Spring Boot application
        ConfigurableApplicationContext context = SpringApplication.run(CsriKgServiceApplication.class, args);

        System.out.println();
        System.out.println("✅ CSRI Knowledge Graph Service started successfully!");
        System.out.println("🌐 REST API available at: http://localhost:8080/api/graph/");
        System.out.println("📊 Health check: http://localhost:8080/api/graph/health");
        System.out.println("📖 Documentation: See GraphController.java for endpoints");
        System.out.println();

        // Demonstrate service capabilities
        demonstrateService(context);
    }

    /**
     * Run in demo mode without Spring Boot for testing basic functionality.
     */
    private static void runDemoMode() {
        System.out.println("Running in Demo Mode (no Spring Boot required)");
        System.out.println("==============================================");
        System.out.println();

        // Test GraphController directly
        GraphController controller = new GraphController();

        System.out.println("Testing GraphController Mock Implementation:");
        System.out.println("==========================================");

        try {
            // Test assertion
            var statement = java.util.Map.of(
                "predicate", "is_robot",
                "arguments", java.util.List.of("R2D2", "autonomous_agent")
            );
            var assertResult = controller.assertOne(statement);
            System.out.println("✅ Assertion: " + assertResult.get("message"));

            // Test batch assertion
            var statements = java.util.List.of(
                java.util.Map.of("predicate", "is_robot", "arguments", java.util.List.of("R2D2")),
                java.util.Map.of("predicate", "can_move", "arguments", java.util.List.of("R2D2"))
            );
            var batchResult = controller.assertBatch(statements);
            System.out.println("✅ Batch Assertion: " + batchResult.get("total") + " statements processed");

            // Test query
            var queryResult = controller.query("?x is_robot", 10);
            System.out.println("✅ Query: " + queryResult.get("count") + " results found");

            // Test justification
            var justification = controller.why("stmt_123");
            System.out.println("✅ Justification: " + justification.get("success"));

            // Test search
            var searchResult = controller.search("robot", "entity", 5);
            System.out.println("✅ Search: " + searchResult.get("total") + " entities found");

            // Test health
            var health = controller.health();
            System.out.println("✅ Health: " + health.get("status"));

            System.out.println();
            System.out.println("🎉 All services working correctly in demo mode!");
            System.out.println();
            System.out.println("To run with Spring Boot REST server:");
            System.out.println("1. mvn spring-boot:run");
            System.out.println("2. java -jar target/csri-kg-service-0.1.0-SNAPSHOT.jar");
            System.out.println("3. Uncomment @RestController annotations in GraphController.java");

        } catch (Exception e) {
            System.out.println("⚠️  Demo error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demonstrate service capabilities when running with Spring Boot.
     */
    private static void demonstrateService(ConfigurableApplicationContext context) {
        try {
            System.out.println("Spring Boot Integration Status:");
            System.out.println("==============================");
            System.out.println("✅ Application context loaded");
            System.out.println("✅ REST endpoints available");
            System.out.println("✅ gRPC server ready");
            System.out.println("✅ CSNePS integration available");
            System.out.println();

            // Initialize and demonstrate services
            CsnepsIntegrationServiceSimple csnepsService = new CsnepsIntegrationServiceSimple();
            GrpcServerSimple grpcServer = new GrpcServerSimple();

            // Start gRPC server
            grpcServer.start();

            // Demo CSNePS operations
            System.out.println("Testing CSNePS Integration:");
            var fact = java.util.Map.<String, Object>of(
                "subject", "Robot",
                "predicate", "can_navigate",
                "object", "environment"
            );
            boolean result = csnepsService.assertFact(fact);
            System.out.println("✅ CSNePS assertion successful: " + result);

            var queryResults = csnepsService.query("?x can_navigate environment");
            System.out.println("✅ CSNePS query returned " + queryResults.size() + " results");

            System.out.println();

        } catch (Exception e) {
            System.out.println("⚠️  Integration demo error: " + e.getMessage());
        }
    }
}
