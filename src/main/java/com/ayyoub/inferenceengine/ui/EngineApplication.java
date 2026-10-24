package com.ayyoub.inferenceengine.ui;

import com.ayyoub.inferenceengine.config.AppModule;
import com.ayyoub.inferenceengine.json.JsonParseException;
import com.ayyoub.inferenceengine.ui.component.Dialogs;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * JavaFX application that boots the composition root and shows the engine UI.
 */
public final class EngineApplication extends Application {

  @Override
  public void start(Stage stage) {
    try {
      Injector injector = Guice.createInjector(new AppModule());
      injector.getInstance(EngineUI.class).show(stage);
    } catch (JsonParseException | ConfigurationException | CreationException | ProvisionException error) {
      Dialogs.showError(null, "Chargement impossible", error.getMessage());
      Platform.exit();
    }
  }
}
