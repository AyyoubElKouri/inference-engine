package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Spinner;

/**
 * Editable integer spinner.
 */
public final class IntegerInput implements Input {

  private final Spinner<Integer> spinner;

  /**
   * Builds the spinner.
   */
  public IntegerInput(int initial, Consumer<Object> onChange) {
    spinner = new Spinner<>(-99999, 99999, initial, 1);
    spinner.setEditable(true);
    spinner.setMaxWidth(Double.MAX_VALUE);
    spinner.setStyle(Styles.input());
    spinner.valueProperty().addListener((observable, previous, current) -> {
      if (current != null)
        onChange.accept(current);
    });
  }

  @Override
  public Node node() {
    return spinner;
  }

  @Override
  public Object value() {
    return spinner.getValue();
  }
}
