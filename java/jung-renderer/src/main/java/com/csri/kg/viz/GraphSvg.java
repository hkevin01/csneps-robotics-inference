package com.csri.kg.viz;

import java.awt.Color;
import java.awt.Dimension;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.visualization.VisualizationImageServer;
import edu.uci.ics.jung.visualization.renderers.Renderer;

/**
 * Minimal JUNG SVG renderer. In production, fetch subgraph from CSNePS HTTP endpoint
 * given a focus term and radius. Here we render a toy graph for demonstration.
 */
public class GraphSvg {

  private record Args(String focus, int radius, String out) {}

  public static void main(String[] args) throws Exception {
    Args a = parseArgs(args);
    // Build a toy subgraph; replace with HTTP call to fetch nodes/edges around 'focus'
    var g = new SparseMultigraph<String, String>();
    List<String> nodes = Arrays.asList(a.focus, a.focus + "-n1", a.focus + "-n2", a.focus + "-n3");
    for (String n : nodes) g.addVertex(n);
    g.addEdge("e1", a.focus, a.focus + "-n1");
    g.addEdge("e2", a.focus, a.focus + "-n2");
    g.addEdge("e3", a.focus + "-n1", a.focus + "-n3");

    Dimension size = new Dimension(800, 600);
    var layout = new FRLayout<>(g);
    layout.setSize(size);

    var vis = new VisualizationImageServer<String, String>(layout, size);
    vis.getRenderContext().setVertexLabelTransformer(v -> v);
    vis.getRenderContext().setEdgeLabelTransformer(e -> e);
    vis.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.CNTR);
    vis.setBackground(Color.WHITE);

    // Render to SVG using Batik
    DOMImplementation domImpl = org.apache.batik.dom.GenericDOMImplementation.getDOMImplementation();
    String svgNS = "http://www.w3.org/2000/svg";
    Document document = domImpl.createDocument(svgNS, "svg", null);
    SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

    // Paint into SVGGraphics2D
    vis.setDoubleBuffered(false);
    vis.addNotify();
    vis.validate();
    vis.setSize(size);
    vis.paint(svgGenerator);

    Path outPath = Path.of(a.out);
    Files.createDirectories(outPath.getParent());
    try (FileWriter w = new FileWriter(outPath.toFile())) {
      svgGenerator.stream(w, true);
    }
    System.out.println("Wrote SVG to " + outPath);
  }

  private static Args parseArgs(String[] argv) {
    String focus = "Focus";
    int radius = 2;
    String out = "graph.svg";
    for (int i = 0; i < argv.length; i++) {
      switch (argv[i]) {
        case "--focus" -> focus = argv[++i];
        case "--radius" -> radius = Integer.parseInt(argv[++i]);
        case "--out" -> out = argv[++i];
      }
    }
    return new Args(focus, radius, out);
  }
}
