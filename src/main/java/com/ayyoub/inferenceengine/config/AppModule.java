package com.ayyoub.inferenceengine.config;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator;
import com.ayyoub.inferenceengine.engine.InferenceEngine;
import com.ayyoub.inferenceengine.engine.KnowledgeBase;
import com.ayyoub.inferenceengine.json.JsonParser;
import com.ayyoub.inferenceengine.json.JsonParserImpl;
import com.ayyoub.inferenceengine.service.InferenceService;
import com.ayyoub.inferenceengine.service.InferenceServiceImpl;
import com.ayyoub.inferenceengine.ui.EngineUI;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/**
 * Sole composition root for the application.
 */
public class AppModule extends AbstractModule {

  @Override
  protected void configure() {
    bindConstant().annotatedWith(Names.named("rulesResource")).to("/rules.json");
    bindConstant().annotatedWith(Names.named("factsResource")).to("/facts.json");
    bind(JsonParser.class).to(JsonParserImpl.class).in(Scopes.SINGLETON);
    bind(ConditionEvaluator.class).in(Scopes.SINGLETON);
    bind(InferenceService.class).to(InferenceServiceImpl.class).in(Scopes.SINGLETON);
    bind(EngineUI.class).in(Scopes.SINGLETON);
  }

  /**
   * Loads the priority-ordered rules once.
   */
  @Provides
  @Singleton
  KnowledgeBase knowledgeBase(JsonParser parser, @Named("rulesResource") String rulesResource) {
    return new KnowledgeBase(parser.parseRules(rulesResource));
  }

  /**
   * Engine is stateless; one instance serves every run.
   */
  @Provides
  @Singleton
  InferenceEngine inferenceEngine(KnowledgeBase knowledgeBase, ConditionEvaluator evaluator) {
    return new InferenceEngine(knowledgeBase, evaluator);
  }
}
