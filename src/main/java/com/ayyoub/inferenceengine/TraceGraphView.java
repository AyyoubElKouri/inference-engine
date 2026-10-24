package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.engine.Derivation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;

/**
 * Bipartite visual trace of one forward-chaining run.
 *
 * <p>
 * Fact nodes and rule nodes are laid out in layers derived from fact level, with directed edges premise → rule → conclusion and dashed absence edges
 * from absence badges to rule nodes. The static {@code layout} method computes positions without JavaFX so the graph structure stays verifiable
 * headlessly; {@code build} renders it into a pannable, zoomable scroll pane with click-to-highlight of proof chains.
 */
public class TraceGraphView {

  /**
   * Node kinds in the trace graph.
   */
  public enum NodeKind {
    FACT, RULE, ABSENCE
  }

  /**
   * Edge kinds in the trace graph.
   */
  public enum EdgeKind {
    PREMISE, CONCLUSION, ABSENCE
  }

  /**
   * One positioned node: fact card, rule pill, or absence badge.
   */
  public record GraphNode(
      String id,
      NodeKind kind,
      String title,
      String detail,
      int level,
      double x,
      double y,
      double width,
      double height) {

    /**
     * Horizontal center, for edge routing.
     */
    public double centerX() {
      return x + width / 2;
    }

    /**
     * Vertical center, for edge routing.
     */
    public double centerY() {
      return y + height / 2;
    }
  }

  /**
   * One directed edge between positioned nodes.
   */
  public record GraphEdge(
      String fromId,
      String toId,
      EdgeKind kind) {
  }

  /**
   * One waypoint of a routed edge, in canvas coordinates.
   */
  public record RoutePoint(
      double x,
      double y) {
  }

  /**
   * Layout result: positioned nodes plus directed edges, with lookup helpers.
   */
  public record GraphModel(
      List<GraphNode> nodes,
      List<GraphEdge> edges) {

    /**
     * Node by id, or null when absent.
     */
    public GraphNode nodeById(String id) {
      return nodes.stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Edges pointing into a node.
     */
    public List<GraphEdge> edgesInto(String id) {
      return edges.stream().filter(edge -> edge.toId().equals(id)).toList();
    }

    /**
     * Edges leaving a node.
     */
    public List<GraphEdge> edgesOutOf(String id) {
      return edges.stream().filter(edge -> edge.fromId().equals(id)).toList();
    }

    /**
     * Fact nodes only.
     */
    public List<GraphNode> factNodes() {
      return nodes.stream().filter(node -> node.kind() == NodeKind.FACT).toList();
    }

    /**
     * Rule nodes only.
     */
    public List<GraphNode> ruleNodes() {
      return nodes.stream().filter(node -> node.kind() == NodeKind.RULE).toList();
    }
  }

  private static final double FACT_W = 170;
  private static final double FACT_H = 72;
  private static final double RULE_W = 104;
  private static final double RULE_H = 40;
  private static final double BADGE_W = 108;
  private static final double BADGE_H = 26;
  private static final double H_GAP = 200;
  private static final double V_GAP = 16;
  private static final double RULE_GAP = 48;
  private static final double PAD = 48;

  private static final String SURFACE = "#FFFFFF";
  private static final String BORDER = "#E5E7EB";
  private static final String PRIMARY = "#1B4FD8";
  private static final String TXT1 = "#0D0D14";
  private static final String TXT2 = "#6B7280";
  private static final String TXT3 = "#9CA3AF";
  private static final String RULE_BG = "#7C3AED";
  private static final String SUCCESS = "#059669";

  private final GraphModel model;
  private final Map<String, Fact> factsById = new HashMap<>();
  private final Map<String, List<Derivation>> derivationsByRule = new LinkedHashMap<>();

  private final Map<String, Node> nodeViews = new HashMap<>();
  private final List<EdgeView> edgeViews = new ArrayList<>();
  private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

  private String selectedId;
  private Label zoomLabel;

  /**
   * Builds the view state for one run; rendering happens in {@code build}.
   */
  public TraceGraphView(List<Fact> facts, List<Derivation> derivations) {
    this.model = layout(facts, derivations);

    for (Fact fact : facts)
      factsById.put(fact.getFactId(), fact);

    for (Derivation derivation : derivations)
      derivationsByRule.computeIfAbsent(derivation.ruleId(), key -> new ArrayList<>()).add(derivation);
  }

  /**
   * Layout result for this view, for stats cross-checks.
   */
  public GraphModel model() {
    return model;
  }

  /**
   * Computes node positions and edges for facts and derivations, without JavaFX.
   *
   * <p>
   * Facts stack in columns by level along x; each fired rule sits between its deepest premise layer and its shallowest conclusion layer at the mean
   * height of its connected facts; absence badges stack above their rule node.
   */
  public static GraphModel layout(List<Fact> facts, List<Derivation> derivations) {
    Map<Integer, List<Fact>> byLevel = new TreeMap<>();

    for (Fact fact : facts)
      byLevel.computeIfAbsent(fact.getLevel(), key -> new ArrayList<>()).add(fact);

    for (List<Fact> column : byLevel.values())
      column.sort((left, right) -> {
        int compared = left.getAttribute().compareTo(right.getAttribute());

        return compared != 0 ? compared : left.getFactId().compareTo(right.getFactId());
      });

    double maxColumnHeight = byLevel.values().stream().mapToDouble(column -> columnHeight(column.size())).max().orElse(0);

    List<GraphNode> nodes = new ArrayList<>();
    Map<String, GraphNode> nodesById = new HashMap<>();

    for (Map.Entry<Integer, List<Fact>> entry : byLevel.entrySet()) {
      int level = entry.getKey();
      List<Fact> column = entry.getValue();
      double top = PAD + (maxColumnHeight - columnHeight(column.size())) / 2;

      for (int index = 0; index < column.size(); index++) {
        Fact fact = column.get(index);
        GraphNode node = new GraphNode(fact.getFactId(), NodeKind.FACT, fact.getAttribute(), fact.getOperator() + " " + fact.getValue(), level,
            xForLevel(level), top + index * (FACT_H + V_GAP), FACT_W, FACT_H);

        nodes.add(node);
        nodesById.put(node.id(), node);
      }
    }

    Map<String, List<Derivation>> grouped = new LinkedHashMap<>();

    for (Derivation derivation : derivations)
      grouped.computeIfAbsent(derivation.ruleId(), key -> new ArrayList<>()).add(derivation);

    List<GraphNode> ruleNodes = new ArrayList<>();

    for (Map.Entry<String, List<Derivation>> entry : grouped.entrySet()) {
      String ruleId = entry.getKey();
      List<String> premiseIds = entry.getValue().stream().flatMap(derivation -> derivation.premiseIds().stream()).distinct().toList();
      List<String> conclusionIds = entry.getValue().stream().map(Derivation::conclusionId).distinct().toList();

      int premiseLevel = premiseIds.stream().map(nodesById::get).filter(node -> node != null).mapToInt(GraphNode::level).max().orElse(0);
      int conclusionLevel = conclusionIds.stream().map(nodesById::get).filter(node -> node != null).mapToInt(GraphNode::level).min()
          .orElse(premiseLevel + 1);

      // A rule without premises (e.g. at_most holding vacuously) anchors to
      // the gap just before its conclusions so its badges stay clear of facts.
      int anchorLevel = premiseIds.isEmpty() ? Math.max(conclusionLevel - 1, 0) : premiseLevel;
      double premiseRight = xForLevel(anchorLevel) + FACT_W;
      double conclusionX = xForLevel(Math.max(conclusionLevel, anchorLevel));
      double ruleX = premiseRight + Math.max(24, (conclusionX - premiseRight - RULE_W) / 2);

      List<GraphNode> connected = new ArrayList<>();
      premiseIds.stream().map(nodesById::get).filter(node -> node != null).forEach(connected::add);
      conclusionIds.stream().map(nodesById::get).filter(node -> node != null).forEach(connected::add);

      double ruleY = connected.isEmpty()
          ? PAD + maxColumnHeight / 2
          : connected.stream().mapToDouble(GraphNode::centerY).average().orElse(PAD) - RULE_H / 2;

      GraphNode ruleNode = new GraphNode(ruleId, NodeKind.RULE, ruleId, "", premiseLevel, ruleX, Math.max(PAD, ruleY), RULE_W, RULE_H);

      ruleNodes.add(ruleNode);
    }

    // De-collide rule nodes so stacked rules never overlap.
    ruleNodes.sort((left, right) -> Double.compare(left.y(), right.y()));

    double cursor = PAD;

    for (int index = 0; index < ruleNodes.size(); index++) {
      GraphNode node = ruleNodes.get(index);
      double placed = Math.max(node.y(), cursor);
      ruleNodes.set(index,
          new GraphNode(node.id(), node.kind(), node.title(), node.detail(), node.level(), node.x(), placed, node.width(), node.height()));
      cursor = placed + RULE_H + RULE_GAP;
    }

    List<GraphEdge> edges = new ArrayList<>();

    for (GraphNode ruleNode : ruleNodes) {
      nodes.add(ruleNode);
      nodesById.put(ruleNode.id(), ruleNode);

      List<Derivation> group = grouped.get(ruleNode.id());

      for (String premiseId : group.stream().flatMap(derivation -> derivation.premiseIds().stream()).distinct().toList())
        if (nodesById.containsKey(premiseId))
          addEdge(edges, premiseId, ruleNode.id(), EdgeKind.PREMISE);

      for (String conclusionId : group.stream().map(Derivation::conclusionId).distinct().toList())
        if (nodesById.containsKey(conclusionId))
          addEdge(edges, ruleNode.id(), conclusionId, EdgeKind.CONCLUSION);

      List<AbsenceWitness> witnesses = group.stream().flatMap(derivation -> derivation.absences().stream()).distinct().toList();

      for (int index = 0; index < witnesses.size(); index++) {
        AbsenceWitness witness = witnesses.get(index);
        double badgeX = ruleNode.x() + (RULE_W - BADGE_W) / 2;
        double above = ruleNode.y() - (index + 1) * (BADGE_H + 10);
        double badgeY = above >= 8 ? above : ruleNode.y() + RULE_H + 10 + index * (BADGE_H + 8);
        String badgeId = ruleNode.id() + "|absent|" + witness.attribute() + witness.operator() + witness.value();
        GraphNode badge = new GraphNode(badgeId, NodeKind.ABSENCE, witness.label(), "", ruleNode.level(), badgeX, badgeY, BADGE_W, BADGE_H);

        nodes.add(badge);
        nodesById.put(badge.id(), badge);
        addEdge(edges, badge.id(), ruleNode.id(), EdgeKind.ABSENCE);
      }
    }

    return new GraphModel(List.copyOf(nodes), List.copyOf(edges));
  }

  /**
   * Orthogonal route for an edge, computed without JavaFX.
   *
   * <p>
   * Premise and conclusion edges leave the source horizontally, travel vertically in the free gap between columns, and enter the target horizontally,
   * so lines never cut through fact cards. Absence edges drop straight from the badge to its rule node.
   */
  public static List<RoutePoint> routeEdge(GraphEdge edge, GraphNode source, GraphNode target) {
    if (edge.kind() == EdgeKind.ABSENCE)
      return List.of(new RoutePoint(source.centerX(), source.y() + source.height()), new RoutePoint(target.centerX(), target.y()));

    double startX = source.x() + source.width();
    double startY = source.centerY();
    double endX = target.x();
    double endY = target.centerY();

    if (Math.abs(endY - startY) < 1)
      return List.of(new RoutePoint(startX, startY), new RoutePoint(endX, endY));

    double midX = startX + (endX - startX) / 2;

    return List.of(new RoutePoint(startX, startY), new RoutePoint(midX, startY), new RoutePoint(midX, endY), new RoutePoint(endX, endY));
  }

  /**
   * Full upstream proof chain of a node: all ancestor facts, rules, and absence badges.
   */
  public static Set<String> upstreamOf(String startId, GraphModel model) {
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();

    queue.add(startId);

    while (!queue.isEmpty()) {
      String current = queue.poll();

      for (GraphEdge edge : model.edgesInto(current))
        if (visited.add(edge.fromId()))
          queue.add(edge.fromId());
    }

    return visited;
  }

  /**
   * Full downstream consequences of a node: every fact and rule derived from it.
   */
  public static Set<String> downstreamOf(String startId, GraphModel model) {
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();

    queue.add(startId);

    while (!queue.isEmpty()) {
      String current = queue.poll();

      for (GraphEdge edge : model.edgesOutOf(current))
        if (visited.add(edge.toId()))
          queue.add(edge.toId());
    }

    return visited;
  }

  /**
   * Renders the graph into a pannable, zoomable view with proof-chain highlighting.
   */
  public VBox build() {
    Pane canvas = new Pane();
    canvas.setStyle("-fx-background-color: #F7F8FA;");
    canvas.setOnMouseClicked(event -> clearSelection());

    for (GraphEdge edge : model.edges())
      edgeViews.add(new EdgeView(edge, model.nodeById(edge.fromId()), model.nodeById(edge.toId())));

    for (GraphNode node : model.nodes()) {
      Node view = switch (node.kind()) {
        case FACT -> buildFactCard(node);
        case RULE -> buildRuleNode(node);
        case ABSENCE -> buildBadge(node);
      };

      view.setLayoutX(node.x());
      view.setLayoutY(node.y());
      canvas.getChildren().add(view);
      nodeViews.put(node.id(), view);
    }

    for (EdgeView edgeView : edgeViews)
      canvas.getChildren().add(0, edgeView.group());

    double contentWidth = model.nodes().stream().mapToDouble(node -> node.x() + node.width()).max().orElse(0) + PAD;
    double contentHeight = model.nodes().stream().mapToDouble(node -> node.y() + node.height()).max().orElse(0) + PAD;

    canvas.setPrefSize(Math.max(contentWidth, 400), Math.max(contentHeight, 300));

    Group zoomGroup = new Group(canvas);
    zoomGroup.scaleXProperty().bind(zoom);
    zoomGroup.scaleYProperty().bind(zoom);

    ScrollPane scroll = new ScrollPane(zoomGroup);
    scroll.setPannable(true);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    scroll.setStyle("-fx-background: #F7F8FA; -fx-background-color: #F7F8FA;");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    // Plain scroll pans; control-scroll zooms around the cursor.
    scroll.setOnScroll(event -> {
      if (event.isControlDown()) {
        setZoom(zoom.get() * (event.getDeltaY() > 0 ? 1.1 : 0.9));
        event.consume();
      }
    });

    zoomLabel = new Label(zoomText());
    zoomLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + TXT2 + ";");

    Button zoomOut = smallButton("−");
    zoomOut.setOnAction(event -> setZoom(zoom.get() / 1.2));

    Button zoomIn = smallButton("+");
    zoomIn.setOnAction(event -> setZoom(zoom.get() * 1.2));

    Button reset = smallButton("Réinitialiser");
    reset.setOnAction(event -> {
      setZoom(1.0);
      clearSelection();
    });

    Label hint = new Label("Cliquer un nœud pour tracer sa chaîne de preuve ; cliquer le fond pour tout réafficher.");
    hint.setStyle("-fx-font-size: 11; -fx-text-fill: " + TXT3 + ";");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox controls = new HBox(8, zoomOut, zoomLabel, zoomIn, reset, spacer, hint);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(0, 4, 0, 4));

    VBox view = new VBox(10, controls, scroll);
    VBox.setVgrow(scroll, Priority.ALWAYS);

    return view;
  }

  /**
   * Horizontal origin of a level column.
   */
  private static double xForLevel(int level) {
    return PAD + level * (FACT_W + H_GAP);
  }

  /**
   * Stacked height of a column holding count fact cards.
   */
  private static double columnHeight(int count) {
    return count <= 0 ? 0 : count * FACT_H + (count - 1) * V_GAP;
  }

  /**
   * Adds an edge unless the same directed edge already exists.
   */
  private static void addEdge(List<GraphEdge> edges, String fromId, String toId, EdgeKind kind) {
    GraphEdge edge = new GraphEdge(fromId, toId, kind);

    if (!edges.contains(edge))
      edges.add(edge);
  }

  /**
   * Fact card mirroring the result-card styling: attribute, value, and origin.
   */
  private Node buildFactCard(GraphNode node) {
    Fact fact = factsById.get(node.id());
    boolean initial = fact == null || fact.getDerivedBy() == null;
    String accent = initial ? TXT2 : PRIMARY;

    VBox body = new VBox(4);
    body.setPadding(new Insets(10, 12, 10, 12));

    Label attr = new Label(node.title());
    attr.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: " + TXT1 + ";");

    Label value = new Label(node.detail());
    value.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: " + accent + ";");

    Label origin = new Label(initial ? "Fait initial" : "↳ " + fact.getDerivedBy());
    origin.setStyle("-fx-font-size: 10; -fx-text-fill: " + (initial ? TXT3 : TXT2) + ";");

    body.getChildren().addAll(attr, value, origin);

    HBox card = new HBox();
    card.setPrefSize(FACT_W, FACT_H);
    card.setMaxSize(FACT_W, FACT_H);
    card.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER + ";"
        + "-fx-border-radius: 9; -fx-background-radius: 9; -fx-cursor: hand;");

    Region strip = new Region();
    strip.setPrefWidth(4);
    strip.setMinWidth(4);
    strip.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 9 0 0 9;");

    HBox.setHgrow(body, Priority.ALWAYS);
    card.getChildren().addAll(strip, body);

    if (fact != null)
      card.setUserData(fact.getFactId());

    card.setOnMouseClicked(event -> {
      event.consume();
      toggleSelection(node.id());
    });

    return card;
  }

  /**
   * Rule pill with a tooltip summarizing the minimal proof it fired on.
   */
  private Node buildRuleNode(GraphNode node) {
    Label label = new Label(node.title());
    label.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: white;");

    StackPane pill = new StackPane(label);
    pill.setPrefSize(RULE_W, RULE_H);
    pill.setMaxSize(RULE_W, RULE_H);
    pill.setStyle("-fx-background-color: " + RULE_BG + "; -fx-background-radius: 20; -fx-cursor: hand;");
    pill.setEffect(new DropShadow(6, 0, 2, Color.rgb(124, 58, 237, 0.35)));
    Tooltip.install(pill, new Tooltip(tooltipFor(node.id())));
    pill.setOnMouseClicked(event -> {
      event.consume();
      toggleSelection(node.id());
    });

    return pill;
  }

  /**
   * Dashed absence badge naming the condition that held by absence.
   */
  private Node buildBadge(GraphNode node) {
    Label badge = new Label("⊘ " + node.title());
    badge.setAlignment(Pos.CENTER);
    badge.setPrefSize(BADGE_W, BADGE_H);
    badge.setMaxSize(BADGE_W, BADGE_H);
    badge.setStyle("-fx-font-size: 11; -fx-text-fill: " + TXT2 + "; -fx-background-color: " + SURFACE + ";" + "-fx-border-color: " + TXT3
        + "; -fx-border-style: dashed; -fx-border-radius: 13; -fx-background-radius: 13;");
    badge.setMouseTransparent(true);

    return badge;
  }

  /**
   * Tooltip text listing the minimal proof behind a rule node.
   */
  private String tooltipFor(String ruleId) {
    List<Derivation> group = derivationsByRule.getOrDefault(ruleId, List.of());

    List<String> premises = group.stream().flatMap(derivation -> derivation.premiseIds().stream()).distinct()
        .map(id -> factsById.containsKey(id) ? factsById.get(id).getAttribute() : id).toList();
    List<String> absences = group.stream().flatMap(derivation -> derivation.absences().stream()).distinct().map(AbsenceWitness::label).toList();
    List<String> conclusions = group.stream().map(Derivation::conclusionId).distinct()
        .map(id -> factsById.containsKey(id) ? factsById.get(id).getAttribute() : id).toList();

    StringBuilder tooltip = new StringBuilder(ruleId);

    if (!premises.isEmpty() || !absences.isEmpty()) {
      List<String> inputs = new ArrayList<>(premises);
      inputs.addAll(absences);
      tooltip.append(" ← ").append(String.join(", ", inputs));
    }

    if (!conclusions.isEmpty())
      tooltip.append(" → ").append(String.join(", ", conclusions));

    return tooltip.toString();
  }

  /**
   * Selects a node or deselects when it is already selected.
   */
  private void toggleSelection(String id) {
    if (id.equals(selectedId))
      clearSelection();
    else
      select(id);
  }

  /**
   * Highlights the upstream proof chain and downstream consequences, dimming the rest.
   */
  private void select(String id) {
    selectedId = id;

    Set<String> keep = new HashSet<>();
    keep.add(id);
    keep.addAll(upstreamOf(id, model));
    keep.addAll(downstreamOf(id, model));

    for (Map.Entry<String, Node> entry : nodeViews.entrySet()) {
      Node view = entry.getValue();

      if (keep.contains(entry.getKey())) {
        view.setOpacity(1.0);

        if (entry.getKey().equals(id))
          view.setEffect(new DropShadow(12, Color.web(SUCCESS)));
        else if (view instanceof StackPane)
          view.setEffect(new DropShadow(6, 0, 2, Color.rgb(124, 58, 237, 0.35)));
        else
          view.setEffect(null);
      } else {
        view.setOpacity(0.22);
        view.setEffect(null);
      }
    }

    for (EdgeView edgeView : edgeViews)
      edgeView.setDimmed(!(keep.contains(edgeView.from()) && keep.contains(edgeView.to())));
  }

  /**
   * Restores full styling after deselection.
   */
  private void clearSelection() {
    selectedId = null;

    for (Node view : nodeViews.values()) {
      view.setOpacity(1.0);

      if (view instanceof StackPane)
        view.setEffect(new DropShadow(6, 0, 2, Color.rgb(124, 58, 237, 0.35)));
      else
        view.setEffect(null);
    }

    for (EdgeView edgeView : edgeViews)
      edgeView.setDimmed(false);
  }

  /**
   * Sets the zoom factor within bounds and refreshes the label.
   */
  private void setZoom(double value) {
    zoom.set(Math.min(2.5, Math.max(0.4, value)));

    if (zoomLabel != null)
      zoomLabel.setText(zoomText());
  }

  /**
   * Zoom label text, such as "100%".
   */
  private String zoomText() {
    return Math.round(zoom.get() * 100) + "%";
  }

  /**
   * Small control button for the zoom bar.
   */
  private static Button smallButton(String text) {
    Button button = new Button(text);
    button.setStyle("-fx-background-color: " + SURFACE + "; -fx-text-fill: " + TXT1 + "; -fx-border-color: " + BORDER + ";"
        + "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 4 12; -fx-cursor: hand;");

    return button;
  }

  /**
   * One rendered edge: orthogonal polyline plus arrowhead, dimmable for highlighting.
   */
  private static class EdgeView {
    private final Group group = new Group();
    private final Polyline line = new Polyline();
    private final Polygon head = new Polygon();
    private final String from;
    private final String to;

    /**
     * Renders a directed edge between two positioned nodes.
     */
    EdgeView(GraphEdge edge, GraphNode source, GraphNode target) {
      this.from = edge.fromId();
      this.to = edge.toId();

      List<RoutePoint> route = routeEdge(edge, source, target);

      for (RoutePoint point : route)
        line.getPoints().addAll(point.x(), point.y());

      line.setStrokeWidth(edge.kind() == EdgeKind.CONCLUSION ? 2.2 : 1.6);

      if (edge.kind() == EdgeKind.ABSENCE) {
        line.getStrokeDashArray().addAll(7.0, 5.0);
        line.setStroke(Color.web(TXT3));
      } else if (edge.kind() == EdgeKind.CONCLUSION) {
        line.setStroke(Color.web(PRIMARY));
      } else {
        line.setStroke(Color.web("#6B87E8"));
      }

      RoutePoint tip = route.get(route.size() - 1);
      RoutePoint before = route.get(route.size() - 2);
      double angle = Math.atan2(tip.y() - before.y(), tip.x() - before.x());
      double size = 7;

      head.getPoints().addAll(tip.x(), tip.y(), tip.x() - size * Math.cos(angle - 0.42), tip.y() - size * Math.sin(angle - 0.42),
          tip.x() - size * Math.cos(angle + 0.42), tip.y() - size * Math.sin(angle + 0.42));
      head.setFill(line.getStroke());

      group.getChildren().addAll(line, head);
      group.setMouseTransparent(true);
    }

    /**
     * Edge group for the canvas.
     */
    Group group() {
      return group;
    }

    /**
     * Source node id.
     */
    String from() {
      return from;
    }

    /**
     * Target node id.
     */
    String to() {
      return to;
    }

    /**
     * Dims or restores the edge during highlighting.
     */
    void setDimmed(boolean dimmed) {
      group.setOpacity(dimmed ? 0.1 : 1.0);
    }
  }
}
