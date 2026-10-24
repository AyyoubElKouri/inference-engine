package com.ayyoub.inferenceengine.ui;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.service.FactInput;
import com.ayyoub.inferenceengine.service.InferenceResult;
import com.ayyoub.inferenceengine.service.InferenceService;
import com.ayyoub.inferenceengine.ui.component.Button;
import com.ayyoub.inferenceengine.ui.component.CataloguePanel;
import com.ayyoub.inferenceengine.ui.component.DesignTokens;
import com.ayyoub.inferenceengine.ui.component.FactRow;
import com.ayyoub.inferenceengine.ui.component.SelectedFactsPanel;
import com.ayyoub.inferenceengine.ui.component.StatsBar;
import com.ayyoub.inferenceengine.ui.component.TopBar;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Two-page UI: fact builder and reasoning trace.
 */
public final class EngineUI {

  private final InferenceService service;
  private final LinkedHashMap<String, Fact> selected = new LinkedHashMap<>();
  private final Map<String, Object> values = new LinkedHashMap<>();
  private final Map<String, FactRow> rows = new LinkedHashMap<>();

  private Stage stage;
  private CataloguePanel cataloguePanel;
  private SelectedFactsPanel selectedPanel;
  private Button runButton;
  private int catalogueSize;

  /**
   * Creates the UI with its use-case dependency.
   */
  @Inject
  public EngineUI(InferenceService service) {
    this.service = service;
  }

  /**
   * Configures the stage and shows the fact builder.
   */
  public void show(Stage stage) {
    this.stage = stage;
    this.catalogueSize = service.factCatalogue().size();
    stage.setTitle("Moteur d'Inférence");
    stage.setMinWidth(900);
    stage.setMinHeight(560);
    stage.setResizable(true);
    showInputPage();
    stage.show();
  }

  /**
   * Rebuilds the fact builder from scratch.
   */
  private void showInputPage() {
    selected.clear();
    values.clear();
    rows.clear();

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + DesignTokens.BG + ";");
    root.setTop(new TopBar(false, () -> {
    }));

    cataloguePanel = new CataloguePanel(service.factCatalogue(), this::addFact);
    cataloguePanel.setPrefWidth(300);
    cataloguePanel.setMinWidth(260);
    cataloguePanel.setMaxWidth(340);

    selectedPanel = new SelectedFactsPanel();
    HBox.setHgrow(selectedPanel, Priority.ALWAYS);

    HBox body = new HBox(0, cataloguePanel, selectedPanel);
    root.setCenter(body);
    root.setBottom(buildFooter());

    stage.setScene(new Scene(root, 960, 660));
  }

  /**
   * Adds one catalogue fact and its editor.
   */
  private void addFact(Fact template) {
    String attribute = template.attribute();

    if (selected.containsKey(attribute))
      return;

    selected.put(attribute, template);
    values.put(attribute, template.value());

    FactRow row = new FactRow(template, service.valueOptions(attribute), value -> values.put(attribute, value), () -> removeFact(attribute));

    rows.put(attribute, row);
    selectedPanel.addRow(row);
    selectedPanel.setCount(selected.size());
    cataloguePanel.markUsed(attribute, true);
    runButton.setDisable(false);
  }

  /**
   * Removes one selected fact and re-enables its catalogue entry.
   */
  private void removeFact(String attribute) {
    selected.remove(attribute);
    values.remove(attribute);

    FactRow row = rows.remove(attribute);

    if (row != null)
      selectedPanel.removeRow(row);

    selectedPanel.setCount(selected.size());
    cataloguePanel.markUsed(attribute, false);
    runButton.setDisable(selected.isEmpty());
  }

  /**
   * Footer with the catalogue count and the run button.
   */
  private HBox buildFooter() {
    HBox footer = new HBox();
    footer.setPadding(new Insets(13, 32, 13, 32));
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: " + DesignTokens.BORDER
        + " transparent transparent transparent;" + "-fx-border-width: 1 0 0 0;");

    HBox hint = new HBox(DesignTokens.label(catalogueSize + " attribut(s) disponibles dans facts.json", 12, DesignTokens.TXT3));
    HBox.setHgrow(hint, Priority.ALWAYS);

    runButton = new Button("Lancer le raisonnement  →", Button.Variant.PRIMARY);
    runButton.setDisable(true);
    runButton.setOnAction(event -> runAndShowResults());

    footer.getChildren().addAll(hint, runButton);
    return footer;
  }

  /**
   * Runs inference over the selected values and shows the trace page.
   */
  private void runAndShowResults() {
    List<FactInput> inputs = selected.keySet().stream().map(attribute -> new FactInput(attribute, values.get(attribute))).toList();

    showResultsPage(service.run(inputs));
  }

  /**
   * Renders the trace page for one result.
   */
  private void showResultsPage(InferenceResult result) {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + DesignTokens.BG + ";");
    root.setTop(new TopBar(true, this::showInputPage));

    VBox page = new VBox(12);
    page.setPadding(new Insets(20, 36, 24, 36));
    page.getChildren().add(new StatsBar(result.facts()));

    VBox graph = new TraceGraphView(result.facts(), result.derivations()).build();
    VBox.setVgrow(graph, Priority.ALWAYS);
    page.getChildren().add(graph);

    root.setCenter(page);
    stage.setScene(new Scene(root, 960, 660));
  }
}
