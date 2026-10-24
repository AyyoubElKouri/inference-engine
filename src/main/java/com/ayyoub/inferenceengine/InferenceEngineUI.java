package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.config.AppModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.*;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Two-page inference engine UI.
 *
 * <p>
 * Page 1 — Fact Builder : left panel = catalogue of available attributes (from facts.json), right panel = facts the user has added + their typed
 * inputs. Nothing is pre-added; the user builds their set.
 *
 * <p>
 * Page 2 — Trace View : full forward-chaining trace grouped by level, with a stats bar and a back button.
 */
public class InferenceEngineUI extends Application {

  // ── Design Tokens ─────────────────────────────────────────────────────────

  private static final String BG = "#F7F8FA";
  private static final String SURFACE = "#FFFFFF";
  private static final String PRIMARY = "#1B4FD8";
  private static final String PRIMARY_ALT = "#1640C0";
  private static final String PRIMARY_TL = "#EEF2FF";
  private static final String BORDER = "#E5E7EB";
  private static final String BORDER_DARK = "#D1D5DB";
  private static final String TXT1 = "#0D0D14";
  private static final String TXT2 = "#6B7280";
  private static final String TXT3 = "#9CA3AF";
  private static final String SUCCESS = "#059669";
  private static final String WARN = "#D97706";
  private static final String DANGER_TL = "#FEF2F2";
  private static final String DANGER = "#DC2626";

  // ── State ─────────────────────────────────────────────────────────────────

  private Stage stage;
  private KnowledgeBase knowledgeBase;

  private static Injector injector;

  /**
   * Full catalogue loaded from facts.json (templates with types).
   */
  private List<Fact> catalogue;

  /**
   * Facts the user has chosen to add, keyed by attribute name. Insertion order is preserved for display.
   */
  private final LinkedHashMap<String, Fact> addedFacts = new LinkedHashMap<>();

  /**
   * Current typed value for each added attribute.
   */
  private final Map<String, Object> userInputs = new LinkedHashMap<>();

  /**
   * The VBox that holds the list of added-fact rows (right panel).
   */
  private VBox addedList;

  /**
   * Label showing count / empty-state in right panel.
   */
  private Label rightHint;

  /**
   * Run button — enabled only when at least one fact is added.
   */
  private Button runBtn;

  // ── Application Entry ─────────────────────────────────────────────────────

  @Override
  public void init() {
    injector = Guice.createInjector(new AppModule());
  }

  @Override
  public void start(Stage stage) {
    this.stage = stage;
    this.knowledgeBase = new KnowledgeBase();

    try {
      this.catalogue = FactsReader.readFacts("src/main/resources/facts.json");
    } catch (Exception e) {
      this.catalogue = new ArrayList<>();
      System.err.println("[UI] Could not load facts.json: " + e.getMessage());
    }

    stage.setTitle("Moteur d'Inférence");
    stage.setMinWidth(900);
    stage.setMinHeight(560);
    stage.setResizable(true);

    showInputPage();
    stage.show();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PAGE 1 — Fact Builder
  // ═══════════════════════════════════════════════════════════════════════════

  private void showInputPage() {
    addedFacts.clear();
    userInputs.clear();

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + BG + ";");
    root.setTop(buildTopBar(false));

    // Two-panel split
    HBox body = new HBox(0);

    // ── Left panel: catalogue ─────────────────────────────────────────────
    VBox leftPanel = buildCataloguePanel();
    leftPanel.setPrefWidth(300);
    leftPanel.setMinWidth(260);
    leftPanel.setMaxWidth(340);
    leftPanel.setStyle("-fx-background-color: " + SURFACE + ";" + "-fx-border-color: transparent " + BORDER + " transparent transparent;"
        + "-fx-border-width: 0 1 0 0;");

    // ── Right panel: added facts ──────────────────────────────────────────
    VBox rightPanel = buildAddedPanel();
    HBox.setHgrow(rightPanel, Priority.ALWAYS);

    body.getChildren().addAll(leftPanel, rightPanel);
    root.setCenter(body);
    root.setBottom(buildInputFooter());

    stage.setScene(new Scene(root, 960, 660));
  }

  // ── Left: catalogue ───────────────────────────────────────────────────────

  private VBox buildCataloguePanel() {
    VBox panel = new VBox(0);

    // Panel header
    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 20, 16, 20));
    header.setStyle("-fx-border-color: transparent transparent " + BORDER + " transparent;" + "-fx-border-width: 0 0 1 0;");
    Label title = new Label("Catalogue");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(Color.web(TXT1));
    Label sub = new Label("Cliquez pour ajouter un fait");
    sub.setFont(Font.font("System", 11));
    sub.setTextFill(Color.web(TXT3));
    header.getChildren().addAll(title, sub);

    // Scrollable list of catalogue items
    VBox list = new VBox(4);
    list.setPadding(new Insets(12, 10, 12, 10));

    if (catalogue.isEmpty()) {
      Label empty = new Label("Aucun attribut dans facts.json");
      empty.setFont(Font.font("System", 12));
      empty.setTextFill(Color.web(TXT3));
      empty.setPadding(new Insets(8, 10, 8, 10));
      list.getChildren().add(empty);
    } else {
      for (Fact tmpl : catalogue) {
        list.getChildren().add(buildCatalogueRow(tmpl));
      }
    }

    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setBorder(Border.EMPTY);
    scroll.setStyle("-fx-background: " + SURFACE + "; -fx-background-color: " + SURFACE + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    panel.getChildren().addAll(header, scroll);
    return panel;
  }

  private Node buildCatalogueRow(Fact tmpl) {
    HBox row = new HBox(10);
    row.setPadding(new Insets(9, 12, 9, 12));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setStyle("-fx-background-color: transparent;" + "-fx-background-radius: 8;" + "-fx-cursor: hand;");

    Label attrLbl = new Label(tmpl.getAttribute());
    attrLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
    attrLbl.setTextFill(Color.web(TXT1));
    HBox.setHgrow(attrLbl, Priority.ALWAYS);

    Label badge = typeBadge(tmpl.getValue());

    Label addIcon = new Label("+");
    addIcon.setFont(Font.font("System", FontWeight.BOLD, 14));
    addIcon.setTextFill(Color.web(PRIMARY));
    addIcon.setMinWidth(20);
    addIcon.setAlignment(Pos.CENTER);
    addIcon.setVisible(false);

    row.getChildren().addAll(attrLbl, badge, addIcon);

    row.setOnMouseEntered(e -> {
      row.setStyle("-fx-background-color: " + PRIMARY_TL + ";" + "-fx-background-radius: 8; -fx-cursor: hand;");
      addIcon.setVisible(true);
    });
    row.setOnMouseExited(e -> {
      row.setStyle("-fx-background-color: transparent;" + "-fx-background-radius: 8; -fx-cursor: hand;");
      addIcon.setVisible(false);
    });

    row.setOnMouseClicked(e -> addFactFromCatalogue(tmpl, row));
    return row;
  }

  // ── Right: added facts ────────────────────────────────────────────────────

  private VBox buildAddedPanel() {
    VBox panel = new VBox(0);
    panel.setStyle("-fx-background-color: " + BG + ";");

    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 24, 16, 24));
    header.setStyle(
        "-fx-background-color: " + BG + ";" + "-fx-border-color: transparent transparent " + BORDER + " transparent;" + "-fx-border-width: 0 0 1 0;");
    Label title = new Label("Faits sélectionnés");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(Color.web(TXT1));
    rightHint = new Label("Aucun fait ajouté — sélectionnez dans le catalogue");
    rightHint.setFont(Font.font("System", 11));
    rightHint.setTextFill(Color.web(TXT3));
    header.getChildren().addAll(title, rightHint);

    addedList = new VBox(8);
    addedList.setPadding(new Insets(16, 24, 16, 24));

    ScrollPane scroll = new ScrollPane(addedList);
    scroll.setFitToWidth(true);
    scroll.setBorder(Border.EMPTY);
    scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    panel.getChildren().addAll(header, scroll);
    return panel;
  }

  // ── Logic: add / remove a fact ────────────────────────────────────────────

  private void addFactFromCatalogue(Fact tmpl, Node catalogueRow) {
    String attr = tmpl.getAttribute();

    if (addedFacts.containsKey(attr)) {
      // Flash the existing row to signal "already added"
      for (Node n : addedList.getChildren()) {
        if (attr.equals(n.getUserData())) {
          String orig = n.getStyle();
          n.setStyle(orig.replace(SURFACE, "#FEF9C3"));
          new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
              javafx.application.Platform.runLater(() -> n.setStyle(orig));
            }
          }, 600);
        }
      }
      return;
    }

    addedFacts.put(attr, tmpl);
    userInputs.put(attr, tmpl.getValue());

    // Dim catalogue row → shows it's already in use
    catalogueRow.setStyle("-fx-background-color: transparent; -fx-background-radius: 8;" + "-fx-opacity: 0.4; -fx-cursor: default;");
    catalogueRow.setMouseTransparent(true);

    addedList.getChildren().add(buildAddedRow(tmpl, catalogueRow));
    refreshRightHint();
  }

  private void removeAddedFact(String attr, Node addedRow, Node catalogueRow) {
    addedFacts.remove(attr);
    userInputs.remove(attr);
    addedList.getChildren().remove(addedRow);

    // Re-enable the catalogue row
    if (catalogueRow != null) {
      catalogueRow.setStyle("-fx-background-color: transparent; -fx-background-radius: 8;" + "-fx-opacity: 1.0; -fx-cursor: hand;");
      catalogueRow.setMouseTransparent(false);
    }
    refreshRightHint();
  }

  private void refreshRightHint() {
    int n = addedFacts.size();
    rightHint.setText(
        n == 0 ? "Aucun fait ajouté — sélectionnez dans le catalogue" : n + " fait" + (n > 1 ? "s" : "") + " sélectionné" + (n > 1 ? "s" : ""));
    if (runBtn != null)
      runBtn.setDisable(n == 0);
  }

  // ── Added-fact row ────────────────────────────────────────────────────────

  private Node buildAddedRow(Fact tmpl, Node catalogueRow) {
    HBox card = new HBox();
    card.setUserData(tmpl.getAttribute());
    card.setStyle(
        "-fx-background-color: " + SURFACE + ";" + "-fx-border-color: " + BORDER + ";" + "-fx-border-radius: 10; -fx-background-radius: 10;");

    Region strip = new Region();
    strip.setPrefWidth(4);
    strip.setMinWidth(4);
    strip.setStyle("-fx-background-color: " + PRIMARY + "; -fx-background-radius: 10 0 0 10;");

    VBox body = new VBox(10);
    body.setPadding(new Insets(14, 14, 14, 12));
    HBox.setHgrow(body, Priority.ALWAYS);

    // Header: attr + badge + remove button
    HBox rowHeader = new HBox(8);
    rowHeader.setAlignment(Pos.CENTER_LEFT);
    Label attrLbl = new Label(tmpl.getAttribute());
    attrLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
    attrLbl.setTextFill(Color.web(TXT1));
    Label badge = typeBadge(tmpl.getValue());
    Region sp = new Region();
    HBox.setHgrow(sp, Priority.ALWAYS);

    Button removeBtn = new Button("✕");
    removeBtn.setFont(Font.font("System", FontWeight.BOLD, 11));
    String rmBase = "-fx-background-color: transparent; -fx-text-fill: " + TXT3 + ";"
        + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
    String rmHover = "-fx-background-color: " + DANGER_TL + "; -fx-text-fill: " + DANGER + ";"
        + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
    removeBtn.setStyle(rmBase);
    removeBtn.setOnMouseEntered(e -> removeBtn.setStyle(rmHover));
    removeBtn.setOnMouseExited(e -> removeBtn.setStyle(rmBase));
    removeBtn.setOnAction(e -> removeAddedFact(tmpl.getAttribute(), card, catalogueRow));

    rowHeader.getChildren().addAll(attrLbl, badge, sp, removeBtn);

    body.getChildren().addAll(rowHeader, buildInput(tmpl));
    card.getChildren().addAll(strip, body);
    return card;
  }

  // ── Input widgets ─────────────────────────────────────────────────────────

  private Node buildInput(Fact fact) {
    Object value = fact.getValue();
    String attr = fact.getAttribute();
    if (value instanceof Boolean)
      return buildBooleanToggle(attr, (Boolean) value);
    if (value instanceof Integer)
      return buildIntegerSpinner(attr, (Integer) value);
    if (value instanceof Double)
      return buildDoubleField(attr, (Double) value);
    return buildTextField(attr, value != null ? value.toString() : "");
  }

  // ── Switch booléen iOS-style ──────────────────────────────────────────────

  private Node buildBooleanToggle(String attr, boolean initial) {
    userInputs.put(attr, initial);

    final double trackW = 48, trackH = 26, knobD = 20, padding = 3;

    // Track (pill arrondie)
    Rectangle track = new Rectangle(trackW, trackH);
    track.setArcWidth(trackH);
    track.setArcHeight(trackH);

    // Knob (cercle blanc avec ombre)
    Circle knob = new Circle(knobD / 2);
    knob.setFill(Color.WHITE);
    knob.setEffect(new DropShadow(4, 0, 1, Color.rgb(0, 0, 0, 0.22)));

    // Conteneur du switch
    StackPane switchPane = new StackPane(track, knob);
    switchPane.setPrefSize(trackW, trackH);
    switchPane.setMaxSize(trackW, trackH);
    switchPane.setStyle("-fx-cursor: hand;");

    // Position initiale sans animation
    boolean[] state = {initial};
    applySwitchState(track, knob, trackW, knobD, padding, state[0], false);

    // Clic → bascule avec animation
    switchPane.setOnMouseClicked(e -> {
      state[0] = !state[0];
      userInputs.put(attr, state[0]);
      applySwitchState(track, knob, trackW, knobD, padding, state[0], true);
    });

    HBox row = new HBox(10, label("Non", 12, TXT2), switchPane, label("Oui", 12, TXT2));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(4, 0, 4, 0));
    return row;
  }

  /**
   * Applique couleur + position du knob. animate=true → slide avec TranslateTransition, false → instantané.
   */
  private void applySwitchState(Rectangle track, Circle knob, double trackW, double knobD, double padding, boolean on, boolean animate) {
    double targetX = on ? (trackW / 2) - (knobD / 2) - padding : -(trackW / 2) + (knobD / 2) + padding;

    track.setFill(on ? Color.web(PRIMARY) : Color.web(BORDER_DARK));

    if (animate) {
      TranslateTransition tt = new TranslateTransition(Duration.millis(160), knob);
      tt.setToX(targetX);
      tt.play();
    } else {
      knob.setTranslateX(targetX);
    }
  }

  // ── Spinner entier ────────────────────────────────────────────────────────

  private Node buildIntegerSpinner(String attr, int initial) {
    userInputs.put(attr, initial);
    Spinner<Integer> sp = new Spinner<>(-99999, 99999, initial, 1);
    sp.setEditable(true);
    sp.setMaxWidth(Double.MAX_VALUE);
    sp.setStyle(inputStyle());
    sp.valueProperty().addListener((obs, o, n) -> {
      if (n != null)
        userInputs.put(attr, n);
    });
    return sp;
  }

  // ── Champ décimal ─────────────────────────────────────────────────────────

  private Node buildDoubleField(String attr, double initial) {
    userInputs.put(attr, initial);
    TextField tf = new TextField(String.valueOf(initial));
    tf.setStyle(inputStyle());
    tf.textProperty().addListener((obs, o, n) -> {
      try {
        userInputs.put(attr, Double.parseDouble(n));
      } catch (NumberFormatException ignored) {
      }
    });
    return tf;
  }

  // ── Champ texte → ComboBox si plusieurs valeurs connues ──────────────────

  private Node buildTextField(String attr, String initial) {
    userInputs.put(attr, initial);

    // Collecter toutes les valeurs possibles pour cet attribut :
    // a) la valeur initiale du catalogue
    // b) les conclusions des règles qui touchent cet attribut
    Set<String> options = new LinkedHashSet<>();
    if (initial != null && !initial.isBlank())
      options.add(initial);

    for (Rule rule : knowledgeBase.getRules()) {
      for (Map<String, Object> conc : rule.getConclusions()) {
        if (attr.equals(conc.get("attribute"))) {
          Object v = conc.get("value");
          if (v instanceof String s && !s.isBlank())
            options.add(s);
        }
      }
    }

    if (options.size() > 1) {
      // Plusieurs valeurs possibles → ComboBox
      ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(new ArrayList<>(options)));
      cb.setValue(initial);
      cb.setMaxWidth(Double.MAX_VALUE);
      cb.setStyle(comboStyle());
      cb.valueProperty().addListener((obs, o, n) -> {
        if (n != null)
          userInputs.put(attr, n);
      });
      return cb;
    }

    // Valeur unique ou inconnue → TextField simple
    TextField tf = new TextField(initial != null ? initial : "");
    tf.setStyle(inputStyle());
    tf.textProperty().addListener((obs, o, n) -> userInputs.put(attr, n));
    return tf;
  }

  // ── Styles des champs ─────────────────────────────────────────────────────

  private String inputStyle() {
    return "-fx-background-color: " + BG + ";" + "-fx-border-color: " + BORDER_DARK + ";" + "-fx-border-radius: 7; -fx-background-radius: 7;"
        + "-fx-padding: 7 11; -fx-font-size: 13;";
  }

  private String comboStyle() {
    return "-fx-background-color: " + BG + ";" + "-fx-border-color: " + BORDER_DARK + ";" + "-fx-border-radius: 7; -fx-background-radius: 7;"
        + "-fx-padding: 3 6; -fx-font-size: 13;" + "-fx-cursor: hand;";
  }

  // ── Footer ────────────────────────────────────────────────────────────────

  private Node buildInputFooter() {
    HBox footer = new HBox();
    footer.setPadding(new Insets(13, 32, 13, 32));
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setStyle("-fx-background-color: " + SURFACE + ";" + "-fx-border-color: " + BORDER + " transparent transparent transparent;"
        + "-fx-border-width: 1 0 0 0;");
    Label hint = label(catalogue.size() + " attribut(s) disponibles dans facts.json", 12, TXT3);
    HBox.setHgrow(hint, Priority.ALWAYS);
    runBtn = primaryButton("Lancer le raisonnement  →");
    runBtn.setDisable(true);
    runBtn.setOnAction(e -> runAndNavigate());
    footer.getChildren().addAll(hint, runBtn);
    return footer;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Inference run
  // ═══════════════════════════════════════════════════════════════════════════

  private void runAndNavigate() {
    WorkingMemory wm = new WorkingMemory();
    int idx = 1;
    for (Map.Entry<String, Fact> entry : addedFacts.entrySet()) {
      String attr = entry.getKey();
      Object val = userInputs.getOrDefault(attr, entry.getValue().getValue());
      wm.addFact(new Fact("F" + idx++, attr, "=", val, entry.getValue().getConfidence(), null, 0));
    }
    new InferenceEngine(wm, knowledgeBase).forwardChaining();
    showResultsPage(wm);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PAGE 2 — Reasoning Trace
  // ═══════════════════════════════════════════════════════════════════════════

  private void showResultsPage(WorkingMemory workingMemory) {
    List<Fact> allFacts = workingMemory.getFacts();

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + BG + ";");
    root.setTop(buildTopBar(true));

    VBox page = new VBox(20);
    page.setPadding(new Insets(28, 36, 32, 36));
    page.getChildren().add(buildStatsBar(allFacts));

    // Bipartite reasoning graph: fact nodes, rule nodes, and
    // premise → rule → conclusion edges with absence badges.
    Node graphView = new TraceGraphView(allFacts, workingMemory.getDerivations()).build();
    VBox.setVgrow(graphView, Priority.ALWAYS);
    page.getChildren().add(graphView);

    root.setCenter(page);
    stage.setScene(new Scene(root, 960, 660));
  }

  // ── Stats bar ─────────────────────────────────────────────────────────────

  private Node buildStatsBar(List<Fact> allFacts) {
    long derived = allFacts.stream().filter(f -> f.getDerivedBy() != null).count();
    long initial = allFacts.size() - derived;
    long levels = allFacts.stream().mapToInt(Fact::getLevel).distinct().count();
    boolean alert = allFacts.stream().anyMatch(f -> f.getAttribute().startsWith("alerte") || f.getAttribute().equals("conseil"));

    HBox bar = new HBox(0);
    bar.setStyle("-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER + ";" + "-fx-border-radius: 12; -fx-background-radius: 12;");
    bar.setAlignment(Pos.CENTER);
    bar.getChildren().addAll(statCell(String.valueOf(allFacts.size()), "Faits totaux", PRIMARY), statDivider(),
        statCell(String.valueOf(initial), "Initiaux", TXT2), statDivider(), statCell(String.valueOf(derived), "Déduits", SUCCESS), statDivider(),
        statCell(String.valueOf(levels), "Niveaux", "#7C3AED"), statDivider(), statCell(alert ? "Oui" : "Non", "Alerte", alert ? WARN : TXT3));
    return bar;
  }

  private Node statCell(String value, String label, String color) {
    VBox cell = new VBox(4);
    cell.setAlignment(Pos.CENTER);
    cell.setPadding(new Insets(18, 32, 18, 32));
    HBox.setHgrow(cell, Priority.ALWAYS);
    Label v = new Label(value);
    v.setFont(Font.font("System", FontWeight.BOLD, 22));
    v.setTextFill(Color.web(color));
    Label l = new Label(label);
    l.setFont(Font.font("System", 11));
    l.setTextFill(Color.web(TXT3));
    cell.getChildren().addAll(v, l);
    return cell;
  }

  private Node statDivider() {
    Region r = new Region();
    r.setPrefWidth(1);
    r.setMinHeight(50);
    r.setStyle("-fx-background-color: " + BORDER + ";");
    return r;
  }

  // ── Shared helpers ────────────────────────────────────────────────────────

  private Node buildTopBar(boolean resultsMode) {
    HBox bar = new HBox(14);
    bar.setPadding(new Insets(17, 40, 17, 40));
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setStyle("-fx-background-color: " + SURFACE + ";" + "-fx-border-color: transparent transparent " + BORDER + " transparent;"
        + "-fx-border-width: 0 0 1 0;");

    if (resultsMode) {
      Button back = flatButton("← Retour");
      back.setOnAction(e -> showInputPage());
      bar.getChildren().add(back);
      bar.getChildren().add(spacer(16));
    }

    Circle dot = new Circle(5, Color.web(PRIMARY));
    Label appName = new Label("Moteur d'Inférence");
    appName.setFont(Font.font("System", FontWeight.BOLD, 15));
    appName.setTextFill(Color.web(TXT1));
    Label crumb = new Label(resultsMode ? "/ Trace de raisonnement" : "/ Configuration des faits");
    crumb.setFont(Font.font("System", 14));
    crumb.setTextFill(Color.web(TXT3));

    bar.getChildren().addAll(dot, appName, crumb);
    return bar;
  }

  private Label label(String text, int size, String color) {
    Label l = new Label(text);
    l.setFont(Font.font("System", size));
    l.setTextFill(Color.web(color));
    return l;
  }

  private Region spacer(double width) {
    Region r = new Region();
    r.setPrefWidth(width);
    return r;
  }

  private Label typeBadge(Object value) {
    String text;
    String fg, bg;
    if (value instanceof Boolean) {
      text = "booléen";
      fg = "#7C3AED";
      bg = "#F5F3FF";
    } else if (value instanceof Integer) {
      text = "entier";
      fg = "#0891B2";
      bg = "#ECFEFF";
    } else if (value instanceof Double) {
      text = "décimal";
      fg = "#0891B2";
      bg = "#ECFEFF";
    } else {
      text = "texte";
      fg = TXT2;
      bg = BG;
    }
    Label lbl = new Label(text);
    lbl.setFont(Font.font("System", 10));
    lbl.setTextFill(Color.web(fg));
    lbl.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 20; -fx-padding: 2 9;");
    return lbl;
  }

  private Button primaryButton(String text) {
    Button btn = new Button(text);
    btn.setFont(Font.font("System", FontWeight.BOLD, 13));
    String base = "-fx-background-color: " + PRIMARY + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 9 22; -fx-cursor: hand;";
    String hover = "-fx-background-color: " + PRIMARY_ALT + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 9 22; -fx-cursor: hand;";
    btn.setStyle(base);
    btn.setOnMouseEntered(e -> {
      if (!btn.isDisabled())
        btn.setStyle(hover);
    });
    btn.setOnMouseExited(e -> {
      if (!btn.isDisabled())
        btn.setStyle(base);
    });
    return btn;
  }

  private Button flatButton(String text) {
    Button btn = new Button(text);
    btn.setFont(Font.font("System", 12));
    btn.setStyle("-fx-background-color: " + BG + "; -fx-text-fill: " + TXT1 + ";" + "-fx-border-color: " + BORDER
        + "; -fx-border-radius: 7; -fx-background-radius: 7;" + "-fx-padding: 6 14; -fx-cursor: hand;");
    return btn;
  }
}
