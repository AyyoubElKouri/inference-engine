package com.ayyoub.inferenceengine;

import com.ayyoub.inferenceengine.config.AppModule;
import com.ayyoub.inferenceengine.json.JsonParseException;
import com.ayyoub.inferenceengine.ui.EngineUI;
import com.ayyoub.inferenceengine.ui.component.Dialogs;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Application entry point and composition-root trigger.
 */
public class Launcher extends Application {

  @Override
  public void start(Stage stage) {
    try {
      Injector injector = Guice.createInjector(new AppModule());
      injector.getInstance(EngineUI.class).show(stage);
    } catch (JsonParseException | ConfigurationException | ProvisionException error) {
      Dialogs.showError(null, "Chargement impossible", error.getMessage());
      Platform.exit();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
