package com.ayyoub.inferenceengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.TraceGraphView.EdgeKind;
import com.ayyoub.inferenceengine.TraceGraphView.GraphEdge;
import com.ayyoub.inferenceengine.TraceGraphView.GraphModel;
import com.ayyoub.inferenceengine.TraceGraphView.GraphNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * End-to-end reasoning session over the full 17-rule knowledge base, mirroring the UI run path.
 *
 * <p>
 * Each test maps to spec scenarios in {@code specs/reasoning-trace/spec.md}; the graph structure is asserted on the headless layout model because CI
 * must not launch JavaFX.
 */
class FullRunTraceTest {

  private record Run(
      List<Fact> facts,
      List<com.ayyoub.inferenceengine.engine.Derivation> derivations,
      GraphModel model) {
  }

  private static Run fullRun() throws Exception {
    WorkingMemory memory = new WorkingMemory();
    int index = 1;

    for (Fact template : FactsReader.readFacts("src/main/resources/facts.json"))
      memory.addFact(new Fact("F" + index++, template.getAttribute(), "=", template.getValue(), template.getConfidence(), null, 0));

    new InferenceEngine(memory, new KnowledgeBase()).forwardChaining();

    return new Run(memory.getFacts(), memory.getDerivations(), TraceGraphView.layout(memory.getFacts(), memory.getDerivations()));
  }

  private static Fact byAttribute(List<Fact> facts, String attribute) {
    return facts.stream().filter(fact -> fact.getAttribute().equals(attribute)).findFirst().orElseThrow();
  }

  @Test
  void simpleDerivationShowsFactRuleFact() throws Exception {
    Run run = fullRun();
    Fact premisesA = byAttribute(run.facts(), "A");
    Fact premisesB = byAttribute(run.facts(), "B");
    Fact conclusion = byAttribute(run.facts(), "G");

    assertEquals("R1", conclusion.getDerivedBy());
    assertTrue(run.model().edges().contains(new GraphEdge(premisesA.getFactId(), "R1", EdgeKind.PREMISE)));
    assertTrue(run.model().edges().contains(new GraphEdge(premisesB.getFactId(), "R1", EdgeKind.PREMISE)));
    assertTrue(run.model().edges().contains(new GraphEdge("R1", conclusion.getFactId(), EdgeKind.CONCLUSION)));
  }

  @Test
  void unfiredRuleIsAbsent() throws Exception {
    Run run = fullRun();

    assertTrue(run.model().ruleNodes().stream().noneMatch(node -> node.id().equals("R2")));
    assertEquals(16, run.model().ruleNodes().size());
  }

  @Test
  void initialFactsAppearWithoutIncomingEdges() throws Exception {
    Run run = fullRun();

    for (Fact fact : run.facts().stream().filter(fact -> fact.getDerivedBy() == null).toList())
      assertTrue(run.model().edgesInto(fact.getFactId()).isEmpty(), "Initial fact " + fact.getAttribute() + " has incoming edges");
  }

  @Test
  void disjunctiveRuleShowsOnlySufficientPremises() throws Exception {
    Run run = fullRun();
    Fact highScore = byAttribute(run.facts(), "high_score");
    Fact lowLevel = byAttribute(run.facts(), "low_level");

    Set<String> intoR11 = run.model().edgesInto("R11").stream().map(GraphEdge::fromId).collect(Collectors.toSet());

    assertTrue(intoR11.contains(byAttribute(run.facts(), "G").getFactId()));
    assertTrue(intoR11.contains(highScore.getFactId()));
    assertTrue(run.model().edgesOutOf(lowLevel.getFactId()).stream().noneMatch(edge -> edge.toId().equals("R11")));
  }

  @Test
  void negatedPremiseRendersDashedAbsenceEdge() throws Exception {
    Run run = fullRun();

    assertTrue(run.model().edges().contains(new GraphEdge(byAttribute(run.facts(), "A").getFactId(), "R3", EdgeKind.PREMISE)));

    List<GraphEdge> absenceEdges = run.model().edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).toList();

    assertTrue(absenceEdges.stream().anyMatch(edge -> edge.toId().equals("R3")));
    assertTrue(
        absenceEdges.stream().map(edge -> run.model().nodeById(edge.fromId())).anyMatch(badge -> badge != null && badge.title().equals("C absent")));
  }

  @Test
  void multiLevelRunLaysOutInCausalOrder() throws Exception {
    Run run = fullRun();

    assertEquals(2, byAttribute(run.facts(), "P").getLevel());
    assertEquals(3, byAttribute(run.facts(), "T").getLevel());
    assertEquals(4, byAttribute(run.facts(), "V").getLevel());
    assertEquals(5, byAttribute(run.facts(), "FINAL").getLevel());
    assertTrue(run.model().nodeById(byAttribute(run.facts(), "FINAL").getFactId()).x() > run.model()
        .nodeById(byAttribute(run.facts(), "A").getFactId()).x());

    for (GraphEdge edge : run.model().edges()) {
      GraphNode source = run.model().nodeById(edge.fromId());
      GraphNode target = run.model().nodeById(edge.toId());

      assertTrue(target.x() >= source.x() || target.kind() == TraceGraphView.NodeKind.RULE || source.kind() == TraceGraphView.NodeKind.ABSENCE,
          "Edge " + edge + " points backward across layers");
    }
  }

  @Test
  void fullGraphIsReachableWithoutOverlap() throws Exception {
    Run run = fullRun();

    List<GraphNode> nodes = run.model().nodes();

    for (int left = 0; left < nodes.size(); left++)
      for (int right = left + 1; right < nodes.size(); right++) {
        GraphNode first = nodes.get(left);
        GraphNode second = nodes.get(right);
        boolean separated = first.x() + first.width() <= second.x() || second.x() + second.width() <= first.x()
            || first.y() + first.height() <= second.y() || second.y() + second.height() <= first.y();

        assertTrue(separated, "Nodes " + first.id() + " and " + second.id() + " overlap");
      }
  }

  @Test
  void clickTracesDerivationChain() throws Exception {
    Run run = fullRun();
    String finalId = byAttribute(run.facts(), "FINAL").getFactId();

    Set<String> upstream = TraceGraphView.upstreamOf(finalId, run.model());

    assertTrue(upstream.contains("R17"));
    assertTrue(upstream.contains(byAttribute(run.facts(), "T").getFactId()));
    assertTrue(upstream.contains(byAttribute(run.facts(), "V").getFactId()));
    assertTrue(upstream.contains(byAttribute(run.facts(), "A").getFactId()));

    Set<String> downstream = TraceGraphView.downstreamOf(byAttribute(run.facts(), "A").getFactId(), run.model());

    assertTrue(downstream.contains("R1"));
    assertTrue(downstream.contains("R3"));
    assertTrue(downstream.contains(finalId));
  }

  @Test
  void statsMatchGraphContent() throws Exception {
    Run run = fullRun();

    long derived = run.facts().stream().filter(fact -> fact.getDerivedBy() != null).count();

    assertEquals(23, run.facts().size());
    assertEquals(16, derived);
    assertEquals(run.facts().size(), run.model().factNodes().size());
  }
}
