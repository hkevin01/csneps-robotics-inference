package com.csri.owl.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.slf4j.LoggerFactory;

/**
 * SHACL validation tool for RDF data generated from OWL ontologies.
 * Validates RDF instances against SHACL shapes to ensure data quality.
 */
public class ShaclValidatorTool {
    private static final Logger logger = LoggerFactory.getLogger(ShaclValidatorTool.class);

    private Model dataModel;
    private Shapes shapesModel;

    /**
     * Load RDF data to be validated.
     */
    public void loadDataModel(File rdfFile) {
        logger.info("Loading RDF data from: {}", rdfFile.getName());
        dataModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(dataModel, rdfFile.getAbsolutePath());
        logger.debug("Loaded {} triples", dataModel.size());
    }

    /**
     * Load SHACL shapes for validation.
     */
    public void loadShapesModel(File shapesFile) {
        logger.info("Loading SHACL shapes from: {}", shapesFile.getName());
        Model shapesGraph = ModelFactory.createDefaultModel();
        RDFDataMgr.read(shapesGraph, shapesFile.getAbsolutePath());
        shapesModel = Shapes.parse(shapesGraph);
        logger.debug("Loaded {} shapes", shapesModel.numShapes());
    }

    /**
     * Validate the data model against SHACL shapes.
     */
    public ValidationResult validate() {
        if (dataModel == null) {
            throw new IllegalStateException("Data model not loaded");
        }
        if (shapesModel == null) {
            throw new IllegalStateException("Shapes model not loaded");
        }

        logger.info("Starting SHACL validation...");

        Graph dataGraph = dataModel.getGraph();
        ValidationReport report = ShaclValidator.get().validate(shapesModel, dataGraph);

        ValidationResult result = new ValidationResult();
        result.isValid = report.conforms();
        result.violationCount = report.getEntries().size();
        result.report = report;
        result.dataTriples = dataModel.size();
        result.shapesCount = shapesModel.numShapes();

        logger.info("Validation complete: {} violations, conformance: {}",
                   result.violationCount, result.isValid);

        return result;
    }

    /**
     * Export validation report to file.
     */
    public void exportReport(ValidationResult result, File outputFile) throws IOException {
        logger.info("Exporting validation report to: {}", outputFile.getName());

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("# SHACL Validation Report\n\n");
            writer.write("**Validation Status**: " + (result.isValid ? "VALID" : "INVALID") + "\n");
            writer.write("**Data Triples**: " + result.dataTriples + "\n");
            writer.write("**Shapes Count**: " + result.shapesCount + "\n");
            writer.write("**Violations**: " + result.violationCount + "\n\n");

            if (!result.isValid) {
                writer.write("## Validation Violations\n\n");
                result.report.getEntries().forEach(entry -> {
                    try {
                        writer.write("### Violation\n");
                        writer.write("- **Focus Node**: " + entry.focusNode() + "\n");
                        writer.write("- **Result Path**: " + entry.resultPath() + "\n");
                        writer.write("- **Message**: " + entry.message() + "\n");
                        writer.write("- **Severity**: " + entry.severity() + "\n\n");
                    } catch (IOException e) {
                        logger.error("Error writing violation report", e);
                    }
                });
            }

            writer.flush();
        }

        logger.info("Report exported successfully");
    }

    /**
     * Create basic SHACL shapes for robotics ontology.
     */
    public static void createRoboticsShapes(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("@prefix sh: <http://www.w3.org/ns/shacl#> .\n");
            writer.write("@prefix ex: <http://example.org/robotics#> .\n");
            writer.write("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");
            writer.write("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n");

            // Robot shape
            writer.write("ex:RobotShape\n");
            writer.write("    a sh:NodeShape ;\n");
            writer.write("    sh:targetClass ex:Robot ;\n");
            writer.write("    sh:property [\n");
            writer.write("        sh:path ex:hasId ;\n");
            writer.write("        sh:datatype xsd:string ;\n");
            writer.write("        sh:minCount 1 ;\n");
            writer.write("        sh:maxCount 1 ;\n");
            writer.write("    ] ;\n");
            writer.write("    sh:property [\n");
            writer.write("        sh:path ex:hasLocation ;\n");
            writer.write("        sh:class ex:Location ;\n");
            writer.write("        sh:minCount 1 ;\n");
            writer.write("    ] .\n\n");

            // Location shape
            writer.write("ex:LocationShape\n");
            writer.write("    a sh:NodeShape ;\n");
            writer.write("    sh:targetClass ex:Location ;\n");
            writer.write("    sh:property [\n");
            writer.write("        sh:path ex:hasCoordinates ;\n");
            writer.write("        sh:datatype xsd:string ;\n");
            writer.write("        sh:pattern \"^\\\\d+\\\\.\\\\d+,\\\\d+\\\\.\\\\d+$\" ;\n");
            writer.write("        sh:minCount 1 ;\n");
            writer.write("        sh:maxCount 1 ;\n");
            writer.write("    ] .\n\n");

            // Area shape
            writer.write("ex:AreaShape\n");
            writer.write("    a sh:NodeShape ;\n");
            writer.write("    sh:targetClass ex:Area ;\n");
            writer.write("    sh:property [\n");
            writer.write("        sh:path ex:hasName ;\n");
            writer.write("        sh:datatype xsd:string ;\n");
            writer.write("        sh:minCount 1 ;\n");
            writer.write("        sh:maxCount 1 ;\n");
            writer.write("    ] .\n");
        }
    }

    // Data class for validation results
    public static class ValidationResult {
        public boolean isValid;
        public int violationCount;
        public long dataTriples;
        public int shapesCount;
        public ValidationReport report;
    }

    // Main method for command-line usage
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage:");
            System.err.println("  Validate: java ShaclValidatorTool validate <data-file> <shapes-file> [output-report]");
            System.err.println("  Create shapes: java ShaclValidatorTool create-shapes <output-file>");
            System.exit(1);
        }

        try {
            ShaclValidatorTool validator = new ShaclValidatorTool();

            if ("validate".equals(args[0])) {
                if (args.length < 3) {
                    System.err.println("Validate requires data file and shapes file");
                    System.exit(1);
                }

                File dataFile = new File(args[1]);
                File shapesFile = new File(args[2]);

                validator.loadDataModel(dataFile);
                validator.loadShapesModel(shapesFile);
                ValidationResult result = validator.validate();

                if (args.length > 3) {
                    File reportFile = new File(args[3]);
                    validator.exportReport(result, reportFile);
                }

                System.out.println("Validation " + (result.isValid ? "PASSED" : "FAILED"));
                System.out.println("Violations: " + result.violationCount);

            } else if ("create-shapes".equals(args[0])) {
                if (args.length < 2) {
                    System.err.println("Create shapes requires output file");
                    System.exit(1);
                }

                File outputFile = new File(args[1]);
                createRoboticsShapes(outputFile);
                System.out.println("SHACL shapes created: " + outputFile.getName());
            }

        } catch (Exception e) {
            System.err.println("Operation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
