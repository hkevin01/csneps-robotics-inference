package com.csri.owl.tools;

import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Enhanced OWL to CSNePS converter with support for:
 * - Inverse properties → bidirectional role mappings
 * - Property chains → CSNePS rules
 * - EquivalentClasses and DisjointClasses → constraints/rules
 * - Advanced property characteristics (transitivity, symmetry, etc.)
 */
public class EnhancedOwlToCsnepsConverter extends OwlToCsnepsConverter {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedOwlToCsnepsConverter.class);

    private final List<CsnepsRule> rules;
    private final Map<String, String> inversePropertyMappings;
    private final List<PropertyChain> propertyChains;

    public EnhancedOwlToCsnepsConverter() {
        super();
        this.rules = new ArrayList<>();
        this.inversePropertyMappings = new HashMap<>();
        this.propertyChains = new ArrayList<>();
    }

    /**
     * Enhanced conversion with advanced OWL constructs.
     */
    @Override
    public ConversionResult convertOntology(java.io.File owlFile) throws OWLOntologyCreationException {
        logger.info("Enhanced conversion of OWL ontology: {}", owlFile.getName());

        // First, run the base conversion
        ConversionResult baseResult = super.convertOntology(owlFile);

        // Reload ontology for enhanced processing
        OWLOntologyManager manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);

        // Process advanced constructs
        processInverseProperties(ontology);
        processPropertyChains(ontology);
        processTransitiveProperties(ontology);
        processSymmetricProperties(ontology);
        processEquivalentClasses(ontology);
        processDisjointClasses(ontology);
        processDomainRangeConstraints(ontology);

        // Create enhanced result
        EnhancedConversionResult result = new EnhancedConversionResult();
        result.frames = baseResult.frames;
        result.relations = baseResult.relations;
        result.sourceFile = baseResult.sourceFile;
        result.classCount = baseResult.classCount;
        result.propertyCount = baseResult.propertyCount;
        result.axiomCount = baseResult.axiomCount;

        // Add enhanced features
        result.rules = new ArrayList<>(rules);
        result.inversePropertyMappings = new HashMap<>(inversePropertyMappings);
        result.propertyChains = new ArrayList<>(propertyChains);
        result.ruleCount = rules.size();

        logger.info("Enhanced conversion complete: {} rules, {} inverse mappings, {} property chains",
                   result.ruleCount, result.inversePropertyMappings.size(), result.propertyChains.size());

        return result;
    }

    /**
     * Process inverse object properties for bidirectional mappings.
     */
    private void processInverseProperties(OWLOntology ontology) {
        for (OWLInverseObjectPropertiesAxiom axiom : ontology.getAxioms(AxiomType.INVERSE_OBJECT_PROPERTIES)) {
            OWLObjectPropertyExpression prop1 = axiom.getFirstProperty();
            OWLObjectPropertyExpression prop2 = axiom.getSecondProperty();

            if (prop1.isOWLObjectProperty() && prop2.isOWLObjectProperty()) {
                String name1 = getLocalName(prop1.asOWLObjectProperty().getIRI());
                String name2 = getLocalName(prop2.asOWLObjectProperty().getIRI());

                inversePropertyMappings.put(name1, name2);
                inversePropertyMappings.put(name2, name1);

                // Create CSNePS rule for inverse property
                CsnepsRule rule = new CsnepsRule();
                rule.name = "inverse-" + name1 + "-" + name2;
                rule.type = "inverse-property";
                rule.description = name1 + " is inverse of " + name2;
                rule.condition = String.format("(and (?x %s ?y))", name1);
                rule.conclusion = String.format("(?y %s ?x)", name2);
                rule.bidirectional = true;

                rules.add(rule);

                logger.debug("Added inverse property mapping: {} <-> {}", name1, name2);
            }
        }
    }

    /**
     * Process property chain axioms into CSNePS rules.
     */
    private void processPropertyChains(OWLOntology ontology) {
        for (OWLSubPropertyChainOfAxiom axiom : ontology.getAxioms(AxiomType.SUB_PROPERTY_CHAIN_OF)) {
            List<OWLObjectPropertyExpression> chain = axiom.getPropertyChain();
            OWLObjectPropertyExpression superProperty = axiom.getSuperProperty();

            if (superProperty.isOWLObjectProperty() && chain.size() >= 2) {
                String superPropName = getLocalName(superProperty.asOWLObjectProperty().getIRI());

                List<String> chainNames = new ArrayList<>();
                for (OWLObjectPropertyExpression prop : chain) {
                    if (prop.isOWLObjectProperty()) {
                        chainNames.add(getLocalName(prop.asOWLObjectProperty().getIRI()));
                    }
                }

                if (chainNames.size() == chain.size()) {
                    PropertyChain propertyChain = new PropertyChain();
                    propertyChain.chainProperties = chainNames;
                    propertyChain.resultProperty = superPropName;
                    propertyChains.add(propertyChain);

                    // Create CSNePS rule for property chain
                    CsnepsRule rule = new CsnepsRule();
                    rule.name = "chain-" + superPropName + "-" + String.join("-", chainNames);
                    rule.type = "property-chain";
                    rule.description = String.format("Property chain: %s → %s", String.join(" ∘ ", chainNames), superPropName);

                    // Build condition for chain
                    StringBuilder condition = new StringBuilder("(and ");
                    char var = 'x';
                    for (int i = 0; i < chainNames.size(); i++) {
                        if (i == 0) {
                            condition.append(String.format("(?%c %s ?%c) ", var, chainNames.get(i), (char)(var + 1)));
                        } else {
                            condition.append(String.format("(?%c %s ?%c) ", (char)(var + i), chainNames.get(i), (char)(var + i + 1)));
                        }
                    }
                    condition.append(")");

                    rule.condition = condition.toString();
                    rule.conclusion = String.format("(?x %s ?%c)", superPropName, (char)(var + chainNames.size()));

                    rules.add(rule);

                    logger.debug("Added property chain rule: {} → {}", String.join(" ∘ ", chainNames), superPropName);
                }
            }
        }
    }

    /**
     * Process transitive properties.
     */
    private void processTransitiveProperties(OWLOntology ontology) {
        for (OWLTransitiveObjectPropertyAxiom axiom : ontology.getAxioms(AxiomType.TRANSITIVE_OBJECT_PROPERTY)) {
            OWLObjectPropertyExpression property = axiom.getProperty();

            if (property.isOWLObjectProperty()) {
                String propName = getLocalName(property.asOWLObjectProperty().getIRI());

                CsnepsRule rule = new CsnepsRule();
                rule.name = "transitive-" + propName;
                rule.type = "transitivity";
                rule.description = propName + " is transitive";
                rule.condition = String.format("(and (?x %s ?y) (?y %s ?z))", propName, propName);
                rule.conclusion = String.format("(?x %s ?z)", propName);

                rules.add(rule);

                logger.debug("Added transitivity rule for: {}", propName);
            }
        }
    }

    /**
     * Process symmetric properties.
     */
    private void processSymmetricProperties(OWLOntology ontology) {
        for (OWLSymmetricObjectPropertyAxiom axiom : ontology.getAxioms(AxiomType.SYMMETRIC_OBJECT_PROPERTY)) {
            OWLObjectPropertyExpression property = axiom.getProperty();

            if (property.isOWLObjectProperty()) {
                String propName = getLocalName(property.asOWLObjectProperty().getIRI());

                CsnepsRule rule = new CsnepsRule();
                rule.name = "symmetric-" + propName;
                rule.type = "symmetry";
                rule.description = propName + " is symmetric";
                rule.condition = String.format("(?x %s ?y)", propName);
                rule.conclusion = String.format("(?y %s ?x)", propName);
                rule.bidirectional = true;

                rules.add(rule);

                logger.debug("Added symmetry rule for: {}", propName);
            }
        }
    }

    /**
     * Process equivalent classes into rules.
     */
    private void processEquivalentClasses(OWLOntology ontology) {
        for (OWLEquivalentClassesAxiom axiom : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
            Set<OWLClass> classes = axiom.getClassesInSignature();

            if (classes.size() == 2) {
                List<OWLClass> classList = new ArrayList<>(classes);
                String class1 = getLocalName(classList.get(0).getIRI());
                String class2 = getLocalName(classList.get(1).getIRI());

                // Create bidirectional equivalence rules
                CsnepsRule rule1 = new CsnepsRule();
                rule1.name = "equiv-" + class1 + "-to-" + class2;
                rule1.type = "class-equivalence";
                rule1.description = class1 + " equivalent to " + class2;
                rule1.condition = String.format("(?x isa %s)", class1);
                rule1.conclusion = String.format("(?x isa %s)", class2);

                CsnepsRule rule2 = new CsnepsRule();
                rule2.name = "equiv-" + class2 + "-to-" + class1;
                rule2.type = "class-equivalence";
                rule2.description = class2 + " equivalent to " + class1;
                rule2.condition = String.format("(?x isa %s)", class2);
                rule2.conclusion = String.format("(?x isa %s)", class1);

                rules.add(rule1);
                rules.add(rule2);

                logger.debug("Added equivalence rules: {} <-> {}", class1, class2);
            }
        }
    }

    /**
     * Process disjoint classes into constraint rules.
     */
    private void processDisjointClasses(OWLOntology ontology) {
        for (OWLDisjointClassesAxiom axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
            Set<OWLClass> classes = axiom.getClassesInSignature();

            for (OWLClass class1 : classes) {
                for (OWLClass class2 : classes) {
                    if (!class1.equals(class2)) {
                        String name1 = getLocalName(class1.getIRI());
                        String name2 = getLocalName(class2.getIRI());

                        CsnepsRule rule = new CsnepsRule();
                        rule.name = "disjoint-" + name1 + "-" + name2;
                        rule.type = "disjointness-constraint";
                        rule.description = name1 + " is disjoint from " + name2;
                        rule.condition = String.format("(and (?x isa %s) (?x isa %s))", name1, name2);
                        rule.conclusion = "contradiction";
                        rule.isConstraint = true;

                        rules.add(rule);

                        logger.debug("Added disjointness constraint: {} ⊥ {}", name1, name2);
                    }
                }
            }
        }
    }

    /**
     * Process domain and range constraints.
     */
    private void processDomainRangeConstraints(OWLOntology ontology) {
        // Process object property domains
        for (OWLObjectPropertyDomainAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_DOMAIN)) {
            OWLObjectPropertyExpression property = axiom.getProperty();
            OWLClassExpression domain = axiom.getDomain();

            if (property.isOWLObjectProperty() && domain.isOWLClass()) {
                String propName = getLocalName(property.asOWLObjectProperty().getIRI());
                String domainName = getLocalName(domain.asOWLClass().getIRI());

                CsnepsRule rule = new CsnepsRule();
                rule.name = "domain-" + propName + "-" + domainName;
                rule.type = "domain-constraint";
                rule.description = "Domain of " + propName + " is " + domainName;
                rule.condition = String.format("(?x %s ?y)", propName);
                rule.conclusion = String.format("(?x isa %s)", domainName);

                rules.add(rule);

                logger.debug("Added domain constraint: {} → {}", propName, domainName);
            }
        }

        // Process object property ranges
        for (OWLObjectPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_RANGE)) {
            OWLObjectPropertyExpression property = axiom.getProperty();
            OWLClassExpression range = axiom.getRange();

            if (property.isOWLObjectProperty() && range.isOWLClass()) {
                String propName = getLocalName(property.asOWLObjectProperty().getIRI());
                String rangeName = getLocalName(range.asOWLClass().getIRI());

                CsnepsRule rule = new CsnepsRule();
                rule.name = "range-" + propName + "-" + rangeName;
                rule.type = "range-constraint";
                rule.description = "Range of " + propName + " is " + rangeName;
                rule.condition = String.format("(?x %s ?y)", propName);
                rule.conclusion = String.format("(?y isa %s)", rangeName);

                rules.add(rule);

                logger.debug("Added range constraint: {} → {}", propName, rangeName);
            }
        }
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
     * Export enhanced results to EDN format for CSNePS.
     */
    public void exportToEdn(EnhancedConversionResult result, java.io.File outputFile) throws IOException {
        logger.info("Exporting enhanced conversion to EDN format: {}", outputFile.getName());

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(";; Enhanced CSNePS Knowledge Base generated from OWL ontology\n");
            writer.write(";; Source: " + result.sourceFile + "\n");
            writer.write(";; Generated: " + new Date() + "\n");
            writer.write(";; Rules: " + result.ruleCount + "\n");
            writer.write(";; Property chains: " + result.propertyChains.size() + "\n");
            writer.write(";; Inverse mappings: " + result.inversePropertyMappings.size() + "\n\n");

            writer.write("{\n");

            // Export frames
            writer.write(" :frames #{\n");
            for (CsnepsFrame frame : result.frames) {
                exportFrameEdn(writer, frame);
            }
            writer.write(" }\n\n");

            // Export relations
            writer.write(" :relations #{\n");
            for (CsnepsRelation relation : result.relations) {
                exportRelationEdn(writer, relation);
            }
            writer.write(" }\n\n");

            // Export rules
            writer.write(" :rules [\n");
            for (CsnepsRule rule : result.rules) {
                exportRuleEdn(writer, rule);
            }
            writer.write(" ]\n\n");

            // Export inverse mappings
            writer.write(" :inverse-properties {\n");
            for (Map.Entry<String, String> entry : result.inversePropertyMappings.entrySet()) {
                writer.write(String.format("  :%s :%s\n", entry.getKey(), entry.getValue()));
            }
            writer.write(" }\n\n");

            // Export property chains
            writer.write(" :property-chains [\n");
            for (PropertyChain chain : result.propertyChains) {
                writer.write("  {:chain [");
                for (String prop : chain.chainProperties) {
                    writer.write(":" + prop + " ");
                }
                writer.write("] :result :" + chain.resultProperty + "}\n");
            }
            writer.write(" ]\n");

            writer.write("}\n");
        }

        logger.info("Enhanced export complete");
    }

    private void exportFrameEdn(FileWriter writer, CsnepsFrame frame) throws IOException {
        writer.write(String.format("  {:name :%s :type :%s :iri \"%s\"", frame.name, frame.type, frame.iri));

        if (!frame.constraints.isEmpty()) {
            writer.write(" :constraints [");
            for (CsnepsConstraint constraint : frame.constraints) {
                writer.write(String.format("{:type :%s :value \"%s\"} ", constraint.type, constraint.value));
            }
            writer.write("]");
        }

        writer.write("}\n");
    }

    private void exportRelationEdn(FileWriter writer, CsnepsRelation relation) throws IOException {
        writer.write(String.format("  {:name :%s :type :%s :iri \"%s\"", relation.name, relation.type, relation.iri));

        if (!relation.characteristics.isEmpty()) {
            writer.write(" :characteristics [");
            for (String characteristic : relation.characteristics) {
                writer.write(":" + characteristic + " ");
            }
            writer.write("]");
        }

        writer.write("}\n");
    }

    private void exportRuleEdn(FileWriter writer, CsnepsRule rule) throws IOException {
        writer.write("  {:name \"" + rule.name + "\"\n");
        writer.write("   :type \"" + rule.type + "\"\n");
        writer.write("   :description \"" + rule.description + "\"\n");
        writer.write("   :condition \"" + rule.condition + "\"\n");
        writer.write("   :conclusion \"" + rule.conclusion + "\"");

        if (rule.bidirectional) {
            writer.write("\n   :bidirectional true");
        }

        if (rule.isConstraint) {
            writer.write("\n   :constraint true");
        }

        writer.write("}\n");
    }

    // Data structures for enhanced features
    public static class EnhancedConversionResult extends ConversionResult {
        public List<CsnepsRule> rules;
        public Map<String, String> inversePropertyMappings;
        public List<PropertyChain> propertyChains;
        public int ruleCount;
    }

    public static class CsnepsRule {
        public String name;
        public String type;
        public String description;
        public String condition;
        public String conclusion;
        public boolean bidirectional = false;
        public boolean isConstraint = false;
    }

    public static class PropertyChain {
        public List<String> chainProperties;
        public String resultProperty;
    }
}
