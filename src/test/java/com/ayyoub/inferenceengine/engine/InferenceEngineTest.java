package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InferenceEngineTest {

  private static Fact fact(String id, String attribute, Object value, int level) {
    return new Fact(id, attribute, "=", value, 1.0, null, level);
  }

  private static Map<String, Object> leaf(String attribute, String operator, Object value) {
    return Map.of("attribute", attribute, "operator", operator, "value", value);
  }

  private static Rule rule(String id, int priority, Map<String, Object> condition, String conclusionAttribute, Object conclusionValue) {
    return new Rule(id, id, condition, List.of(Map.of("attribute", conclusionAttribute, "operator", "=", "value", conclusionValue)), priority, 1.0,
        "");
  }

  private static InferenceEngine engine(Rule... rules) {
    return new InferenceEngine(new KnowledgeBase(List.of(rules)), new ConditionEvaluator());
  }

  @Test
  void saturatesToAFixedPoint() {
    Rule r1 = rule("R1", 10, Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))), "G", true);
    Rule r10 = rule("R10", 100, Map.of("all", List.of(leaf("G", "=", true), leaf("C", "=", true))), "P", true);

    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));
    memory.addFact(fact("F2", "B", true, 0));
    memory.addFact(fact("F3", "C", true, 0));

    engine(r1, r10).run(memory);

    Fact derived = memory.facts().stream().filter(f -> f.attribute().equals("P")).findFirst().orElseThrow();

    assertEquals(2, derived.level());
    assertEquals("R10", derived.derivedBy());
    assertEquals(2, memory.derivations().size());
  }

  @Test
  void recordsMinimalPremisesAndAbsences() {
    Rule r3 = rule("R3", 30, Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true)))), "I", true);

    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));
    memory.addFact(fact("F2", "C", false, 0));

    engine(r3).run(memory);

    Derivation derivation = memory.derivations().get(0);

    assertEquals("R3", derivation.ruleId());
    assertEquals(List.of("F1"), derivation.premiseIds());
    assertEquals("C absent", derivation.absences().get(0).label());
  }

  @Test
  void duplicateConclusionsAreDerivedOnlyOnce() {
    Rule high = rule("R_HIGH", 100, leaf("A", "=", true), "X", true);
    Rule low = rule("R_LOW", 10, leaf("A", "=", true), "X", true);

    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));

    engine(low, high).run(memory);

    List<Fact> conclusions = memory.facts().stream().filter(f -> f.attribute().equals("X")).toList();

    assertEquals(1, conclusions.size());
    assertEquals("R_HIGH", conclusions.get(0).derivedBy());
    assertEquals(1, memory.derivations().size());
  }

  @Test
  void highestPriorityFiresFirst() {
    Rule high = rule("R_HIGH", 170, Map.of("any", List.of(leaf("A", "=", true), leaf("B", "=", true))), "Y", true);
    Rule low = rule("R_LOW", 10, Map.of("any", List.of(leaf("A", "=", true), leaf("B", "=", true))), "Z", true);

    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));

    engine(low, high).run(memory);

    assertEquals(List.of("R_HIGH", "R_LOW"), memory.derivations().stream().map(Derivation::ruleId).toList());
  }

  @Test
  void derivedIdsContinuePastExistingNumericIds() {
    Rule r1 = rule("R1", 10, leaf("A", "=", true), "G", true);

    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F7", "A", true, 0));

    engine(r1).run(memory);

    assertEquals("F8", memory.facts().get(1).factId());
  }

  @Test
  void emptyRulesLeaveMemoryUntouched() {
    WorkingMemory memory = new WorkingMemory();
    memory.addFact(fact("F1", "A", true, 0));

    engine().run(memory);

    assertEquals(1, memory.facts().size());
    assertTrue(memory.derivations().isEmpty());
  }
}
