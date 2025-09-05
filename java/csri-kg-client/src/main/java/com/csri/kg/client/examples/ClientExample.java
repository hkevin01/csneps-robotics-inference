package com.csri.kg.client.examples;

import java.util.Arrays;

import javax.management.Query;

import com.csri.kg.proto.GraphProto.*;

import main.java.com.csri.kg.client.CsriKgClient;
import main.java.com.csri.kg.client.CsriKgClientBuilder;
import main.java.com.csri.kg.service.core.CsnepsIntegrationService.QueryResult;

/**
 * Example usage of the CSNePS Knowledge Graph client.
 */
public class ClientExample {

    public static void main(String[] args) {
        // Create client connection
        try (CsriKgClient client = CsriKgClient.createLocal()) {

            // Check health
            if (!client.isHealthy()) {
                System.err.println("CSNePS service is not available");
                return;
            }

            System.out.println("Connected to CSNePS service");

            // Example 1: Assert some facts
            assertFacts(client);

            // Example 2: Query for information
            queryFacts(client);

            // Example 3: Get justifications
            getJustification(client);

            // Example 4: Search knowledge base
            searchKnowledge(client);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void assertFacts(CsriKgClient client) {
        System.out.println("\n=== Asserting Facts ===");

        // Create assertions using the builder
        Assertion fact1 = CsriKgClientBuilder.assertion()
                .predicate("isa")
                .args("tweety", "bird")
                .confidence(1.0)
                .source("example")
                .metadata("category", "taxonomy")
                .build();

        Assertion fact2 = CsriKgClientBuilder.assertion()
                .predicate("can-fly")
                .args("tweety")
                .confidence(0.9)
                .source("inference")
                .metadata("rule", "birds-fly")
                .build();

        // Assert the facts
        AssertResponse response = client.assertFacts(Arrays.asList(fact1, fact2));

        if (response.getSuccess()) {
            System.out.println("Successfully asserted " + response.getProcessedCount() + " facts");
            System.out.println("Assertion IDs: " + response.getAssertionIdsList());
        } else {
            System.err.println("Failed to assert facts: " + response.getErrorsList());
        }
    }

    private static void queryFacts(CsriKgClient client) {
        System.out.println("\n=== Querying Facts ===");

        // Create a query using the builder
        Query query = CsriKgClientBuilder.query()
                .pattern("isa(?x, bird)")
                .variable("x", "entity")
                .maxResults(10)
                .minConfidence(0.5)
                .includeJustification(true)
                .build();

        // Execute the query
        QueryResponse response = client.query(query);

        if (response.getSuccess()) {
            System.out.println("Query returned " + response.getResultsCount() + " results");

            for (QueryResult result : response.getResultsList()) {
                System.out.println("  Bindings: " + result.getBindingsMap());
                System.out.println("  Confidence: " + result.getConfidence());

                if (result.hasJustification()) {
                    System.out.println("  Justification: " + result.getJustification().getRule());
                }
            }

            if (response.hasStats()) {
                QueryStats stats = response.getStats();
                System.out.println("  Execution time: " + stats.getExecutionTimeMs() + "ms");
                System.out.println("  Nodes explored: " + stats.getNodesExplored());
            }
        } else {
            System.err.println("Query failed: " + response.getError());
        }
    }

    private static void getJustification(CsriKgClient client) {
        System.out.println("\n=== Getting Justification ===");

        // Ask why something is true
        WhyResponse response = client.why("can-fly(tweety)", 5);

        if (response.getSuccess()) {
            System.out.println("Found " + response.getJustificationsCount() + " justifications");

            for (Justification just : response.getJustificationsList()) {
                System.out.println("  Conclusion: " + just.getConclusion());
                System.out.println("  Rule: " + just.getRule());
                System.out.println("  Premises: " + just.getPremisesList());
                System.out.println("  Confidence: " + just.getConfidence());

                if (just.getSubJustificationsCount() > 0) {
                    System.out.println("  Sub-justifications: " + just.getSubJustificationsCount());
                }
            }
        } else {
            System.err.println("Justification failed: " + response.getError());
        }
    }

    private static void searchKnowledge(CsriKgClient client) {
        System.out.println("\n=== Searching Knowledge Base ===");

        // Create search criteria using the builder
        SearchCriteria criteria = CsriKgClientBuilder.search()
                .textQuery("bird")
                .predicate("isa")
                .entity("tweety")
                .minConfidence(0.7)
                .source("example")
                .build();

        // Execute the search
        SearchResponse response = client.search(criteria, 20, 0);

        if (response.getSuccess()) {
            System.out.println("Search returned " + response.getResultsCount() +
                             " of " + response.getTotalCount() + " total results");

            for (SearchResult result : response.getResultsList()) {
                Assertion assertion = result.getAssertion();
                System.out.println("  " + assertion.getPredicate() + "(" +
                                 String.join(", ", assertion.getArgsList()) + ")");
                System.out.println("    Confidence: " + assertion.getConfidence());
                System.out.println("    Relevance: " + result.getRelevanceScore());
                System.out.println("    Matched terms: " + result.getMatchedTermsList());
            }
        } else {
            System.err.println("Search failed: " + response.getError());
        }
    }
}
