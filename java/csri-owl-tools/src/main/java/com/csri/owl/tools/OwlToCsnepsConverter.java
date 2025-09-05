package com.csri.owl.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitor;
import org.slf4j.LoggerFactory;

/**
 * Converts OWL ontologies to CSNePS frame representations.
 * Maps OWL classes to CSNePS frames and properties to CSNePS slots/relations.
 */
public class OwlToCsnepsConverter {
    private static final Logger logger = LoggerFactory.getLogger(OwlToCsnepsConverter.class);

    private final OWLOntologyManager manager;
    private final Map<String, CsnepsFrame> frames;
    private final Map<String, CsnepsRelation> relations;
    private final Set<String> processedAxioms;

    public OwlToCsnepsConverter() {
        this.manager = OWLManager.createOWLOntologyManager();
        this.frames = new HashMap<>();
        this.relations = new HashMap<>();
        this.processedAxioms = new HashSet<>();
    }

    /**
     * Convert an OWL ontology file to CSNePS representation.
     */
    public ConversionResult convertOntology(File owlFile) throws OWLOntologyCreationException {
        logger.info("Converting OWL ontology: {}", owlFile.getName());

        // Load the ontology
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);

        // Process the ontology
        processOntology(ontology);

        // Create conversion result
        ConversionResult result = new ConversionResult();
        result.frames = new ArrayList<>(frames.values());
        result.relations = new ArrayList<>(relations.values());
        result.sourceFile = owlFile.getName();
        result.classCount = frames.size();
        result.propertyCount = relations.size();
        result.axiomCount = processedAxioms.size();

        logger.info("Conversion complete: {} classes, {} properties, {} axioms",
                   result.classCount, result.propertyCount, result.axiomCount);

        return result;
    }

    /**
     * Process an OWL ontology and extract frames and relations.
     */
    private void processOntology(OWLOntology ontology) {
        // Create a walker to visit all axioms
        OWLOntologyWalker walker = new OWLOntologyWalker(Collections.singleton(ontology));
        OWLOntologyWalkerVisitor visitor = new OWLOntologyWalkerVisitor(walker) {

            @Override
            public void visit(OWLClass cls) {
                processClass(cls);
            }

            @Override
            public void visit(OWLObjectProperty property) {
                processObjectProperty(property);
            }

            @Override
            public void visit(OWLDataProperty property) {
                processDataProperty(property);
            }

            @Override
            public void visit(OWLSubClassOfAxiom axiom) {
                processSubClassAxiom(axiom);
            }

            @Override
            public void visit(OWLEquivalentClassesAxiom axiom) {
                processEquivalentClassesAxiom(axiom);
            }

            @Override
            public void visit(OWLDisjointClassesAxiom axiom) {
                processDisjointClassesAxiom(axiom);
            }
        };

        walker.walkStructure(visitor);
    }

    /**
     * Process an OWL class and create a CSNePS frame.
     */
    private void processClass(OWLClass owlClass) {
        if (owlClass.isOWLThing() || owlClass.isOWLNothing()) {
            return; // Skip built-in classes
        }

        String className = getLocalName(owlClass.getIRI());

        if (!frames.containsKey(className)) {
            CsnepsFrame frame = new CsnepsFrame();
            frame.name = className;
            frame.type = "class";
            frame.iri = owlClass.getIRI().toString();
            frame.slots = new ArrayList<>();
            frame.constraints = new ArrayList<>();

            frames.put(className, frame);
            logger.debug("Created frame: {}", className);
        }
    }

    /**
     * Process an OWL object property and create a CSNePS relation.
     */
    private void processObjectProperty(OWLObjectProperty property) {
        String propertyName = getLocalName(property.getIRI());

        if (!relations.containsKey(propertyName)) {
            CsnepsRelation relation = new CsnepsRelation();
            relation.name = propertyName;
            relation.type = "object-property";
            relation.iri = property.getIRI().toString();
            relation.domain = new ArrayList<>();
            relation.range = new ArrayList<>();
            relation.characteristics = new ArrayList<>();

            relations.put(propertyName, relation);
            logger.debug("Created object relation: {}", propertyName);
        }
    }

    /**
     * Process an OWL data property and create a CSNePS relation.
     */
    private void processDataProperty(OWLDataProperty property) {
        String propertyName = getLocalName(property.getIRI());

        if (!relations.containsKey(propertyName)) {
            CsnepsRelation relation = new CsnepsRelation();
            relation.name = propertyName;
            relation.type = "data-property";
            relation.iri = property.getIRI().toString();
            relation.domain = new ArrayList<>();
            relation.range = new ArrayList<>();
            relation.characteristics = new ArrayList<>();

            relations.put(propertyName, relation);
            logger.debug("Created data relation: {}", propertyName);
        }
    }

    /**
     * Process subclass axioms and add inheritance constraints.
     */
    private void processSubClassAxiom(OWLSubClassOfAxiom axiom) {
        OWLClassExpression subClass = axiom.getSubClass();
        OWLClassExpression superClass = axiom.getSuperClass();

        if (subClass.isOWLClass() && superClass.isOWLClass()) {
            String subName = getLocalName(subClass.asOWLClass().getIRI());
            String superName = getLocalName(superClass.asOWLClass().getIRI());

            CsnepsFrame subFrame = frames.get(subName);
            if (subFrame != null) {
                CsnepsConstraint constraint = new CsnepsConstraint();
                constraint.type = "inheritance";
                constraint.value = superName;
                constraint.description = subName + " is a subclass of " + superName;

                subFrame.constraints.add(constraint);
                processedAxioms.add(axiom.toString());

                logger.debug("Added inheritance: {} -> {}", subName, superName);
            }
        }
    }

    /**
     * Process equivalent classes axioms.
     */
    private void processEquivalentClassesAxiom(OWLEquivalentClassesAxiom axiom) {
        Set<OWLClass> classes = axiom.getClassesInSignature();

        for (OWLClass cls1 : classes) {
            String name1 = getLocalName(cls1.getIRI());
            CsnepsFrame frame1 = frames.get(name1);

            if (frame1 != null) {
                for (OWLClass cls2 : classes) {
                    if (!cls1.equals(cls2)) {
                        String name2 = getLocalName(cls2.getIRI());

                        CsnepsConstraint constraint = new CsnepsConstraint();
                        constraint.type = "equivalence";
                        constraint.value = name2;
                        constraint.description = name1 + " is equivalent to " + name2;

                        frame1.constraints.add(constraint);
                    }
                }
            }
        }

        processedAxioms.add(axiom.toString());
        logger.debug("Processed equivalent classes: {}", classes.size());
    }

    /**
     * Process disjoint classes axioms.
     */
    private void processDisjointClassesAxiom(OWLDisjointClassesAxiom axiom) {
        Set<OWLClass> classes = axiom.getClassesInSignature();

        for (OWLClass cls1 : classes) {
            String name1 = getLocalName(cls1.getIRI());
            CsnepsFrame frame1 = frames.get(name1);

            if (frame1 != null) {
                for (OWLClass cls2 : classes) {
                    if (!cls1.equals(cls2)) {
                        String name2 = getLocalName(cls2.getIRI());

                        CsnepsConstraint constraint = new CsnepsConstraint();
                        constraint.type = "disjointness";
                        constraint.value = name2;
                        constraint.description = name1 + " is disjoint from " + name2;

                        frame1.constraints.add(constraint);
                    }
                }
            }
        }

        processedAxioms.add(axiom.toString());
        logger.debug("Processed disjoint classes: {}", classes.size());
    }

    /**
     * Extract local name from IRI.
     */
    private String getLocalName(IRI iri) {
        String iriString = iri.toString();
        int lastSlash = iriString.lastIndexOf('/');
        int lastHash = iriString.lastIndexOf('#');
        int separator = Math.max(lastSlash, lastHash);

        if (separator >= 0 && separator < iriString.length() - 1) {
            return iriString.substring(separator + 1);
        }

        return iriString;
    }

    /**
     * Export the conversion result to CSNePS format.
     */
    public void exportToCsneps(ConversionResult result, File outputFile) throws IOException {
        logger.info("Exporting to CSNePS format: {}", outputFile.getName());

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(";; CSNePS Knowledge Base generated from OWL ontology\n");
            writer.write(";; Source: " + result.sourceFile + "\n");
            writer.write(";; Generated: " + new Date() + "\n\n");

            // Export frames
            writer.write(";; ========== FRAMES ==========\n\n");
            for (CsnepsFrame frame : result.frames) {
                exportFrame(writer, frame);
            }

            // Export relations
            writer.write(";; ========== RELATIONS ==========\n\n");
            for (CsnepsRelation relation : result.relations) {
                exportRelation(writer, relation);
            }

            writer.flush();
        }

        logger.info("Export complete: {} frames, {} relations",
                   result.frames.size(), result.relations.size());
    }

    private void exportFrame(FileWriter writer, CsnepsFrame frame) throws IOException {
        writer.write(String.format("(defframe %s\n", frame.name));
        writer.write(String.format("  :type %s\n", frame.type));
        writer.write(String.format("  :iri \"%s\"\n", frame.iri));

        if (!frame.slots.isEmpty()) {
            writer.write("  :slots (\n");
            for (CsnepsSlot slot : frame.slots) {
                writer.write(String.format("    (%s :type %s", slot.name, slot.type));
                if (slot.cardinality != null) {
                    writer.write(String.format(" :cardinality %s", slot.cardinality));
                }
                writer.write(")\n");
            }
            writer.write("  )\n");
        }

        if (!frame.constraints.isEmpty()) {
            writer.write("  :constraints (\n");
            for (CsnepsConstraint constraint : frame.constraints) {
                writer.write(String.format("    (%s \"%s\" \"%s\")\n",
                           constraint.type, constraint.value, constraint.description));
            }
            writer.write("  )\n");
        }

        writer.write(")\n\n");
    }

    private void exportRelation(FileWriter writer, CsnepsRelation relation) throws IOException {
        writer.write(String.format("(defrelation %s\n", relation.name));
        writer.write(String.format("  :type %s\n", relation.type));
        writer.write(String.format("  :iri \"%s\"\n", relation.iri));

        if (!relation.domain.isEmpty()) {
            writer.write("  :domain (" + String.join(" ", relation.domain) + ")\n");
        }

        if (!relation.range.isEmpty()) {
            writer.write("  :range (" + String.join(" ", relation.range) + ")\n");
        }

        if (!relation.characteristics.isEmpty()) {
            writer.write("  :characteristics (" + String.join(" ", relation.characteristics) + ")\n");
        }

        writer.write(")\n\n");
    }

    // Data classes for conversion results
    public static class ConversionResult {
        public List<CsnepsFrame> frames;
        public List<CsnepsRelation> relations;
        public String sourceFile;
        public int classCount;
        public int propertyCount;
        public int axiomCount;
    }

    public static class CsnepsFrame {
        public String name;
        public String type;
        public String iri;
        public List<CsnepsSlot> slots;
        public List<CsnepsConstraint> constraints;
    }

    public static class CsnepsRelation {
        public String name;
        public String type;
        public String iri;
        public List<String> domain;
        public List<String> range;
        public List<String> characteristics;
    }

    public static class CsnepsSlot {
        public String name;
        public String type;
        public String cardinality;
    }

    public static class CsnepsConstraint {
        public String type;
        public String value;
        public String description;
    }

    // Main method for command-line usage
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar csri-owl-tools.jar <input-owl-file> <output-csneps-file>");
            System.exit(1);
        }

        try {
            File inputFile = new File(args[0]);
            File outputFile = new File(args[1]);

            OwlToCsnepsConverter converter = new OwlToCsnepsConverter();
            ConversionResult result = converter.convertOntology(inputFile);
            converter.exportToCsneps(result, outputFile);

            System.out.println("Conversion successful!");
            System.out.println("Frames: " + result.classCount);
            System.out.println("Relations: " + result.propertyCount);
            System.out.println("Axioms: " + result.axiomCount);

        } catch (Exception e) {
            System.err.println("Conversion failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
