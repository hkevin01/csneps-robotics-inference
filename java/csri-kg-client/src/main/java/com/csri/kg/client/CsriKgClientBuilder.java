package com.csri.kg.client;

import com.csri.kg.proto.GraphProto.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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
        private String predicate;
        private String[] args = new String[0];
        private double confidence = 1.0;
        private String source;
        private String method = "manual";
        private Map<String, String> metadata = new HashMap<>();
        private Map<String, String> context = new HashMap<>();
        
        public AssertionBuilder predicate(String predicate) {
            this.predicate = predicate;
            return this;
        }
        
        public AssertionBuilder args(String... args) {
            this.args = args;
            return this;
        }
        
        public AssertionBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public AssertionBuilder source(String source) {
            this.source = source;
            return this;
        }
        
        public AssertionBuilder method(String method) {
            this.method = method;
            return this;
        }
        
        public AssertionBuilder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public AssertionBuilder context(String key, String value) {
            this.context.put(key, value);
            return this;
        }
        
        public Assertion build() {
            if (predicate == null) {
                throw new IllegalStateException("Predicate is required");
            }
            
            Provenance.Builder provBuilder = Provenance.newBuilder()
                    .setTimestamp(Instant.now().toEpochMilli())
                    .setMethod(method);
            
            if (source != null) {
                provBuilder.setSource(source);
            }
            
            provBuilder.putAllContext(context);
            
            return Assertion.newBuilder()
                    .setPredicate(predicate)
                    .addAllArgs(java.util.Arrays.asList(args))
                    .setConfidence(confidence)
                    .setProvenance(provBuilder.build())
                    .putAllMetadata(metadata)
                    .build();
        }
    }
    
    /**
     * Builder for Query objects.
     */
    public static class QueryBuilder {
        private String pattern;
        private java.util.List<Variable> variables = new java.util.ArrayList<>();
        private int maxResults = 100;
        private double minConfidence = 0.0;
        private boolean includeJustification = false;
        private java.util.List<String> includeSources = new java.util.ArrayList<>();
        
        public QueryBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }
        
        public QueryBuilder variable(String name, String type) {
            this.variables.add(Variable.newBuilder()
                    .setName(name)
                    .setType(type)
                    .build());
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
        
        public Query build() {
            if (pattern == null) {
                throw new IllegalStateException("Pattern is required");
            }
            
            QueryOptions options = QueryOptions.newBuilder()
                    .setMaxResults(maxResults)
                    .setMinConfidence(minConfidence)
                    .setIncludeJustification(includeJustification)
                    .addAllIncludeSources(includeSources)
                    .build();
            
            return Query.newBuilder()
                    .setPattern(pattern)
                    .addAllVariables(variables)
                    .setOptions(options)
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
        
        public SearchCriteria build() {
            return SearchCriteria.newBuilder()
                    .setTextQuery(textQuery != null ? textQuery : "")
                    .addAllPredicates(predicates)
                    .addAllEntities(entities)
                    .setMinConfidence(minConfidence)
                    .addAllSources(sources)
                    .build();
        }
    }
}
