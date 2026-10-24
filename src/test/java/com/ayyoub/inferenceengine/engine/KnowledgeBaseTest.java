package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ayyoub.inferenceengine.domain.Rule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeBaseTest {

  private static Rule rule(String id, int priority) {
    return new Rule(id, id, Map.of(), List.of(), priority, 1.0, "");
  }

  @Test
  void sortsByDescendingPriority() {
    KnowledgeBase knowledgeBase = new KnowledgeBase(List.of(rule("R1", 10), rule("R2", 30), rule("R3", 20)));

    assertEquals(List.of("R2", "R3", "R1"), knowledgeBase.rules().stream().map(Rule::ruleId).toList());
  }

  @Test
  void equalPrioritiesKeepFileOrder() {
    KnowledgeBase knowledgeBase = new KnowledgeBase(List.of(rule("R1", 10), rule("R2", 10), rule("R3", 10)));

    assertEquals(List.of("R1", "R2", "R3"), knowledgeBase.rules().stream().map(Rule::ruleId).toList());
  }

  @Test
  void rulesAreReadOnly() {
    KnowledgeBase knowledgeBase = new KnowledgeBase(List.of(rule("R1", 10)));

    assertThrows(UnsupportedOperationException.class, () -> knowledgeBase.rules().clear());
  }
}
