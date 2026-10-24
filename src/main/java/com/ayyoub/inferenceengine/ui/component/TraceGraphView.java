package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
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
  private static final double FACT_GAP = 24;
  private static final double ROW_GAP = 120;
  private static final double RULE_GAP = 48;
  private static final double BADGE_GAP = 12;
  private static final double PAD = 48;

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
      factsById.put(fact.factId(), fact);

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
   * Facts sit in rows by level along y, ordered top to bottom; each fired rule sits between its deepest premise row and its shallowest conclusion row
   * at the mean horizontal center of its connected facts; absence badges sit beside their rule node.
   */
  public static GraphModel layout(List<Fact> facts, List<Derivation> derivations) {
    Map<Integer, List<Fact>> byLevel = new TreeMap<>();

    for (Fact fact : facts)
      byLevel.computeIfAbsent(fact.level(), key -> new ArrayList<>()).add(fact);

    for (List<Fact> row : byLevel.values())
      row.sort((left, right) -> {
        int compared = left.attribute().compareTo(right.attribute());

        return compared != 0 ? compared : left.factId().compareTo(right.factId());
      });

    double maxRowWidth = byLevel.values().stream().mapToDouble(row -> rowWidth(row.size())).max().orElse(0);

    List<GraphNode> nodes = new ArrayList<>();
    Map<String, GraphNode> nodesById = new HashMap<>();

    for (Map.Entry<Integer, List<Fact>> entry : byLevel.entrySet()) {
      int level = entry.getKey();
      List<Fact> row = entry.getValue();
      double left = PAD + (maxRowWidth - rowWidth(row.size())) / 2;

      for (int index = 0; index < row.size(); index++) {
        Fact fact = row.get(index);
        GraphNode node = new GraphNode(fact.factId(), NodeKind.FACT, fact.attribute(), fact.operator() + " " + fact.value(), level,
            left + index * (FACT_W + FACT_GAP), yForLevel(level), FACT_W, FACT_H);

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
      int conclusionRow = Math.max(conclusionLevel, anchorLevel + 1);
      double premiseBottom = yForLevel(anchorLevel) + FACT_H;
      double conclusionTop = yForLevel(conclusionRow);

      List<GraphNode> connected = new ArrayList<>();
      premiseIds.stream().map(nodesById::get).filter(node -> node != null).forEach(connected::add);
      conclusionIds.stream().map(nodesById::get).filter(node -> node != null).forEach(connected::add);

      double ruleX = connected.isEmpty()
          ? PAD + maxRowWidth / 2 - RULE_W / 2
          : connected.stream().mapToDouble(GraphNode::centerX).average().orElse(PAD) - RULE_W / 2;
      double ruleY = premiseBottom + Math.max(BADGE_GAP, (conclusionTop - premiseBottom - RULE_H) / 2);

      GraphNode ruleNode = new GraphNode(ruleId, NodeKind.RULE, ruleId, "", premiseLevel, Math.max(PAD, ruleX), ruleY, RULE_W, RULE_H);

      ruleNodes.add(ruleNode);
    }

    // De-collide rules per row gap, reserving room for their absence badges.
    Map<Long, List<GraphNode>> rulesByGap = new TreeMap<>();

    for (GraphNode node : ruleNodes)
      rulesByGap.computeIfAbsent(Math.round(node.y()), key -> new ArrayList<>()).add(node);

    List<GraphNode> placedRules = new ArrayList<>();

    for (List<GraphNode> gapRules : rulesByGap.values()) {
      gapRules.sort((left, right) -> Double.compare(left.x(), right.x()));

      double cursor = PAD;

      for (GraphNode node : gapRules) {
        double placed = Math.max(node.x(), cursor);
        placedRules
            .add(new GraphNode(node.id(), node.kind(), node.title(), node.detail(), node.level(), placed, node.y(), node.width(), node.height()));
        cursor = placed + ruleBlockWidth(node.id(), grouped) + RULE_GAP;
      }
    }

    ruleNodes = placedRules;

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
        double badgeX = ruleNode.x() + RULE_W + BADGE_GAP + index * (BADGE_W + 8);
        double badgeY = ruleNode.y() + (RULE_H - BADGE_H) / 2;
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
   * Premise and conclusion edges leave the source vertically, travel horizontally in the free gap between level rows, and enter the target
   * vertically, so lines never cut through fact cards. Absence edges run straight from the badge to its rule node.
   */
  public static List<RoutePoint> routeEdge(GraphEdge edge, GraphNode source, GraphNode target) {
    if (edge.kind() == EdgeKind.ABSENCE)
      return routeAbsenceEdge(source, target);

    boolean downward = source.centerY() <= target.centerY();
    double startX = source.centerX();
    double startY = downward ? source.y() + source.height() : source.y();
    double endX = target.centerX();
    double endY = downward ? target.y() : target.y() + target.height();

    if (Math.abs(endX - startX) < 1)
      return List.of(new RoutePoint(startX, startY), new RoutePoint(endX, endY));

    double midY = startY + (endY - startY) / 2;

    return List.of(new RoutePoint(startX, startY), new RoutePoint(startX, midY), new RoutePoint(endX, midY), new RoutePoint(endX, endY));
  }

  /**
   * Straight horizontal or vertical route from an absence badge to its rule node.
   */
  private static List<RoutePoint> routeAbsenceEdge(GraphNode source, GraphNode target) {
    if (Math.abs(source.centerY() - target.centerY()) < 1) {
      boolean sourceIsRight = source.centerX() > target.centerX();
      double startX = sourceIsRight ? source.x() : source.x() + source.width();
      double endX = sourceIsRight ? target.x() + target.width() : target.x();

      return List.of(new RoutePoint(startX, source.centerY()), new RoutePoint(endX, target.centerY()));
    }

    boolean downward = source.centerY() < target.centerY();
    double startY = downward ? source.y() + source.height() : source.y();
    double endY = downward ? target.y() : target.y() + target.height();

    return List.of(new RoutePoint(source.centerX(), startY), new RoutePoint(target.centerX(), endY));
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
    zoomLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + DesignTokens.TXT2 + ";");

    Button zoomOut = new Button("−", Button.Variant.SECONDARY);
    zoomOut.setOnAction(event -> setZoom(zoom.get() / 1.2));

    Button zoomIn = new Button("+", Button.Variant.SECONDARY);
    zoomIn.setOnAction(event -> setZoom(zoom.get() * 1.2));

    Button reset = new Button("Réinitialiser", Button.Variant.SECONDARY);
    reset.setOnAction(event -> {
      setZoom(1.0);
      clearSelection();
    });

    Label hint = new Label("Cliquer un nœud pour tracer sa chaîne de preuve ; cliquer le fond pour tout réafficher.");
    hint.setStyle("-fx-font-size: 11; -fx-text-fill: " + DesignTokens.TXT3 + ";");

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
   * Vertical origin of a level row.
   */
  private static double yForLevel(int level) {
    return PAD + level * (FACT_H + ROW_GAP);
  }

  /**
   * Width of a row holding count fact cards.
   */
  private static double rowWidth(int count) {
    return count <= 0 ? 0 : count * FACT_W + (count - 1) * FACT_GAP;
  }

  /**
   * Horizontal room one fired rule needs, including its absence badges.
   */
  private static double ruleBlockWidth(String ruleId, Map<String, List<Derivation>> grouped) {
    long badges = grouped.get(ruleId).stream().flatMap(derivation -> derivation.absences().stream()).distinct().count();

    return RULE_W + (badges == 0 ? 0 : BADGE_GAP + badges * BADGE_W + (badges - 1) * 8);
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
    boolean initial = fact == null || fact.derivedBy() == null;
    String accent = initial ? DesignTokens.TXT2 : DesignTokens.PRIMARY;

    VBox body = new VBox(4);
    body.setPadding(new Insets(10, 12, 10, 12));

    Label attr = new Label(node.title());
    attr.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: " + DesignTokens.TXT1 + ";");

    Label value = new Label(node.detail());
    value.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: " + accent + ";");

    Label origin = new Label(initial ? "Fait initial" : "↳ " + fact.derivedBy());
    origin.setStyle("-fx-font-size: 10; -fx-text-fill: " + (initial ? DesignTokens.TXT3 : DesignTokens.TXT2) + ";");

    body.getChildren().addAll(attr, value, origin);

    HBox card = new HBox();
    card.setPrefSize(FACT_W, FACT_H);
    card.setMaxSize(FACT_W, FACT_H);
    card.setStyle("-fx-background-color: " + DesignTokens.SURFACE + "; -fx-border-color: " + DesignTokens.BORDER + ";"
        + "-fx-border-radius: 9; -fx-background-radius: 9; -fx-cursor: hand;");

    Region strip = new Region();
    strip.setPrefWidth(4);
    strip.setMinWidth(4);
    strip.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 9 0 0 9;");

    HBox.setHgrow(body, Priority.ALWAYS);
    card.getChildren().addAll(strip, body);

    if (fact != null)
      card.setUserData(fact.factId());

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
    pill.setStyle("-fx-background-color: " + DesignTokens.RULE + "; -fx-background-radius: 20; -fx-cursor: hand;");
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
    badge.setStyle("-fx-font-size: 11; -fx-text-fill: " + DesignTokens.TXT2 + "; -fx-background-color: " + DesignTokens.SURFACE + ";"
        + "-fx-border-color: " + DesignTokens.TXT3 + "; -fx-border-style: dashed; -fx-border-radius: 13; -fx-background-radius: 13;");
    badge.setMouseTransparent(true);

    return badge;
  }

  /**
   * Tooltip text listing the minimal proof behind a rule node.
   */
  private String tooltipFor(String ruleId) {
    List<Derivation> group = derivationsByRule.getOrDefault(ruleId, List.of());

    List<String> premises = group.stream().flatMap(derivation -> derivation.premiseIds().stream()).distinct()
        .map(id -> factsById.containsKey(id) ? factsById.get(id).attribute() : id).toList();
    List<String> absences = group.stream().flatMap(derivation -> derivation.absences().stream()).distinct().map(AbsenceWitness::label).toList();
    List<String> conclusions = group.stream().map(Derivation::conclusionId).distinct()
        .map(id -> factsById.containsKey(id) ? factsById.get(id).attribute() : id).toList();

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
          view.setEffect(new DropShadow(12, Color.web(DesignTokens.SUCCESS)));
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
        line.setStroke(Color.web(DesignTokens.TXT3));
      } else if (edge.kind() == EdgeKind.CONCLUSION) {
        line.setStroke(Color.web(DesignTokens.PRIMARY));
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
