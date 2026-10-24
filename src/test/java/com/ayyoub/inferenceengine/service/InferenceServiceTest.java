package com.ayyoub.inferenceengine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.engine.ConditionEvaluator;
import com.ayyoub.inferenceengine.engine.InferenceEngine;
import com.ayyoub.inferenceengine.engine.KnowledgeBase;
import com.ayyoub.inferenceengine.json.JsonParser;
import com.ayyoub.inferenceengine.json.JsonParserImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InferenceServiceTest {

  private InferenceService service;

  @BeforeEach
  void setUp() {
    JsonParser parser = new JsonParserImpl();
    KnowledgeBase knowledgeBase = new KnowledgeBase(parser.parseRules("/rules.json"));
    InferenceEngine engine = new InferenceEngine(knowledgeBase, new ConditionEvaluator());
    service = new InferenceServiceImpl(parser, "/facts.json", knowledgeBase, engine);
  }

  @Test
  void exposesTheFactCatalogue() {
    assertEquals(7, service.factCatalogue().size());
    assertEquals("A", service.factCatalogue().get(0).attribute());
  }

  @Test
  void valueOptionsIncludeTheCatalogueString() {
    assertEquals(List.of("alpha"), service.valueOptions("category"));
    assertTrue(service.valueOptions("A").isEmpty());
    assertTrue(service.valueOptions("unknown").isEmpty());
    assertTrue(service.valueOptions("category").stream().noneMatch(String::isBlank));
  }

  @Test
  void runAssignsSequentialIdsAndDerivesFacts() {
    InferenceResult result = service.run(List.of(new FactInput("A", true), new FactInput("B", true)));

    assertEquals("F1", result.facts().get(0).factId());
    assertEquals("F2", result.facts().get(1).factId());
    assertTrue(result.facts().stream().anyMatch(fact -> fact.attribute().equals("G") && "R1".equals(fact.derivedBy())));
    assertTrue(result.derivations().stream().anyMatch(derivation -> derivation.ruleId().equals("R1")));
  }

  @Test
  void eachRunStartsFromFreshMemory() {
    InferenceResult first = service.run(List.of(new FactInput("A", true), new FactInput("B", true)));
    InferenceResult second = service.run(List.of(new FactInput("A", true)));

    assertTrue(first.facts().stream().anyMatch(fact -> "G".equals(fact.attribute())));
    assertEquals("F1", second.facts().get(0).factId());
    assertEquals("F2", second.facts().get(1).factId());
    assertTrue(second.facts().stream().noneMatch(fact -> "G".equals(fact.attribute())));
  }

  @Test
  void emptyInputRunReturnsAnEmptyResult() {
    InferenceResult result = service.run(List.of());

    assertTrue(result.facts().isEmpty());
    assertTrue(result.derivations().isEmpty());
  }
}
