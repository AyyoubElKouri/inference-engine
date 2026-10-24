package com.ayyoub.inferenceengine.ui.component;

import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

/**
 * Dropdown over known string values.
 */
public final class ChoiceInput implements Input {

  private final ComboBox<String> comboBox;

  /**
   * Builds the dropdown.
   */
  public ChoiceInput(String initial, List<String> options, Consumer<Object> onChange) {
    comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
    comboBox.setValue(initial);
    comboBox.setMaxWidth(Double.MAX_VALUE);
    comboBox.setStyle(Styles.combo());
    comboBox.valueProperty().addListener((observable, previous, current) -> {
      if (current != null)
        onChange.accept(current);
    });
  }

  @Override
  public Node node() {
    return comboBox;
  }

  @Override
  public Object value() {
    return comboBox.getValue();
  }
}
