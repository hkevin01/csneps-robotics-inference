package com.csri.kg.service.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * SHACL Validation Service for CSNePS assertions.
 * Validates incoming assertions against ontology shapes before ingestion.
 */
@Service
public class ShaclValidationService {
    private static final Logger logger = LoggerFactory.getLogger(ShaclValidationService.class);

    private final Shapes coreShapes;
    private static final String CORE_SHAPES_FILE = "/ontology/shacl/core-shapes.ttl";

    public ShaclValidationService() {
        this.coreShapes = loadCoreShapes();
    }

    /**
     * Load core SHACL shapes from resources.
     */
    private Shapes loadCoreShapes() {
        try (InputStream is = getClass().getResourceAsStream(CORE_SHAPES_FILE)) {
            if (is == null) {
                logger.warn("Core shapes file not found: {}", CORE_SHAPES_FILE);
                return Shapes.parse(ModelFactory.createDefaultModel());
            }

            Model shapesModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(shapesModel, is, null, org.apache.jena.riot.Lang.TURTLE);

            logger.info("Loaded SHACL shapes: {} shapes", shapesModel.size());
            return Shapes.parse(shapesModel);

        } catch (Exception e) {
            logger.error("Failed to load core shapes", e);
            return Shapes.parse(ModelFactory.createDefaultModel());
        }
    }

    /**
     * Validate a single assertion against SHACL shapes.
     * Converts assertion to RDF triple and validates.
     */
    public ValidationResult validateAssertion(Map<String, Object> assertion) {
        try {
            // Create minimal RDF model from assertion
            Model dataModel = createAssertionModel(assertion);

            // Perform SHACL validation
            ValidationReport report = ShaclValidator.get().validate(coreShapes, dataModel.getGraph());

            return new ValidationResult(
                report.conforms(),
                report.getEntries().size(),
                extractViolationMessages(report),
                assertion
            );

        } catch (Exception e) {
            logger.error("Validation failed for assertion: {}", assertion, e);
            return new ValidationResult(
                false,
                1,
                java.util.List.of("Validation error: " + e.getMessage()),
                assertion
            );
        }
    }

    /**
     * Create RDF model from CSNePS assertion.
     */
    private Model createAssertionModel(Map<String, Object> assertion) {
        Model model = ModelFactory.createDefaultModel();

        String subject = (String) assertion.get("subject");
        String predicate = (String) assertion.get("predicate");
        String object = (String) assertion.get("object");

        if (subject == null || predicate == null || object == null) {
            throw new IllegalArgumentException("Assertion missing required fields: subject, predicate, object");
        }

        // Create RDF namespace
        String ns = "http://csneps.ai/kb#";

        // Create resources and properties
        Resource subjectResource = model.createResource(ns + sanitizeForRdf(subject));
        Property predicateProperty = model.createProperty(ns + sanitizeForRdf(predicate));

        // Handle object - could be resource or literal
        if (isUri(object) || object.contains(":")) {
            Resource objectResource = model.createResource(ns + sanitizeForRdf(object));
            model.add(subjectResource, predicateProperty, objectResource);
        } else {
            model.add(subjectResource, predicateProperty, model.createLiteral(object));
        }

        // Add confidence if present
        Object confidence = assertion.get("confidence");
        if (confidence != null) {
            Property confidenceProp = model.createProperty(ns + "confidence");
            model.add(subjectResource, confidenceProp, model.createTypedLiteral(confidence));
        }

        return model;
    }

    /**
     * Sanitize string for use in RDF URIs.
     */
    private String sanitizeForRdf(String value) {
        if (value == null) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_")
                   .replaceAll("^[^a-zA-Z]", "item_");
    }

    /**
     * Check if string looks like a URI.
     */
    private boolean isUri(String value) {
        return value != null && (value.startsWith("http") || value.contains("://") || value.contains(":"));
    }

    /**
     * Extract violation messages from SHACL report.
     */
    private java.util.List<String> extractViolationMessages(ValidationReport report) {
        return report.getEntries().stream()
            .map(entry -> String.format("Focus: %s, Path: %s, Message: %s",
                entry.focusNode(),
                entry.resultPath(),
                entry.message()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Validation result container.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final int violationCount;
        private final java.util.List<String> violations;
        private final Map<String, Object> assertion;

        public ValidationResult(boolean valid, int violationCount,
                              java.util.List<String> violations, Map<String, Object> assertion) {
            this.valid = valid;
            this.violationCount = violationCount;
            this.violations = violations;
            this.assertion = assertion;
        }

        public boolean isValid() { return valid; }
        public int getViolationCount() { return violationCount; }
        public java.util.List<String> getViolations() { return violations; }
        public Map<String, Object> getAssertion() { return assertion; }

        public Map<String, Object> toResponseMap() {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", valid);
            response.put("violation_count", violationCount);
            response.put("violations", violations);
            response.put("assertion", assertion);
            return response;
        }
    }
}
