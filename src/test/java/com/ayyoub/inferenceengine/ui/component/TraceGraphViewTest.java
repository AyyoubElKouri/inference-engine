package com.ayyoub.inferenceengine.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.EdgeKind;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphEdge;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphModel;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphNode;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.NodeKind;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.RoutePoint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceGraphViewTest {

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
  void laysOutFactRuleFactChains() {
    GraphModel model = smallRun();

    assertTrue(model.edges().contains(new GraphEdge("F1", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new GraphEdge("F2", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new GraphEdge("R1", "F3", EdgeKind.CONCLUSION)));
    assertEquals(List.of("R1", "R3"), model.ruleNodes().stream().map(GraphNode::id).sorted().toList());
  }

  @Test
  void unfiredRulesAreAbsent() {
    assertTrue(smallRun().ruleNodes().stream().noneMatch(node -> node.id().equals("R9")));
  }

  @Test
  void negatedPremiseRendersAnAbsenceBadge() {
    GraphModel model = smallRun();
    GraphEdge absence = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).findFirst().orElseThrow();
    GraphNode badge = model.nodeById(absence.fromId());

    assertEquals(NodeKind.ABSENCE, badge.kind());
    assertEquals("C absent", badge.title());
    assertEquals("R3", absence.toId());
  }

  @Test
  void layersFlowForwardWithoutOverlap() {
    GraphModel model = smallRun();
    List<GraphNode> nodes = model.nodes();

    for (GraphEdge edge : model.edges()) {
      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());

      assertTrue(target.x() >= source.x() || target.kind() == NodeKind.RULE || source.kind() == NodeKind.ABSENCE);
    }

    for (int left = 0; left < nodes.size(); left++)
      for (int right = left + 1; right < nodes.size(); right++) {
        GraphNode first = nodes.get(left);
        GraphNode second = nodes.get(right);
        boolean separated = first.x() + first.width() <= second.x() || second.x() + second.width() <= first.x()
            || first.y() + first.height() <= second.y() || second.y() + second.height() <= first.y();

        assertTrue(separated, "Nodes overlap: " + first.id() + " and " + second.id());
      }
  }

  @Test
  void routesStayOrthogonalInTheColumnGap() {
    GraphModel model = smallRun();

    for (GraphEdge edge : model.edges()) {
      if (edge.kind() == EdgeKind.ABSENCE)
        continue;

      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());
      List<RoutePoint> route = TraceGraphView.routeEdge(edge, source, target);

      assertEquals(source.x() + source.width(), route.get(0).x());
      assertEquals(target.x(), route.get(route.size() - 1).x());

      for (int index = 1; index < route.size(); index++) {
        RoutePoint before = route.get(index - 1);
        RoutePoint point = route.get(index);

        assertTrue(before.x() == point.x() || before.y() == point.y());
      }
    }
  }

  @Test
  void traversalFindsUpstreamAndDownstream() {
    GraphModel model = smallRun();
    String badgeId = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).map(GraphEdge::fromId).findFirst().orElseThrow();

    assertEquals(Set.of("R3", "F1", badgeId), TraceGraphView.upstreamOf("F4", model));
    assertEquals(Set.of("R1", "R3", "F3", "F4"), TraceGraphView.downstreamOf("F1", model));
  }
}
