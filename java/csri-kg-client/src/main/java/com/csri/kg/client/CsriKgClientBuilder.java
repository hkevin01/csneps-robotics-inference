package com.csri.kg.client;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.csri.kg.proto.*;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.ArrayList;

/**
 * Builder utility for creating CSNePS knowledge graph objects.
 */
public class CsriKgClientBuilder {

    /**
     * Create an assertion builder.
     */
    public static AssertionBuilder assertion() {
        return new AssertionBuilder();
    }

    /**
     * Create a query builder.
     */
    public static QueryBuilder query() {
        return new QueryBuilder();
    }

    /**
     * Create a search criteria builder.
     */
    public static SearchCriteriaBuilder search() {
        return new SearchCriteriaBuilder();
    }

    /**
     * Builder for Assertion objects.
     */
    public static class AssertionBuilder {
        private String subject;
        private String predicate;
        private String object;
        private double confidence = 1.0;
        private String timestamp;
        private String docId;

        public AssertionBuilder predicate(String predicate) {
            this.predicate = predicate;
            return this;
        }

        public AssertionBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public AssertionBuilder object(String object) {
            this.object = object;
            return this;
        }

        public AssertionBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public AssertionBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AssertionBuilder docId(String docId) {
            this.docId = docId;
            return this;
        }

        public Assertion build() {
            // TODO: Uncomment when protobuf classes are regenerated
            // Provenance.Builder provBuilder = Provenance.newBuilder();
            // if (timestamp != null) {
            //     provBuilder.setTimestamp(timestamp);
            // }
            // if (docId != null) {
            //     provBuilder.setDocId(docId);
            // }

            return Assertion.newBuilder()
                    .setSubject(subject)
                    .setPredicate(predicate)
                    .setObject(object)
                    .setConfidence(confidence)
                    // .setProvenance(provBuilder.build())  // TODO: Uncomment when protobuf regenerated
                    .build();
        }
    }

    /**
     * Builder for Query objects.
     */
    public static class QueryBuilder {
        private String pattern;
        private int maxResults = 100;
        private double minConfidence = 0.0;
        private boolean includeJustification = false;
        private java.util.List<String> includeSources = new java.util.ArrayList<>();

        public QueryBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public QueryBuilder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public QueryBuilder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }

        public QueryBuilder includeJustification(boolean includeJustification) {
            this.includeJustification = includeJustification;
            return this;
        }

        public QueryBuilder includeSource(String source) {
            this.includeSources.add(source);
            return this;
        }

        public QueryRequest build() {
            if (pattern == null) {
                throw new IllegalStateException("Pattern is required");
            }

            return QueryRequest.newBuilder()
                    .setPattern(pattern)
                    .setLimit(maxResults)
                    .setIncludeJustification(includeJustification)
                    .build();
        }
    }

    /**
     * Builder for SearchCriteria objects.
     */
    public static class SearchCriteriaBuilder {
        private String textQuery;
        private java.util.List<String> predicates = new java.util.ArrayList<>();
        private java.util.List<String> entities = new java.util.ArrayList<>();
        private double minConfidence = 0.0;
        private java.util.List<String> sources = new java.util.ArrayList<>();

        public SearchCriteriaBuilder textQuery(String textQuery) {
            this.textQuery = textQuery;
            return this;
        }

        public SearchCriteriaBuilder predicate(String predicate) {
            this.predicates.add(predicate);
            return this;
        }

        public SearchCriteriaBuilder entity(String entity) {
            this.entities.add(entity);
            return this;
        }

        public SearchCriteriaBuilder minConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
            return this;
        }

        public SearchCriteriaBuilder source(String source) {
            this.sources.add(source);
            return this;
        }

        // TODO: Uncomment when protobuf classes are regenerated
        // public SearchRequest build() {
        //     return SearchRequest.newBuilder()
        //             .setQueryText(textQuery != null ? textQuery : "")
        //             .setConceptFilter("")  // Default empty filter
        //             .setLimit(100)  // Default limit
        //             .setFuzzyMatch(false)  // Default exact match
        //             .build();
        // }

        public Object build() {
            throw new UnsupportedOperationException("SearchRequest build not available until protobuf classes are regenerated");
        }
    }
}
