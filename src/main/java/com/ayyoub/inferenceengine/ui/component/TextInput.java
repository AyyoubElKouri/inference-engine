package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/**
 * Free-text field.
 */
public final class TextInput implements Input {

  private final TextField field;

  /**
   * Builds the field.
   */
  public TextInput(String initial, Consumer<Object> onChange) {
    field = new TextField(initial != null ? initial : "");
    field.setStyle(Styles.input());
    field.textProperty().addListener((observable, previous, current) -> onChange.accept(current));
  }

  @Override
  public Node node() {
    return field;
  }

  @Override
  public Object value() {
    return field.getText();
  }
}
