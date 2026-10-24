package com.ayyoub.inferenceengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ayyoub.inferenceengine.service.InferenceService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

class AppModuleTest {

  @Test
  void injectorProvidesAFullyLoadedService() {
    Injector injector = Guice.createInjector(new AppModule());
    InferenceService service = injector.getInstance(InferenceService.class);

    assertEquals(7, service.factCatalogue().size());
  }
}
