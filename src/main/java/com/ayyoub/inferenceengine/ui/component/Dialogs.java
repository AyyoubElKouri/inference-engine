package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Shared modal dialogs.
 */
public final class Dialogs {

  /**
   * Shows a blocking error alert.
   */
  public static void showError(Window owner, String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.initOwner(owner);
    alert.setTitle(title);
    alert.setHeaderText(title);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private Dialogs() {
  }
}
