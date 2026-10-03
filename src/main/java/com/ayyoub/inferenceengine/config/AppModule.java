package com.ayyoub.inferenceengine.config;

import com.google.inject.AbstractModule;

public class AppModule extends AbstractModule {

  @Override
  protected void configure() {
    // Bindings here, for example :
    // bind(InferenceEngine.class).to(ForwardChainingEngine.class).in(Scopes.SINGLETON);
    // bind(KnowledgeBaseLoader.class).in(Scopes.SINGLETON);
    // bind(ExplanationModule.class).to(ExplanationModuleImpl.class).in(Scopes.SINGLETON);
  }
}
