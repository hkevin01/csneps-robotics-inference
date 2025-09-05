package com.csri.kg.client.examples;

import java.util.Arrays;

import com.csri.kg.proto.*;

import com.csri.kg.client.CsriKgClient;
import com.csri.kg.client.CsriKgClientBuilder;

/**
 * Example usage of the CSNePS Knowledge Graph client.
 */
public class ClientExample {

    public static void main(String[] args) {
        // Create client connection
        try (CsriKgClient client = CsriKgClient.createLocal()) {

            // Check health
            if (!client.checkHealth()) {
                System.err.println("CSNePS service is not available");
                return;
            }

            System.out.println("Connected to CSNePS service");

            // Example 1: Assert some facts
            assertFacts(client);

            // Example 2: Query for information
            queryFacts(client);

            // Example 3: Get justification for a fact
            whyExplanation(client);

            // Example 4: Search for related concepts
            searchConcepts(client);

        } catch (Exception e) {
            System.err.println("Error communicating with CSNePS service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void assertFacts(CsriKgClient client) {
        System.out.println("\n=== Asserting Facts ===");

        // Create assertions using the builder
        Assertion fact1 = CsriKgClientBuilder.assertion()
                .predicate("isa")
                .subject("tweety")
                .object("bird")
                .confidence(1.0)
                .build();

        Assertion fact2 = CsriKgClientBuilder.assertion()
                .predicate("can-fly")
                .subject("tweety")
                .confidence(0.9)
                .build();

        // Assert the facts
        AssertResponse response = client.assertFacts(Arrays.asList(fact1, fact2));

        if (response.getSuccess()) {
            System.out.println("Successfully asserted " + response.getProcessedCount() + " facts");
        } else {
            System.err.println("Failed to assert facts: " + response.getErrorsList());
        }
    }

    private static void queryFacts(CsriKgClient client) {
        System.out.println("\n=== Querying Facts ===");

        // Execute a query with a pattern string
        QueryResponse response = client.query("isa(?x, bird)");

        if (response.getSuccess()) {
            System.out.println("Query returned " + response.getResultsCount() + " results:");
            for (QueryResult result : response.getResultsList()) {
                System.out.println("  Bindings: " + result.getBindingsMap());
                System.out.println("  Confidence: " + result.getConfidence());
                if (result.hasJustification()) {
                    System.out.println("  Justification: " + result.getJustification());
                }
                System.out.println();
            }
        } else {
            System.err.println("Query failed: " + response.getError());
        }
    }

    private static void whyExplanation(CsriKgClient client) {
        System.out.println("\n=== Why Explanation ===");

        // Get explanation for why something is true
        WhyResponse response = client.why("can-fly(tweety)", 5);

        if (response.getSuccess()) {
            System.out.println("Justification tree count: " + response.getJustificationTreeCount());
            if (!response.getJustificationJson().isEmpty()) {
                System.out.println("Justification JSON: " + response.getJustificationJson());
            }
        } else {
            System.err.println("Why explanation failed: " + response.getMessage());
        }
    }

    private static void searchConcepts(CsriKgClient client) {
        System.out.println("\n=== Search Concepts ===");

        // TODO: Uncomment when search methods are implemented
        // SearchResponse response = client.search("bird", 10);
        //
        // if (response.getSuccess()) {
        //     System.out.println("Search returned " + response.getResultsCount() + " results:");
        //     for (QueryResult result : response.getResultsList()) {
        //         System.out.println("  Result bindings: " + result.getBindingsMap());
        //         System.out.println("  Confidence: " + result.getConfidence());
        //         if (result.hasJustification()) {
        //             System.out.println("  Has justification");
        //         }
        //         System.out.println();
        //     }
        // } else {
        //     System.err.println("Search failed: " + response.getMessage());
        // }

        System.out.println("Search functionality temporarily disabled - protobuf classes need regeneration");
    }
}
