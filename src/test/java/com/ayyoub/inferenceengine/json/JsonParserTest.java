package com.ayyoub.inferenceengine.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonParserTest {

  private final JsonParser parser = new JsonParserImpl();

  @Test
  void parsesTheRuleWrapper() {
    List<Rule> rules = parser.parseRules("/rules.json");

    assertEquals(17, rules.size());
    assertEquals("R1", rules.get(0).ruleId());
    assertEquals(10, rules.get(0).priority());
    assertTrue(rules.get(0).condition().containsKey("all"));
  }

  @Test
  void parsesFactsAndInfersTypes() {
    List<Fact> facts = parser.parseFacts("/facts.json");

    assertEquals(7, facts.size());
    assertEquals("A", facts.get(0).attribute());
    assertTrue(facts.get(0).value() instanceof Boolean);
    assertTrue(facts.get(4).value() instanceof Integer);
    assertTrue(facts.get(6).value() instanceof String);
  }

  @Test
  void missingResourceNamesTheResource() {
    JsonParseException error = assertThrows(JsonParseException.class, () -> parser.parseFacts("/does-not-exist.json"));

    assertEquals("/does-not-exist.json", error.resource());
  }

  @Test
  void malformedJsonIsRejected() {
    assertThrows(JsonParseException.class, () -> parser.parseFacts("/malformed.json"));
    assertThrows(JsonParseException.class, () -> parser.parseRules("/malformed.json"));
  }

  @Test
  void rulesDocumentWithoutAWrapperIsRejected() {
    assertThrows(JsonParseException.class, () -> parser.parseRules("/facts.json"));
  }
}
