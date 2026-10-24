package com.ayyoub.inferenceengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.TraceGraphView.EdgeKind;
import com.ayyoub.inferenceengine.TraceGraphView.GraphEdge;
import com.ayyoub.inferenceengine.TraceGraphView.GraphModel;
import com.ayyoub.inferenceengine.TraceGraphView.GraphNode;
import com.ayyoub.inferenceengine.TraceGraphView.NodeKind;
import com.ayyoub.inferenceengine.TraceGraphView.RoutePoint;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.AbsenceWitness;
import com.ayyoub.inferenceengine.engine.Derivation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceGraphModelTest {

  private static Fact fact(String id, String attribute, Object value, String derivedBy, int level) {
    return new Fact(id, attribute, "=", value, 1.0, derivedBy, level);
  }

  private static GraphModel smallRun() {
    List<Fact> facts = List.of(fact("F1", "A", true, null, 0), fact("F2", "B", true, null, 0), fact("F3", "G", true, "R1", 1),
        fact("F4", "I", true, "R3", 1));
    List<Derivation> derivations = List.of(new Derivation("R1", "F3", List.of("F1", "F2"), List.of()),
        new Derivation("R3", "F4", List.of("F1"), List.of(new AbsenceWitness("C", "=", true))));

    return TraceGraphView.layout(facts, derivations);
  }

  @Test
  void simpleDerivationRendersFactRuleFact() {
    GraphModel model = smallRun();

    assertTrue(model.factNodes().stream().map(GraphNode::id).toList().containsAll(List.of("F1", "F2", "F3")));
    assertEquals(List.of("R1", "R3"), model.ruleNodes().stream().map(GraphNode::id).sorted().toList());
    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("F1", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("F2", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("R1", "F3", EdgeKind.CONCLUSION)));
  }

  @Test
  void unfiredRulesAreAbsent() {
    GraphModel model = smallRun();

    assertTrue(model.ruleNodes().stream().noneMatch(node -> node.id().equals("R9")));
  }

  @Test
  void initialFactsHaveNoIncomingEdges() {
    GraphModel model = smallRun();

    assertTrue(model.edgesInto("F1").isEmpty());
    assertTrue(model.edgesInto("F2").isEmpty());
  }

  @Test
  void anyRuleShowsOnlySufficientPremise() {
    List<Fact> facts = List.of(fact("F3", "C", true, null, 0), fact("F4", "D", false, null, 0), fact("F5", "H", true, "R2", 1));
    List<Derivation> derivations = List.of(new Derivation("R2", "F5", List.of("F3"), List.of()));

    GraphModel model = TraceGraphView.layout(facts, derivations);

    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("F3", "R2", EdgeKind.PREMISE)));
    assertTrue(model.edgesOutOf("F4").isEmpty());
  }

  @Test
  void negatedPremiseRendersDashedAbsenceEdge() {
    GraphModel model = smallRun();

    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("F1", "R3", EdgeKind.PREMISE)));

    List<TraceGraphView.GraphEdge> absenceEdges = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).toList();

    assertEquals(1, absenceEdges.size());
    assertEquals("R3", absenceEdges.get(0).toId());

    GraphNode badge = model.nodeById(absenceEdges.get(0).fromId());

    assertEquals(NodeKind.ABSENCE, badge.kind());
    assertEquals("C absent", badge.title());
  }

  @Test
  void layersFlowForwardWithoutBackwardEdges() {
    GraphModel model = smallRun();

    for (TraceGraphView.GraphEdge edge : model.edges()) {
      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());

      assertTrue(target.x() >= source.x() || target.kind() == NodeKind.RULE || source.kind() == NodeKind.ABSENCE,
          "Edge " + edge + " points backward across layers");
    }

    GraphNode premise = model.nodeById("F1");
    GraphNode rule = model.nodeById("R1");
    GraphNode conclusion = model.nodeById("F3");

    assertTrue(rule.x() > premise.x());
    assertTrue(conclusion.x() > rule.x());
  }

  @Test
  void nodesDoNotOverlap() {
    GraphModel model = smallRun();

    List<GraphNode> nodes = model.nodes();

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
  void clickTracesUpstreamAndDownstream() {
    GraphModel model = smallRun();
    String badgeId = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).map(GraphEdge::fromId).findFirst().orElseThrow();

    assertEquals(Set.of("R3", "F1", badgeId), TraceGraphView.upstreamOf("F4", model));
    assertEquals(Set.of("R1", "R3", "F3", "F4"), TraceGraphView.downstreamOf("F1", model));
  }

  @Test
  void statsMatchGraphContent() {
    List<Fact> facts = List.of(fact("F1", "A", true, null, 0), fact("F2", "B", true, null, 0), fact("F3", "G", true, "R1", 1));

    GraphModel model = TraceGraphView.layout(facts, List.of(new Derivation("R1", "F3", List.of("F1", "F2"), List.of())));

    assertEquals(facts.size(), model.factNodes().size());
    assertEquals(1, facts.stream().filter(fact -> fact.getDerivedBy() != null).count());
  }

  @Test
  void ruleWithoutPremisesStillLaysOut() {
    List<Fact> facts = List.of(fact("F3", "C", false, null, 0), fact("F6", "R_sym", true, "R12", 1));

    GraphModel model = TraceGraphView.layout(facts, List.of(new Derivation("R12", "F6", List.of(), List.of())));

    assertEquals(1, model.ruleNodes().size());
    assertTrue(model.edges().contains(new TraceGraphView.GraphEdge("R12", "F6", EdgeKind.CONCLUSION)));
  }

  @Test
  void routesTravelOrthogonallyThroughTheGap() {
    GraphModel model = smallRun();

    for (GraphEdge edge : model.edges()) {
      if (edge.kind() == EdgeKind.ABSENCE)
        continue;

      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());
      List<RoutePoint> route = TraceGraphView.routeEdge(edge, source, target);

      assertEquals(source.x() + source.width(), route.get(0).x());
      assertEquals(source.centerY(), route.get(0).y());
      assertEquals(target.x(), route.get(route.size() - 1).x());
      assertEquals(target.centerY(), route.get(route.size() - 1).y());

      // Every segment is axis-aligned and intermediate waypoints stay
      // between the two columns, clear of fact cards.
      for (int index = 1; index < route.size(); index++) {
        RoutePoint before = route.get(index - 1);
        RoutePoint point = route.get(index);

        assertTrue(before.x() == point.x() || before.y() == point.y(), "Route segment is not axis-aligned");
      }

      for (RoutePoint point : route.subList(1, route.size() - 1))
        assertTrue(point.x() >= source.x() + source.width() && point.x() <= target.x(), "Route leaves the inter-column gap");
    }
  }

  @Test
  void absenceRouteDropsVerticallyToItsRule() {
    GraphModel model = smallRun();
    GraphEdge absence = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).findFirst().orElseThrow();
    List<RoutePoint> route = TraceGraphView.routeEdge(absence, model.nodeById(absence.fromId()), model.nodeById(absence.toId()));

    assertEquals(2, route.size());
    assertEquals(route.get(0).x(), route.get(1).x());
  }
}
