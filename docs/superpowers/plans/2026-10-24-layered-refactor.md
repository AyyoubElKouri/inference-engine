# Layered Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the JavaFX inference engine into layered packages with a service facade, a JSON port, Guice wiring, and a reusable UI component library, without changing the product's behavior beyond the accepted changes in the spec.

**Architecture:** `ui` depends only on `service` and `domain`; `service` orchestrates `engine`, `json`, and `domain`; `engine` and `json` depend only on `domain`. Guice `AppModule` is the single composition root. `Launcher` creates the injector and hands a fully wired `EngineUI` its dependencies.

**Tech Stack:** Java 21, JavaFX 21, Guice 7, Jackson 2.17, JUnit 5, Maven, Spotless (Eclipse formatter, 2 spaces, 150 columns).

**Spec:** `docs/superpowers/specs/2026-10-24-layered-refactor-design.md`

## Global Constraints

- Java source and target: 21.
- Use `./mvnw`, never `mvn`.
- Formatting: Spotless with `eclipse-formatter.xml`, 2-space indent, 150-column limit.
- Javadoc is always multi-line, never a one-liner.
- Group code into logical blocks; separate blocks with exactly one blank line.
- No `javafx` import outside `ui`, except `Launcher` (the JavaFX entry point).
- No Jackson import outside `json`.
- No classpath or file I/O outside `json`.
- Resource paths are `"/rules.json"` and `"/facts.json"`, loaded from the classpath.
- Rules stable-sort descending by numeric priority (170 before 10); ties keep file order.
- Behavior changes allowed only: priority order, fail-fast loading, classpath resources, records/Lombok removal, modernized method names.

## Review Focus

- A rule with an unknown operator must fail loudly with `IllegalArgumentException`, not silently skip.
- Empty or whitespace-only string conclusions must not create blank combo-box options.
- Running with zero inputs must produce an empty result, not a null or a crash.
- Two rules concluding the same attribute and value must not create duplicate facts; the first fired rule (higher priority) owns the fact.
- A malformed JSON document must raise `JsonParseException` naming the resource, never an empty catalogue.

---

### Task 1: Domain records and test reset

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/domain/Fact.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/domain/Rule.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/domain/Derivation.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/domain/AbsenceWitness.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/domain/AbsenceWitnessTest.java`
- Delete: `src/test/java/com/ayyoub/inferenceengine/FullRunTraceTest.java`
- Delete: `src/test/java/com/ayyoub/inferenceengine/InferenceEngineDerivationTest.java`
- Delete: `src/test/java/com/ayyoub/inferenceengine/TraceGraphModelTest.java`
- Delete: `src/test/java/com/ayyoub/inferenceengine/engine/ConditionEvaluatorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Fact`, `Rule`, `Derivation`, `AbsenceWitness` records used by every later task.

- [ ] **Step 1: Write the failing test**

```java
package com.ayyoub.inferenceengine.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbsenceWitnessTest {

  @Test
  void labelNamesTheMissingAttribute() {
    assertEquals("C absent", new AbsenceWitness("C", "=", true).label());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=AbsenceWitnessTest test`
Expected: FAIL — `package com.ayyoub.inferenceengine.domain does not exist`.

- [ ] **Step 3: Create the domain records**

`Fact.java`:

```java
package com.ayyoub.inferenceengine.domain;

/**
 * One fact in working memory or in the fact catalogue.
 */
public record Fact(
    String factId,
    String attribute,
    String operator,
    Object value,
    double confidence,
    String derivedBy,
    int level) {
}
```

`Rule.java`:

```java
package com.ayyoub.inferenceengine.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * One inference rule loaded from rules.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Rule(
    @JsonProperty("id") String ruleId,
    @JsonProperty("name") String name,
    @JsonProperty("condition") Map<String, Object> condition,
    @JsonProperty("conclusions") List<Map<String, Object>> conclusions,
    @JsonProperty("priority") int priority,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("explanation") String explanation) {
}
```

`Derivation.java`:

```java
package com.ayyoub.inferenceengine.domain;

import java.util.List;

/**
 * Provenance recorded when a rule fires, for one derived conclusion fact.
 */
public record Derivation(
    String ruleId,
    String conclusionId,
    List<String> premiseIds,
    List<AbsenceWitness> absences) {
}
```

`AbsenceWitness.java`:

```java
package com.ayyoub.inferenceengine.domain;

/**
 * Witness that a negated branch holds because its leaf is absent from working memory.
 */
public record AbsenceWitness(
    String attribute,
    String operator,
    Object value) {

  /**
   * Short badge text for the trace graph, such as "C absent".
   */
  public String label() {
    return attribute + " absent";
  }
}
```

- [ ] **Step 4: Delete the old tests**

Run:

```bash
git rm src/test/java/com/ayyoub/inferenceengine/FullRunTraceTest.java \
  src/test/java/com/ayyoub/inferenceengine/InferenceEngineDerivationTest.java \
  src/test/java/com/ayyoub/inferenceengine/TraceGraphModelTest.java \
  src/test/java/com/ayyoub/inferenceengine/engine/ConditionEvaluatorTest.java
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=AbsenceWitnessTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/domain src/test/java/com/ayyoub/inferenceengine/domain
git commit -m "refactor: add immutable domain records"
```

---

### Task 2: ConditionEvaluator

**Files:**
- Modify: `src/main/java/com/ayyoub/inferenceengine/engine/ConditionEvaluator.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/engine/ConditionEvaluatorTest.java`

**Interfaces:**
- Consumes: `Fact`, `AbsenceWitness` from Task 1.
- Produces: `ConditionEvaluator()`, `MatchResult(boolean matched, int depth)`, `ProofResult(boolean matched, int depth, List<String> premiseIds, List<AbsenceWitness> absences)`, `evaluate(Map, List<Fact>)`, `evaluateWithProvenance(Map, List<Fact>)`, `compare(Object, String, Object)`.

- [ ] **Step 1: Write the failing tests**

```java
package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.MatchResult;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.ProofResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  private static Fact fact(String id, String attribute, Object value, int level) {
    return new Fact(id, attribute, "=", value, 1.0, null, level);
  }

  private static Map<String, Object> leaf(String attribute, String operator, Object value) {
    return Map.of("attribute", attribute, "operator", operator, "value", value);
  }

  @Test
  void leafMatchReturnsDeepestAttributeLevel() {
    MatchResult result = evaluator.evaluate(leaf("A", "=", true), List.of(fact("F1", "A", true, 2)));

    assertTrue(result.matched());
    assertEquals(2, result.depth());
  }

  @Test
  void leafMismatchKeepsAttributeDepth() {
    MatchResult result = evaluator.evaluate(leaf("A", "=", true), List.of(fact("F1", "A", false, 3)));

    assertFalse(result.matched());
    assertEquals(3, result.depth());
  }

  @Test
  void numbersCompareAcrossIntegerAndDouble() {
    List<Fact> facts = List.of(fact("F5", "score", 80, 0));

    assertTrue(evaluator.compare(80, "=", 80.0));
    assertTrue(evaluator.compare(80, ">", 70.5));
    assertFalse(evaluator.compare(80, "<", 70.5));
  }

  @Test
  void connectivesMatchAsDocumented() {
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1), fact("F3", "C", false, 0));

    assertTrue(evaluator.evaluate(Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("all", List.of(leaf("A", "=", true), leaf("C", "=", true))), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("any", List.of(leaf("C", "=", true), leaf("B", "=", true))), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("not", leaf("C", "=", true)), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("xor", List.of(leaf("A", "=", true), leaf("B", "=", true))), facts).matched());
  }

  @Test
  void quantifiersCountMatchingBranches() {
    List<Map<String, Object>> branches = List.of(leaf("G", "=", true), leaf("H", "=", true), leaf("C", "=", true));
    List<Fact> facts = List.of(fact("F1", "G", true, 1), fact("F2", "H", true, 2));

    assertTrue(evaluator.evaluate(Map.of("at_least", 2, "of", branches), facts).matched());
    assertTrue(evaluator.evaluate(Map.of("exactly", 2, "of", branches), facts).matched());
    assertFalse(evaluator.evaluate(Map.of("at_most", 1, "of", branches), facts).matched());
  }

  @Test
  void emptyAndUnknownConditionsMatchNothing() {
    assertFalse(evaluator.evaluate(Map.of(), List.of(fact("F1", "A", true, 0))).matched());
    assertFalse(evaluator.evaluate(Map.of("unknown", List.of()), List.of()).matched());
    assertEquals(0, evaluator.evaluate(Map.of(), List.of()).depth());
  }

  @Test
  void allKeepsEveryPremise() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true)));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F2", "B", true, 1));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1", "F2"), proof.premiseIds());
    assertEquals(1, proof.depth());
  }

  @Test
  void anyKeepsOnlyTheFirstSufficientPremise() {
    Map<String, Object> condition = Map.of("any", List.of(leaf("C", "=", true), leaf("D", "=", true)));
    List<Fact> facts = List.of(fact("F3", "C", true, 0), fact("F4", "D", true, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertEquals(List.of("F3"), proof.premiseIds());
  }

  @Test
  void negatedBranchRecordsAnAbsenceWitness() {
    Map<String, Object> condition = Map.of("all", List.of(leaf("A", "=", true), Map.of("not", leaf("C", "=", true))));
    List<Fact> facts = List.of(fact("F1", "A", true, 0), fact("F3", "C", false, 0));

    ProofResult proof = evaluator.evaluateWithProvenance(condition, facts);

    assertTrue(proof.matched());
    assertEquals(List.of("F1"), proof.premiseIds());
    assertEquals(List.of(new AbsenceWitness("C", "=", true)), proof.absences());
    assertEquals("C absent", proof.absences().get(0).label());
  }

  @Test
  void unmatchedProofIsEmpty() {
    ProofResult proof = evaluator.evaluateWithProvenance(Map.of("all", List.of(leaf("A", "=", true), leaf("B", "=", true))),
        List.of(fact("F1", "A", true, 0)));

    assertFalse(proof.matched());
    assertTrue(proof.premiseIds().isEmpty());
    assertTrue(proof.absences().isEmpty());
  }

  @Test
  void nullNeverMatchesAndUnknownOperatorThrows() {
    assertFalse(evaluator.compare(null, "=", true));
    assertFalse(evaluator.compare(true, "=", null));
    assertThrows(IllegalArgumentException.class, () -> evaluator.compare(true, "≈", true));
  }

  @Test
  void sameClassComparablesOrder() {
    assertTrue(evaluator.compare("b", ">", "a"));
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=ConditionEvaluatorTest test`
Expected: FAIL — `ConditionEvaluator` does not exist in the new package.

- [ ] **Step 3: Implement ConditionEvaluator**

Copy the existing `src/main/java/com/ayyoub/inferenceengine/engine/ConditionEvaluator.java` verbatim, then make only these changes:

1. Change imports to `com.ayyoub.inferenceengine.domain.AbsenceWitness` and `com.ayyoub.inferenceengine.domain.Fact`.
2. Delete the nested `AbsenceWitness` record (it now lives in `domain`).
3. Replace fact accessors throughout: `getAttribute()` → `attribute()`, `getLevel()` → `level()`, `getFactId()` → `factId()`, `getValue()` → `value()`.
4. Keep `MatchResult`, `ProofResult`, `evaluate`, `evaluateWithProvenance`, `compare`, and every private helper unchanged.

The resulting public shape must be:

```java
public class ConditionEvaluator {

  public record MatchResult(boolean matched, int depth) {
  }

  public record ProofResult(
      boolean matched,
      int depth,
      List<String> premiseIds,
      List<AbsenceWitness> absences) {
  }

  public MatchResult evaluate(Map<String, Object> condition, List<Fact> facts) { ... }

  public ProofResult evaluateWithProvenance(Map<String, Object> condition, List<Fact> facts) { ... }

  public boolean compare(Object real, String operator, Object expected) { ... }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=ConditionEvaluatorTest test`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/engine/ConditionEvaluator.java \
  src/test/java/com/ayyoub/inferenceengine/engine/ConditionEvaluatorTest.java
git commit -m "refactor: move condition evaluator onto domain records"
```

---

### Task 3: WorkingMemory and KnowledgeBase

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/engine/WorkingMemory.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/engine/KnowledgeBase.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/engine/WorkingMemoryTest.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/engine/KnowledgeBaseTest.java`

**Interfaces:**
- Consumes: `Fact`, `Rule`, `Derivation` from Task 1.
- Produces: `WorkingMemory.addFact/facts/addDerivation/derivations/currentLevel`; `KnowledgeBase(List<Rule>)/rules()`.

- [ ] **Step 1: Write the failing tests**

`WorkingMemoryTest.java`:

```java
package com.ayyoub.inferenceengine.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingMemoryTest {

  @Test
  void storesFactsAndDerivationsInInsertionOrder() {
    WorkingMemory memory = new WorkingMemory();
    Fact first = new Fact("F1", "A", "=", true, 1.0, null, 0);
    Fact second = new Fact("F2", "G", "=", true, 1.0, "R1", 1);

    memory.addFact(first);
    memory.addFact(second);
    memory.addDerivation(new Derivation("R1", "F2", List.of("F1"), List.of()));

    assertEquals(List.of(first, second), memory.facts());
    assertEquals(1, memory.derivations().size());
  }

  @Test
  void viewsAreReadOnly() {
    WorkingMemory memory = new WorkingMemory();

    assertThrows(UnsupportedOperationException.class, () -> memory.facts().add(new Fact("F1", "A", "=", true, 1.0, null, 0)));
    assertThrows(UnsupportedOperationException.class, () -> memory.derivations().clear());
  }

  @Test
  void currentLevelIsTheMaximumFactLevel() {
    WorkingMemory memory = new WorkingMemory();

    assertEquals(0, memory.currentLevel());
    memory.addFact(new Fact("F1", "A", "=", true, 1.0, null, 0));
    memory.addFact(new Fact("F2", "G", "=", true, 1.0, "R1", 3));

    assertEquals(3, memory.currentLevel());
    assertTrue(memory.facts().size() == 2);
  }
}
```

`KnowledgeBaseTest.java`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest='WorkingMemoryTest,KnowledgeBaseTest' test`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement the stores**

`WorkingMemory.java`:

```java
package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Facts and provenance for one inference run. Storage only; no reasoning.
 */
public final class WorkingMemory {

  private final List<Fact> facts = new ArrayList<>();
  private final List<Derivation> derivations = new ArrayList<>();
  private final List<Fact> factsView = Collections.unmodifiableList(facts);
  private final List<Derivation> derivationsView = Collections.unmodifiableList(derivations);

  /**
   * Adds one fact.
   */
  public void addFact(Fact fact) {
    facts.add(fact);
  }

  /**
   * Read-only fact view, in insertion order.
   */
  public List<Fact> facts() {
    return factsView;
  }

  /**
   * Records one firing's provenance.
   */
  public void addDerivation(Derivation derivation) {
    derivations.add(derivation);
  }

  /**
   * Read-only derivation view, in firing order.
   */
  public List<Derivation> derivations() {
    return derivationsView;
  }

  /**
   * Highest fact level, or zero when empty.
   */
  public int currentLevel() {
    return facts.stream().mapToInt(Fact::level).max().orElse(0);
  }
}
```

`KnowledgeBase.java`:

```java
package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.domain.Rule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only rule store, ordered by descending priority.
 */
public final class KnowledgeBase {

  private final List<Rule> rules;

  /**
   * Copies and stable-sorts rules, highest numeric priority first.
   */
  public KnowledgeBase(List<Rule> rules) {
    List<Rule> sorted = new ArrayList<>(rules);
    sorted.sort(Comparator.comparingInt(Rule::priority).reversed());
    this.rules = List.copyOf(sorted);
  }

  /**
   * Rules in firing order.
   */
  public List<Rule> rules() {
    return rules;
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest='WorkingMemoryTest,KnowledgeBaseTest' test`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/engine/WorkingMemory.java \
  src/main/java/com/ayyoub/inferenceengine/engine/KnowledgeBase.java \
  src/test/java/com/ayyoub/inferenceengine/engine/WorkingMemoryTest.java \
  src/test/java/com/ayyoub/inferenceengine/engine/KnowledgeBaseTest.java
git commit -m "refactor: make working memory and knowledge base passive stores"
```

---

### Task 4: InferenceEngine

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/engine/InferenceEngine.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/engine/InferenceEngineTest.java`

**Interfaces:**
- Consumes: `KnowledgeBase`, `WorkingMemory`, `ConditionEvaluator`, `Fact`, `Rule`, `Derivation`.
- Produces: `new InferenceEngine(KnowledgeBase, ConditionEvaluator)` and `run(WorkingMemory)`.

- [ ] **Step 1: Write the failing tests**

```java
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
    return new Rule(id, id, condition,
        List.of(Map.of("attribute", conclusionAttribute, "operator", "=", "value", conclusionValue)), priority, 1.0, "");
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=InferenceEngineTest test`
Expected: FAIL — `InferenceEngine` does not exist in the new package.

- [ ] **Step 3: Implement InferenceEngine**

```java
package com.ayyoub.inferenceengine.engine;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import com.ayyoub.inferenceengine.engine.ConditionEvaluator.ProofResult;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Forward-chaining engine over the priority-ordered knowledge base.
 */
public final class InferenceEngine {

  private final KnowledgeBase knowledgeBase;
  private final ConditionEvaluator evaluator;

  /**
   * Creates the engine.
   */
  @Inject
  public InferenceEngine(KnowledgeBase knowledgeBase, ConditionEvaluator evaluator) {
    this.knowledgeBase = knowledgeBase;
    this.evaluator = evaluator;
  }

  /**
   * Derives facts until a full pass over the rules adds nothing new.
   */
  public void run(WorkingMemory memory) {
    List<Rule> rules = knowledgeBase.rules();

    // Start past the highest existing numeric id so derived ids never
    // collide with seed facts.
    int counter = memory.facts().stream().map(Fact::factId).filter(id -> id != null && id.matches("F\\d+"))
        .mapToInt(id -> Integer.parseInt(id.substring(1))).max().orElse(memory.facts().size()) + 1;
    boolean factsChanged = true;

    // Keep applying rules until a complete pass adds no new facts.
    while (factsChanged) {
      factsChanged = false;

      for (Rule rule : rules) {
        ProofResult proof = evaluator.evaluateWithProvenance(rule.condition(), memory.facts());

        if (!proof.matched())
          continue;

        for (Map<String, Object> conclusion : rule.conclusions()) {
          String attribute = (String) conclusion.get("attribute");
          String operator = (String) conclusion.get("operator");
          Object value = conclusion.get("value");

          if (conclusionExists(memory, attribute, operator, value))
            continue;

          int level = proof.depth() + 1;
          String conclusionId = "F" + counter++;

          memory.addFact(new Fact(conclusionId, attribute, operator, value, rule.confidence(), rule.ruleId(), level));
          memory.addDerivation(new Derivation(rule.ruleId(), conclusionId, proof.premiseIds(), proof.absences()));
          factsChanged = true;
        }
      }
    }
  }

  /**
   * True when a fact with the same attribute already satisfies the conclusion value.
   */
  private boolean conclusionExists(WorkingMemory memory, String attribute, String operator, Object value) {
    return memory.facts().stream().anyMatch(fact -> fact.attribute().equals(attribute) && evaluator.compare(fact.value(), operator, value));
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=InferenceEngineTest test`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/engine/InferenceEngine.java \
  src/test/java/com/ayyoub/inferenceengine/engine/InferenceEngineTest.java
git commit -m "refactor: extract priority-ordered forward chaining engine"
```

---

### Task 5: JsonParser adapter

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/json/JsonParser.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/json/JsonParserImpl.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/json/JsonParseException.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/json/JsonParserTest.java`
- Create: `src/test/resources/malformed.json`

**Interfaces:**
- Consumes: `Rule`, `Fact`.
- Produces: `parseRules(String)`, `parseFacts(String)`, `JsonParseException(String resource, Throwable cause)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/resources/malformed.json` containing exactly:

```
{ this is not json
```

`JsonParserTest.java`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=JsonParserTest test`
Expected: FAIL — parser classes do not exist.

- [ ] **Step 3: Implement the parser**

`JsonParseException.java`:

```java
package com.ayyoub.inferenceengine.json;

/**
 * Unchecked failure while loading or parsing a JSON resource.
 */
public class JsonParseException extends RuntimeException {

  private final String resource;

  /**
   * Creates the failure for one resource.
   */
  public JsonParseException(String resource, Throwable cause) {
    super("Could not parse " + resource + ": " + cause.getMessage(), cause);
    this.resource = resource;
  }

  /**
   * Resource name that failed.
   */
  public String resource() {
    return resource;
  }
}
```

`JsonParser.java`:

```java
package com.ayyoub.inferenceengine.json;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import java.util.List;

/**
 * Reads domain objects from JSON resources.
 */
public interface JsonParser {

  /**
   * Parses knowledge_base.rules from a classpath resource.
   */
  List<Rule> parseRules(String classpathResource);

  /**
   * Parses a top-level fact array from a classpath resource.
   */
  List<Fact> parseFacts(String classpathResource);
}
```

`JsonParserImpl.java`:

```java
package com.ayyoub.inferenceengine.json;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson-backed parser for classpath resources.
 */
public final class JsonParserImpl implements JsonParser {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public List<Rule> parseRules(String classpathResource) {
    JsonNode rulesNode = read(classpathResource).path("knowledge_base").path("rules");

    if (!rulesNode.isArray())
      throw new JsonParseException(classpathResource, new IllegalArgumentException("Expected knowledge_base.rules array"));

    try {
      return mapper.convertValue(rulesNode, new TypeReference<List<Rule>>() {
      });
    } catch (IllegalArgumentException error) {
      throw new JsonParseException(classpathResource, error);
    }
  }

  @Override
  public List<Fact> parseFacts(String classpathResource) {
    JsonNode root = read(classpathResource);

    if (!root.isArray())
      throw new JsonParseException(classpathResource, new IllegalArgumentException("Expected a top-level array"));

    List<Fact> facts = new ArrayList<>();

    for (JsonNode node : root)
      facts.add(fact(node));

    return List.copyOf(facts);
  }

  /**
   * Reads one resource and fails with its name on any problem.
   */
  private JsonNode read(String classpathResource) {
    try (InputStream input = JsonParserImpl.class.getResourceAsStream(classpathResource)) {
      if (input == null)
        throw new JsonParseException(classpathResource, new IOException("Resource not found"));

      return mapper.readTree(input);
    } catch (JsonParseException error) {
      throw error;
    } catch (IOException error) {
      throw new JsonParseException(classpathResource, error);
    }
  }

  /**
   * Maps one JSON fact node, inferring the value type.
   */
  private Fact fact(JsonNode node) {
    String id = node.path("id").asText("F?");
    String attribute = node.path("attribute").asText();
    String operator = node.path("operator").asText("=");
    double confidence = node.path("confidence").asDouble(1.0);
    int level = node.path("level").asInt(0);
    String derivedBy = node.path("derived_by").isNull() ? null : node.path("derived_by").asText();
    JsonNode valueNode = node.path("value");
    Object value;

    if (valueNode.isBoolean())
      value = valueNode.asBoolean();
    else if (valueNode.isInt())
      value = valueNode.asInt();
    else if (valueNode.isLong())
      value = (int) valueNode.asLong();
    else if (valueNode.isDouble() || valueNode.isFloat())
      value = valueNode.asDouble();
    else
      value = valueNode.asText();

    return new Fact(id, attribute, operator, value, confidence, derivedBy, level);
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=JsonParserTest test`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/json src/test/java/com/ayyoub/inferenceengine/json src/test/resources/malformed.json
git commit -m "refactor: isolate JSON loading behind a classpath parser"
```

---

### Task 6: InferenceService

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/service/InferenceService.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/service/FactInput.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/service/InferenceResult.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/service/InferenceServiceImpl.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/service/InferenceServiceTest.java`

**Interfaces:**
- Consumes: `JsonParser`, `KnowledgeBase`, `InferenceEngine`, `WorkingMemory`, `Fact`, `Derivation`.
- Produces: `factCatalogue()`, `valueOptions(String)`, `run(List<FactInput>)`.

- [ ] **Step 1: Write the failing tests**

```java
package com.ayyoub.inferenceengine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.Fact;
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
    service.run(List.of(new FactInput("A", true), new FactInput("B", true)));
    InferenceResult second = service.run(List.of(new FactInput("A", true)));

    assertEquals(1, second.facts().size());
    assertEquals(List.of("F1"), second.facts().stream().map(Fact::factId).toList());
  }

  @Test
  void emptyInputRunReturnsAnEmptyResult() {
    InferenceResult result = service.run(List.of());

    assertTrue(result.facts().isEmpty());
    assertTrue(result.derivations().isEmpty());
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=InferenceServiceTest test`
Expected: FAIL — service types do not exist.

- [ ] **Step 3: Implement the service**

`FactInput.java`:

```java
package com.ayyoub.inferenceengine.service;

/**
 * One fact the user chose to assert before a run.
 */
public record FactInput(
    String attribute,
    Object value) {
}
```

`InferenceResult.java`:

```java
package com.ayyoub.inferenceengine.service;

import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;

/**
 * Immutable outcome of one inference run.
 */
public record InferenceResult(
    List<Fact> facts,
    List<Derivation> derivations) {
}
```

`InferenceService.java`:

```java
package com.ayyoub.inferenceengine.service;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;

/**
 * Use-case boundary between the UI and the reasoning engine.
 */
public interface InferenceService {

  /**
   * Templates available in the fact builder.
   */
  List<Fact> factCatalogue();

  /**
   * Known string values for an attribute, in encounter order.
   */
  List<String> valueOptions(String attribute);

  /**
   * Runs one inference session over the supplied facts.
   */
  InferenceResult run(List<FactInput> inputs);
}
```

`InferenceServiceImpl.java`:

```java
package com.ayyoub.inferenceengine.service;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.domain.Rule;
import com.ayyoub.inferenceengine.engine.InferenceEngine;
import com.ayyoub.inferenceengine.engine.KnowledgeBase;
import com.ayyoub.inferenceengine.engine.WorkingMemory;
import com.ayyoub.inferenceengine.json.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the catalogue once and runs the engine per request.
 */
public final class InferenceServiceImpl implements InferenceService {

  private final KnowledgeBase knowledgeBase;
  private final InferenceEngine engine;
  private final List<Fact> catalogue;
  private final Map<String, Fact> templatesByAttribute;

  /**
   * Loads the catalogue eagerly so failures surface at startup.
   */
  @Inject
  public InferenceServiceImpl(
      JsonParser parser,
      @Named("factsResource") String factsResource,
      KnowledgeBase knowledgeBase,
      InferenceEngine engine) {
    this.knowledgeBase = knowledgeBase;
    this.engine = engine;
    this.catalogue = parser.parseFacts(factsResource);
    this.templatesByAttribute = catalogue.stream().collect(Collectors.toMap(Fact::attribute, Function.identity(), (first, second) -> first));
  }

  @Override
  public List<Fact> factCatalogue() {
    return catalogue;
  }

  @Override
  public List<String> valueOptions(String attribute) {
    Set<String> options = new LinkedHashSet<>();

    // The catalogue's own value comes first, matching the old UI.
    for (Fact template : catalogue)
      if (template.attribute().equals(attribute) && template.value() instanceof String text && !text.isBlank())
        options.add(text);

    // Rule conclusions can introduce further choices.
    for (Rule rule : knowledgeBase.rules())
      for (Map<String, Object> conclusion : rule.conclusions())
        if (attribute.equals(conclusion.get("attribute")) && conclusion.get("value") instanceof String text && !text.isBlank())
          options.add(text);

    return List.copyOf(options);
  }

  @Override
  public InferenceResult run(List<FactInput> inputs) {
    WorkingMemory memory = new WorkingMemory();
    int index = 1;

    for (FactInput input : inputs) {
      Fact template = templatesByAttribute.get(input.attribute());
      double confidence = template != null ? template.confidence() : 1.0;

      memory.addFact(new Fact("F" + index++, input.attribute(), "=", input.value(), confidence, null, 0));
    }

    engine.run(memory);

    return new InferenceResult(List.copyOf(memory.facts()), List.copyOf(memory.derivations()));
  }
}
```

Note: `InferenceServiceImpl` uses both constructor parameters during construction and keeps neither as a field.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=InferenceServiceTest test`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/service src/test/java/com/ayyoub/inferenceengine/service
git commit -m "refactor: add inference service facade"
```

---

### Task 7: UI foundation (tokens, Button, TypeBadge, Dialogs)

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/DesignTokens.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/Button.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/TypeBadge.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/Dialogs.java`

**Interfaces:**
- Consumes: JavaFX.
- Produces: `DesignTokens` constants and `label(...)`/`spacer(...)` helpers; `Button(String, Button.Variant)`; `TypeBadge.of(Object)`; `Dialogs.showError(Window, String, String)`.

- [ ] **Step 1: Create the design tokens**

```java
package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Single source of truth for the UI's colors, sizes, and shared helpers.
 */
public final class DesignTokens {

  public static final String BG = "#F7F8FA";
  public static final String SURFACE = "#FFFFFF";
  public static final String PRIMARY = "#1B4FD8";
  public static final String PRIMARY_ALT = "#1640C0";
  public static final String PRIMARY_TL = "#EEF2FF";
  public static final String BORDER = "#E5E7EB";
  public static final String BORDER_DARK = "#D1D5DB";
  public static final String TXT1 = "#0D0D14";
  public static final String TXT2 = "#6B7280";
  public static final String TXT3 = "#9CA3AF";
  public static final String SUCCESS = "#059669";
  public static final String WARN = "#D97706";
  public static final String DANGER = "#DC2626";
  public static final String DANGER_TL = "#FEF2F2";
  public static final String RULE = "#7C3AED";
  public static final String RULE_TL = "#F5F3FF";

  /**
   * Shared small label factory.
   */
  public static Label label(String text, int size, String color) {
    Label label = new Label(text);
    label.setFont(Font.font("System", size));
    label.setTextFill(Color.web(color));
    return label;
  }

  /**
   * Fixed-width horizontal spacer.
   */
  public static Region spacer(double width) {
    Region region = new Region();
    region.setPrefWidth(width);
    return region;
  }

  private DesignTokens() {
  }
}
```

- [ ] **Step 2: Create the Button component**

```java
package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Styled button with a small set of design-system variants.
 */
public class Button extends javafx.scene.control.Button {

  /**
   * Supported visual variants.
   */
  public enum Variant {
    PRIMARY, SECONDARY, GHOST, DANGER
  }

  private final Variant variant;

  /**
   * Creates a button in the requested variant.
   */
  public Button(String text, Variant variant) {
    super(text);
    this.variant = variant;
    setFont(Font.font("System", FontWeight.BOLD, 12));
    setStyle(style(false));
    setOnMouseEntered(event -> {
      if (!isDisabled())
        setStyle(style(true));
    });
    setOnMouseExited(event -> setStyle(style(false)));
    disabledProperty().addListener((observable, previous, current) -> setStyle(style(false)));
  }

  /**
   * Style string for the current variant and hover state.
   */
  private String style(boolean hover) {
    return switch (variant) {
      case PRIMARY -> "-fx-background-color: " + (hover ? DesignTokens.PRIMARY_ALT : DesignTokens.PRIMARY) + ";"
          + "-fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 9 22; -fx-cursor: hand;";
      case SECONDARY -> "-fx-background-color: " + (hover ? DesignTokens.PRIMARY_TL : DesignTokens.SURFACE) + ";"
          + "-fx-text-fill: " + DesignTokens.TXT1 + "; -fx-border-color: " + DesignTokens.BORDER + ";"
          + "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 6 14; -fx-cursor: hand;";
      case GHOST -> "-fx-background-color: transparent; -fx-text-fill: " + (hover ? DesignTokens.TXT1 : DesignTokens.TXT3) + ";"
          + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
      case DANGER -> "-fx-background-color: " + (hover ? DesignTokens.DANGER_TL : "transparent") + ";"
          + "-fx-text-fill: " + (hover ? DesignTokens.DANGER : DesignTokens.TXT3) + ";"
          + "-fx-padding: 2 6; -fx-cursor: hand; -fx-background-radius: 5;";
    };
  }
}
```

- [ ] **Step 3: Create TypeBadge and Dialogs**

`TypeBadge.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

import javafx.scene.control.Label;
import javafx.scene.text.Font;

/**
 * Small badge naming a value's type.
 */
public final class TypeBadge {

  /**
   * Builds the badge for one value.
   */
  public static Label of(Object value) {
    String text;
    String foreground;
    String background;

    if (value instanceof Boolean) {
      text = "booléen";
      foreground = DesignTokens.RULE;
      background = DesignTokens.RULE_TL;
    } else if (value instanceof Integer) {
      text = "entier";
      foreground = "#0891B2";
      background = "#ECFEFF";
    } else if (value instanceof Double) {
      text = "décimal";
      foreground = "#0891B2";
      background = "#ECFEFF";
    } else {
      text = "texte";
      foreground = DesignTokens.TXT2;
      background = DesignTokens.BG;
    }

    Label badge = new Label(text);
    badge.setFont(Font.font("System", 10));
    badge.setTextFill(javafx.scene.paint.Color.web(foreground));
    badge.setStyle("-fx-background-color: " + background + "; -fx-background-radius: 20; -fx-padding: 2 9;");
    return badge;
  }

  private TypeBadge() {
  }
}
```

`Dialogs.java`:

```java
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
```

- [ ] **Step 4: Compile to verify the API**

Run: `./mvnw -q compile -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/ui/component/DesignTokens.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/Button.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/TypeBadge.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/Dialogs.java
git commit -m "feat(ui): add design tokens and base components"
```

---

### Task 8: Typed Input components

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/Input.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/BooleanInput.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/IntegerInput.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/DecimalInput.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/TextInput.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/ChoiceInput.java`

**Interfaces:**
- Consumes: `Fact`, JavaFX, `DesignTokens`.
- Produces: `Input.node()`, `Input.value()`, `Input.forFact(Fact, List<String>, Consumer<Object>)`.

- [ ] **Step 1: Create the Input interface and factory**

```java
package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * A typed editor for one fact template.
 */
public interface Input {

  /**
   * Rendered control.
   */
  Node node();

  /**
   * Current typed value.
   */
  Object value();

  /**
   * Picks the editor that matches the template's value type.
   */
  static Input forFact(Fact template, List<String> options, Consumer<Object> onChange) {
    Object value = template.value();

    if (value instanceof Boolean flag)
      return new BooleanInput(flag, onChange);
    if (value instanceof Integer number)
      return new IntegerInput(number, onChange);
    if (value instanceof Double number)
      return new DecimalInput(number, onChange);
    if (options.size() > 1)
      return new ChoiceInput(value != null ? value.toString() : "", options, onChange);

    return new TextInput(value != null ? value.toString() : "", onChange);
  }
}
```

- [ ] **Step 2: Implement BooleanInput with the existing animation**

```java
package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * iOS-style boolean toggle.
 */
public final class BooleanInput implements Input {

  private static final double TRACK_WIDTH = 48;
  private static final double TRACK_HEIGHT = 26;
  private static final double KNOB_DIAMETER = 20;
  private static final double PADDING = 3;

  private final Consumer<Object> onChange;
  private final HBox row;
  private boolean value;

  /**
   * Builds the toggle.
   */
  public BooleanInput(boolean initial, Consumer<Object> onChange) {
    this.value = initial;
    this.onChange = onChange;

    Rectangle track = new Rectangle(TRACK_WIDTH, TRACK_HEIGHT);
    track.setArcWidth(TRACK_HEIGHT);
    track.setArcHeight(TRACK_HEIGHT);

    Circle knob = new Circle(KNOB_DIAMETER / 2);
    knob.setFill(Color.WHITE);
    knob.setEffect(new DropShadow(4, 0, 1, Color.rgb(0, 0, 0, 0.22)));

    StackPane switchPane = new StackPane(track, knob);
    switchPane.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
    switchPane.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);
    switchPane.setStyle("-fx-cursor: hand;");

    applyState(track, knob, initial, false);

    switchPane.setOnMouseClicked(event -> {
      this.value = !this.value;
      onChange.accept(this.value);
      applyState(track, knob, this.value, true);
    });

    row = new HBox(10, DesignTokens.label("Non", 12, DesignTokens.TXT2), switchPane, DesignTokens.label("Oui", 12, DesignTokens.TXT2));
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(4, 0, 4, 0));
  }

  @Override
  public Node node() {
    return row;
  }

  @Override
  public Object value() {
    return value;
  }

  /**
   * Applies color and knob position, optionally animating.
   */
  private static void applyState(Rectangle track, Circle knob, boolean on, boolean animate) {
    double targetX = on ? (TRACK_WIDTH / 2) - (KNOB_DIAMETER / 2) - PADDING
        : -(TRACK_WIDTH / 2) + (KNOB_DIAMETER / 2) + PADDING;

    track.setFill(on ? Color.web(DesignTokens.PRIMARY) : Color.web(DesignTokens.BORDER_DARK));

    if (animate) {
      TranslateTransition transition = new TranslateTransition(Duration.millis(160), knob);
      transition.setToX(targetX);
      transition.play();
    } else {
      knob.setTranslateX(targetX);
    }
  }
}
```

- [ ] **Step 3: Implement the numeric, text, and choice inputs**

`IntegerInput.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Spinner;

/**
 * Editable integer spinner.
 */
public final class IntegerInput implements Input {

  private final Spinner<Integer> spinner;

  /**
   * Builds the spinner.
   */
  public IntegerInput(int initial, Consumer<Object> onChange) {
    spinner = new Spinner<>(-99999, 99999, initial, 1);
    spinner.setEditable(true);
    spinner.setMaxWidth(Double.MAX_VALUE);
    spinner.setStyle(Styles.input());
    spinner.valueProperty().addListener((observable, previous, current) -> {
      if (current != null)
        onChange.accept(current);
    });
  }

  @Override
  public Node node() {
    return spinner;
  }

  @Override
  public Object value() {
    return spinner.getValue();
  }
}
```

`DecimalInput.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/**
 * Decimal text field that keeps the last valid value.
 */
public final class DecimalInput implements Input {

  private final TextField field;
  private double value;

  /**
   * Builds the field.
   */
  public DecimalInput(double initial, Consumer<Object> onChange) {
    this.value = initial;
    field = new TextField(String.valueOf(initial));
    field.setStyle(Styles.input());
    field.textProperty().addListener((observable, previous, current) -> {
      try {
        this.value = Double.parseDouble(current);
        onChange.accept(this.value);
      } catch (NumberFormatException ignored) {
        // Keep the last valid value while the user is typing.
      }
    });
  }

  @Override
  public Node node() {
    return field;
  }

  @Override
  public Object value() {
    return value;
  }
}
```

`TextInput.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/**
 * Free-text field.
 */
public final class TextInput implements Input {

  private final TextField field;

  /**
   * Builds the field.
   */
  public TextInput(String initial, Consumer<Object> onChange) {
    field = new TextField(initial != null ? initial : "");
    field.setStyle(Styles.input());
    field.textProperty().addListener((observable, previous, current) -> onChange.accept(current));
  }

  @Override
  public Node node() {
    return field;
  }

  @Override
  public Object value() {
    return field.getText();
  }
}
```

`ChoiceInput.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

/**
 * Dropdown over known string values.
 */
public final class ChoiceInput implements Input {

  private final ComboBox<String> comboBox;

  /**
   * Builds the dropdown.
   */
  public ChoiceInput(String initial, List<String> options, Consumer<Object> onChange) {
    comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
    comboBox.setValue(initial);
    comboBox.setMaxWidth(Double.MAX_VALUE);
    comboBox.setStyle(Styles.combo());
    comboBox.valueProperty().addListener((observable, previous, current) -> {
      if (current != null)
        onChange.accept(current);
    });
  }

  @Override
  public Node node() {
    return comboBox;
  }

  @Override
  public Object value() {
    return comboBox.getValue();
  }
}
```

- [ ] **Step 4: Add the shared field styles**

Create `src/main/java/com/ayyoub/inferenceengine/ui/component/Styles.java`:

```java
package com.ayyoub.inferenceengine.ui.component;

/**
 * Shared JavaFX style strings for form controls.
 */
public final class Styles {

  /**
   * Text field, spinner, and decimal field.
   */
  public static String input() {
    return "-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: " + DesignTokens.BORDER_DARK + ";"
        + "-fx-border-radius: 7; -fx-background-radius: 7;" + "-fx-padding: 7 11; -fx-font-size: 13;";
  }

  /**
   * Combo box.
   */
  public static String combo() {
    return "-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: " + DesignTokens.BORDER_DARK + ";"
        + "-fx-border-radius: 7; -fx-background-radius: 7;" + "-fx-padding: 3 6; -fx-font-size: 13;" + "-fx-cursor: hand;";
  }

  private Styles() {
  }
}
```

- [ ] **Step 5: Compile to verify**

Run: `./mvnw -q compile -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/ui/component/Input.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/BooleanInput.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/IntegerInput.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/DecimalInput.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/TextInput.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/ChoiceInput.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/Styles.java
git commit -m "feat(ui): add typed input components"
```

---

### Task 9: Catalogue and selected-facts panels

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/CatalogueRow.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/CataloguePanel.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/FactRow.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/SelectedFactsPanel.java`

**Interfaces:**
- Consumes: `Fact`, `Input`, `Button`, `TypeBadge`, `DesignTokens`.
- Produces: `CataloguePanel(List<Fact>, Consumer<Fact>)` with `markUsed(String, boolean)`; `FactRow(Fact, List<String>, Consumer<Object>, Runnable)`; `SelectedFactsPanel()` with `addRow(Node)`, `removeRow(Node)`, `setCount(int)`.

- [ ] **Step 1: Create CatalogueRow**

```java
package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * One clickable catalogue entry.
 */
public final class CatalogueRow extends HBox {

  private final Fact template;
  private boolean used;

  /**
   * Builds the row.
   */
  public CatalogueRow(Fact template, Consumer<Fact> onAdd) {
    super(10);
    this.template = template;
    setPadding(new Insets(9, 12, 9, 12));
    setAlignment(Pos.CENTER_LEFT);
    setStyle(idleStyle());

    Label attribute = new Label(template.attribute());
    attribute.setFont(Font.font("System", FontWeight.BOLD, 12));
    attribute.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));
    HBox.setHgrow(attribute, Priority.ALWAYS);

    Label addIcon = new Label("+");
    addIcon.setFont(Font.font("System", FontWeight.BOLD, 14));
    addIcon.setTextFill(javafx.scene.paint.Color.web(DesignTokens.PRIMARY));
    addIcon.setMinWidth(20);
    addIcon.setAlignment(Pos.CENTER);
    addIcon.setVisible(false);

    getChildren().addAll(attribute, TypeBadge.of(template.value()), addIcon);

    setOnMouseEntered(event -> {
      if (used)
        return;
      setStyle("-fx-background-color: " + DesignTokens.PRIMARY_TL + ";" + "-fx-background-radius: 8; -fx-cursor: hand;");
      addIcon.setVisible(true);
    });
    setOnMouseExited(event -> {
      if (used)
        return;
      setStyle(idleStyle());
      addIcon.setVisible(false);
    });
    setOnMouseClicked(event -> {
      if (!used)
        onAdd.accept(template);
    });
  }

  /**
   * Dims and disables the row while its attribute is selected.
   */
  public void setUsed(boolean used) {
    this.used = used;
    setMouseTransparent(used);
    setOpacity(used ? 0.4 : 1.0);
    setStyle(used ? idleStyle() + "-fx-cursor: default;" : idleStyle());
  }

  private static String idleStyle() {
    return "-fx-background-color: transparent;" + "-fx-background-radius: 8;" + "-fx-cursor: hand;";
  }
}
```

- [ ] **Step 2: Create CataloguePanel**

```java
package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Left-hand catalogue of available fact templates.
 */
public final class CataloguePanel extends VBox {

  private final Map<String, CatalogueRow> rows = new HashMap<>();

  /**
   * Builds the panel and its rows.
   */
  public CataloguePanel(List<Fact> catalogue, Consumer<Fact> onAdd) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: transparent " + DesignTokens.BORDER
        + " transparent transparent;" + "-fx-border-width: 0 1 0 0;");

    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 20, 16, 20));
    header.setStyle("-fx-border-color: transparent transparent " + DesignTokens.BORDER + " transparent;" + "-fx-border-width: 0 0 1 0;");

    Label title = new Label("Catalogue");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    header.getChildren().addAll(title, DesignTokens.label("Cliquez pour ajouter un fait", 11, DesignTokens.TXT3));

    VBox list = new VBox(4);
    list.setPadding(new Insets(12, 10, 12, 10));

    if (catalogue.isEmpty()) {
      Label empty = DesignTokens.label("Aucun attribut dans facts.json", 12, DesignTokens.TXT3);
      empty.setPadding(new Insets(8, 10, 8, 10));
      list.getChildren().add(empty);
    } else {
      for (Fact template : catalogue) {
        CatalogueRow row = new CatalogueRow(template, onAdd);
        rows.put(template.attribute(), row);
        list.getChildren().add(row);
      }
    }

    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setBorder(javafx.scene.layout.Border.EMPTY);
    scroll.setStyle("-fx-background: " + DesignTokens.SURFACE + "; -fx-background-color: " + DesignTokens.SURFACE + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    getChildren().addAll(header, scroll);
  }

  /**
   * Marks one attribute as selected or available.
   */
  public void markUsed(String attribute, boolean used) {
    CatalogueRow row = rows.get(attribute);

    if (row != null)
      row.setUsed(used);
  }
}
```

- [ ] **Step 3: Create FactRow**

```java
package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * One selected fact card: attribute, badge, remove button, and typed input.
 */
public final class FactRow extends HBox {

  /**
   * Builds the card.
   */
  public FactRow(Fact template, List<String> options, Consumer<Object> onValueChange, Runnable onRemove) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: " + DesignTokens.BORDER + ";"
        + "-fx-border-radius: 10; -fx-background-radius: 10;");

    Region strip = new Region();
    strip.setPrefWidth(4);
    strip.setMinWidth(4);
    strip.setStyle("-fx-background-color: " + DesignTokens.PRIMARY + "; -fx-background-radius: 10 0 0 10;");

    VBox body = new VBox(10);
    body.setPadding(new Insets(14, 14, 14, 12));
    HBox.setHgrow(body, Priority.ALWAYS);

    HBox header = new HBox(8);
    header.setAlignment(Pos.CENTER_LEFT);

    Label attribute = new Label(template.attribute());
    attribute.setFont(Font.font("System", FontWeight.BOLD, 13));
    attribute.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button remove = new Button("✕", Button.Variant.DANGER);
    remove.setOnAction(event -> onRemove.run());

    header.getChildren().addAll(attribute, TypeBadge.of(template.value()), spacer, remove);

    Input input = Input.forFact(template, options, onValueChange);
    body.getChildren().addAll(header, input.node());
    getChildren().addAll(strip, body);
  }
}
```

- [ ] **Step 4: Create SelectedFactsPanel**

```java
package com.ayyoub.inferenceengine.ui.component;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Right-hand list of selected facts.
 */
public final class SelectedFactsPanel extends VBox {

  private final VBox list = new VBox(8);
  private final Label hint;

  /**
   * Builds the panel.
   */
  public SelectedFactsPanel() {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.BG + ";");

    VBox header = new VBox(4);
    header.setPadding(new Insets(24, 24, 16, 24));
    header.setStyle("-fx-background-color: " + DesignTokens.BG + ";" + "-fx-border-color: transparent transparent " + DesignTokens.BORDER
        + " transparent;" + "-fx-border-width: 0 0 1 0;");

    Label title = new Label("Faits sélectionnés");
    title.setFont(Font.font("System", FontWeight.BOLD, 13));
    title.setTextFill(javafx.scene.paint.Color.web(DesignTokens.TXT1));

    hint = DesignTokens.label("Aucun fait ajouté — sélectionnez dans le catalogue", 11, DesignTokens.TXT3);
    header.getChildren().addAll(title, hint);

    list.setPadding(new Insets(16, 24, 16, 24));

    ScrollPane scroll = new ScrollPane(list);
    scroll.setFitToWidth(true);
    scroll.setBorder(javafx.scene.layout.Border.EMPTY);
    scroll.setStyle("-fx-background: " + DesignTokens.BG + "; -fx-background-color: " + DesignTokens.BG + ";");
    VBox.setVgrow(scroll, Priority.ALWAYS);

    getChildren().addAll(header, scroll);
  }

  /**
   * Appends one fact card.
   */
  public void addRow(Node row) {
    list.getChildren().add(row);
  }

  /**
   * Removes one fact card.
   */
  public void removeRow(Node row) {
    list.getChildren().remove(row);
  }

  /**
   * Updates the count hint.
   */
  public void setCount(int count) {
    hint.setText(count == 0 ? "Aucun fait ajouté — sélectionnez dans le catalogue"
        : count + " fait" + (count > 1 ? "s" : "") + " sélectionné" + (count > 1 ? "s" : ""));
  }
}
```

- [ ] **Step 5: Compile to verify**

Run: `./mvnw -q compile -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/ui/component/CatalogueRow.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/CataloguePanel.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/FactRow.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/SelectedFactsPanel.java
git commit -m "feat(ui): add catalogue and selected-fact panels"
```

---

### Task 10: TopBar, StatsBar, and TraceGraphView

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/TopBar.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/StatsBar.java`
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/component/TraceGraphView.java` (moved from the old root package)
- Create: `src/test/java/com/ayyoub/inferenceengine/ui/component/TraceGraphViewTest.java`

**Interfaces:**
- Consumes: `Fact`, `Derivation`, `AbsenceWitness`, JavaFX, `DesignTokens`, `Button`.
- Produces: `TopBar(boolean, Runnable)`, `StatsBar(List<Fact>)`, and `TraceGraphView` with `layout`, `routeEdge`, `upstreamOf`, `downstreamOf`, `build`.

- [ ] **Step 1: Write the failing TraceGraphView tests**

```java
package com.ayyoub.inferenceengine.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ayyoub.inferenceengine.domain.AbsenceWitness;
import com.ayyoub.inferenceengine.domain.Derivation;
import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.EdgeKind;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphEdge;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphModel;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.GraphNode;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.NodeKind;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView.RoutePoint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceGraphViewTest {

  private static Fact fact(String id, String attribute, Object value, String derivedBy, int level) {
    return new Fact(id, attribute, "=", value, 1.0, derivedBy, level);
  }

  private static GraphModel smallRun() {
    List<Fact> facts = List.of(fact("F1", "A", true, null, 0), fact("F2", "B", true, null, 0), fact("F3", "G", true, "R1", 1),
        fact("F4", "I", true, "R3", 1));
    List<Derivation> derivations = List.of(new Derivation("R1", "F3", List.of("F1", "F2"), List.of()),
        new Derivation("R3", "F4", List.of("F1"), List.of(new AbsenceWitness("C", "=", true))));

    return TraceGraphView.layout(facts, derivations);
  }

  @Test
  void laysOutFactRuleFactChains() {
    GraphModel model = smallRun();

    assertTrue(model.edges().contains(new GraphEdge("F1", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new GraphEdge("F2", "R1", EdgeKind.PREMISE)));
    assertTrue(model.edges().contains(new GraphEdge("R1", "F3", EdgeKind.CONCLUSION)));
    assertEquals(List.of("R1", "R3"), model.ruleNodes().stream().map(GraphNode::id).sorted().toList());
  }

  @Test
  void unfiredRulesAreAbsent() {
    assertTrue(smallRun().ruleNodes().stream().noneMatch(node -> node.id().equals("R9")));
  }

  @Test
  void negatedPremiseRendersAnAbsenceBadge() {
    GraphModel model = smallRun();
    GraphEdge absence = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).findFirst().orElseThrow();
    GraphNode badge = model.nodeById(absence.fromId());

    assertEquals(NodeKind.ABSENCE, badge.kind());
    assertEquals("C absent", badge.title());
    assertEquals("R3", absence.toId());
  }

  @Test
  void layersFlowForwardWithoutOverlap() {
    GraphModel model = smallRun();
    List<GraphNode> nodes = model.nodes();

    for (GraphEdge edge : model.edges()) {
      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());

      assertTrue(target.x() >= source.x() || target.kind() == NodeKind.RULE || source.kind() == NodeKind.ABSENCE);
    }

    for (int left = 0; left < nodes.size(); left++)
      for (int right = left + 1; right < nodes.size(); right++) {
        GraphNode first = nodes.get(left);
        GraphNode second = nodes.get(right);
        boolean separated = first.x() + first.width() <= second.x() || second.x() + second.width() <= first.x()
            || first.y() + first.height() <= second.y() || second.y() + second.height() <= first.y();

        assertTrue(separated, "Nodes overlap: " + first.id() + " and " + second.id());
      }
  }

  @Test
  void routesStayOrthogonalInTheColumnGap() {
    GraphModel model = smallRun();

    for (GraphEdge edge : model.edges()) {
      if (edge.kind() == EdgeKind.ABSENCE)
        continue;

      GraphNode source = model.nodeById(edge.fromId());
      GraphNode target = model.nodeById(edge.toId());
      List<RoutePoint> route = TraceGraphView.routeEdge(edge, source, target);

      assertEquals(source.x() + source.width(), route.get(0).x());
      assertEquals(target.x(), route.get(route.size() - 1).x());

      for (int index = 1; index < route.size(); index++) {
        RoutePoint before = route.get(index - 1);
        RoutePoint point = route.get(index);

        assertTrue(before.x() == point.x() || before.y() == point.y());
      }
    }
  }

  @Test
  void traversalFindsUpstreamAndDownstream() {
    GraphModel model = smallRun();
    String badgeId = model.edges().stream().filter(edge -> edge.kind() == EdgeKind.ABSENCE).map(GraphEdge::fromId).findFirst().orElseThrow();

    assertEquals(Set.of("R3", "F1", badgeId), TraceGraphView.upstreamOf("F4", model));
    assertEquals(Set.of("R1", "R3", "F3", "F4"), TraceGraphView.downstreamOf("F1", model));
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=TraceGraphViewTest test`
Expected: FAIL — the new `TraceGraphView` does not exist.

- [ ] **Step 3: Move TraceGraphView into `ui.component`**

Run:

```bash
git mv src/main/java/com/ayyoub/inferenceengine/TraceGraphView.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/TraceGraphView.java
```

Then make these mechanical changes in the moved file:

1. Change the package to `com.ayyoub.inferenceengine.ui.component`.
2. Replace imports with `com.ayyoub.inferenceengine.domain.AbsenceWitness`, `...domain.Derivation`, `...domain.Fact`.
3. Delete the local color constants (`SURFACE`, `BORDER`, `PRIMARY`, `TXT1`, `TXT2`, `TXT3`, `RULE_BG`, `SUCCESS`) and use `DesignTokens.*` in their places. Map `RULE_BG` to `DesignTokens.RULE`.
4. Delete `import javafx.scene.control.Button;` so the file uses `com.ayyoub.inferenceengine.ui.component.Button`.
5. Replace every fact getter: `getFactId()` → `factId()`, `getAttribute()` → `attribute()`, `getOperator()` → `operator()`, `getValue()` → `value()`, `getDerivedBy()` → `derivedBy()`, `getLevel()` → `level()`.
6. Change the three `smallButton` construction sites (`zoomOut`, `zoomLabel` is not a button, `zoomIn`, `reset`) to `new Button(text, Button.Variant.SECONDARY)` and delete the `smallButton` helper.
7. Keep `NodeKind`, `EdgeKind`, `GraphNode`, `GraphEdge`, `RoutePoint`, `GraphModel`, `layout`, `routeEdge`, `upstreamOf`, `downstreamOf`, `build`, and `EdgeView` unchanged otherwise.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=TraceGraphViewTest test`
Expected: PASS, 6 tests.

- [ ] **Step 5: Create TopBar**

```java
package com.ayyoub.inferenceengine.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Page header with app name, breadcrumb, and an optional back button.
 */
public final class TopBar extends HBox {

  /**
   * Builds the bar.
   */
  public TopBar(boolean resultsMode, Runnable onBack) {
    super(14);
    setPadding(new Insets(17, 40, 17, 40));
    setAlignment(Pos.CENTER_LEFT);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: transparent transparent " + DesignTokens.BORDER
        + " transparent;" + "-fx-border-width: 0 0 1 0;");

    if (resultsMode) {
      Button back = new Button("← Retour", Button.Variant.SECONDARY);
      back.setOnAction(event -> onBack.run());
      getChildren().addAll(back, DesignTokens.spacer(16));
    }

    Circle dot = new Circle(5, Color.web(DesignTokens.PRIMARY));

    Label appName = new Label("Moteur d'Inférence");
    appName.setFont(Font.font("System", FontWeight.BOLD, 15));
    appName.setTextFill(Color.web(DesignTokens.TXT1));

    Label breadcrumb = DesignTokens.label(resultsMode ? "/ Trace de raisonnement" : "/ Configuration des faits", 14, DesignTokens.TXT3);

    getChildren().addAll(dot, appName, breadcrumb);
  }
}
```

- [ ] **Step 6: Create StatsBar**

```java
package com.ayyoub.inferenceengine.ui.component;

import com.ayyoub.inferenceengine.domain.Fact;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Summary counts above the trace graph.
 */
public final class StatsBar extends HBox {

  /**
   * Builds the bar from one run's facts.
   */
  public StatsBar(List<Fact> facts) {
    super(0);
    setStyle("-fx-background-color: " + DesignTokens.SURFACE + "; -fx-border-color: " + DesignTokens.BORDER + ";"
        + "-fx-border-radius: 12; -fx-background-radius: 12;");
    setAlignment(Pos.CENTER);

    long derived = facts.stream().filter(fact -> fact.derivedBy() != null).count();
    long initial = facts.size() - derived;
    long levels = facts.stream().mapToInt(Fact::level).distinct().count();
    boolean alert = facts.stream().anyMatch(fact -> fact.attribute().startsWith("alerte") || fact.attribute().equals("conseil"));

    getChildren().addAll(
        statCell(String.valueOf(facts.size()), "Faits totaux", DesignTokens.PRIMARY), divider(),
        statCell(String.valueOf(initial), "Initiaux", DesignTokens.TXT2), divider(),
        statCell(String.valueOf(derived), "Déduits", DesignTokens.SUCCESS), divider(),
        statCell(String.valueOf(levels), "Niveaux", DesignTokens.RULE), divider(),
        statCell(alert ? "Oui" : "Non", "Alerte", alert ? DesignTokens.WARN : DesignTokens.TXT3));
  }

  private static VBox statCell(String value, String label, String color) {
    VBox cell = new VBox(4);
    cell.setAlignment(Pos.CENTER);
    cell.setPadding(new Insets(18, 32, 18, 32));
    HBox.setHgrow(cell, Priority.ALWAYS);

    Label number = new Label(value);
    number.setFont(Font.font("System", FontWeight.BOLD, 22));
    number.setTextFill(Color.web(color));

    Label caption = new Label(label);
    caption.setFont(Font.font("System", 11));
    caption.setTextFill(Color.web(DesignTokens.TXT3));

    cell.getChildren().addAll(number, caption);
    return cell;
  }

  private static Region divider() {
    Region divider = new Region();
    divider.setPrefWidth(1);
    divider.setMinHeight(50);
    divider.setStyle("-fx-background-color: " + DesignTokens.BORDER + ";");
    return divider;
  }
}
```

- [ ] **Step 7: Run the full test suite and compile**

Run: `./mvnw -q test`
Expected: PASS (all tests so far, including TraceGraphViewTest).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/ui/component/TopBar.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/StatsBar.java \
  src/main/java/com/ayyoub/inferenceengine/ui/component/TraceGraphView.java \
  src/test/java/com/ayyoub/inferenceengine/ui/component/TraceGraphViewTest.java
git commit -m "feat(ui): add header, stats, and trace graph components"
```

---

### Task 11: EngineUI

**Files:**
- Create: `src/main/java/com/ayyoub/inferenceengine/ui/EngineUI.java`

**Interfaces:**
- Consumes: `InferenceService`, `FactInput`, `InferenceResult`, every UI component.
- Produces: `EngineUI(InferenceService)` and `show(Stage)`.

- [ ] **Step 1: Implement EngineUI**

```java
package com.ayyoub.inferenceengine.ui;

import com.ayyoub.inferenceengine.domain.Fact;
import com.ayyoub.inferenceengine.service.FactInput;
import com.ayyoub.inferenceengine.service.InferenceResult;
import com.ayyoub.inferenceengine.service.InferenceService;
import com.ayyoub.inferenceengine.ui.component.Button;
import com.ayyoub.inferenceengine.ui.component.CataloguePanel;
import com.ayyoub.inferenceengine.ui.component.DesignTokens;
import com.ayyoub.inferenceengine.ui.component.FactRow;
import com.ayyoub.inferenceengine.ui.component.SelectedFactsPanel;
import com.ayyoub.inferenceengine.ui.component.StatsBar;
import com.ayyoub.inferenceengine.ui.component.TopBar;
import com.ayyoub.inferenceengine.ui.component.TraceGraphView;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Two-page UI: fact builder and reasoning trace.
 */
public final class EngineUI {

  private final InferenceService service;
  private final LinkedHashMap<String, Fact> selected = new LinkedHashMap<>();
  private final Map<String, Object> values = new LinkedHashMap<>();
  private final Map<String, FactRow> rows = new LinkedHashMap<>();

  private Stage stage;
  private CataloguePanel cataloguePanel;
  private SelectedFactsPanel selectedPanel;
  private Button runButton;
  private int catalogueSize;

  /**
   * Creates the UI with its use-case dependency.
   */
  @Inject
  public EngineUI(InferenceService service) {
    this.service = service;
  }

  /**
   * Configures the stage and shows the fact builder.
   */
  public void show(Stage stage) {
    this.stage = stage;
    this.catalogueSize = service.factCatalogue().size();
    stage.setTitle("Moteur d'Inférence");
    stage.setMinWidth(900);
    stage.setMinHeight(560);
    stage.setResizable(true);
    showInputPage();
    stage.show();
  }

  /**
   * Rebuilds the fact builder from scratch.
   */
  private void showInputPage() {
    selected.clear();
    values.clear();
    rows.clear();

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + DesignTokens.BG + ";");
    root.setTop(new TopBar(false, () -> {
    }));

    cataloguePanel = new CataloguePanel(service.factCatalogue(), this::addFact);
    cataloguePanel.setPrefWidth(300);
    cataloguePanel.setMinWidth(260);
    cataloguePanel.setMaxWidth(340);

    selectedPanel = new SelectedFactsPanel();
    HBox.setHgrow(selectedPanel, Priority.ALWAYS);

    HBox body = new HBox(0, cataloguePanel, selectedPanel);
    root.setCenter(body);
    root.setBottom(buildFooter());

    stage.setScene(new Scene(root, 960, 660));
  }

  /**
   * Adds one catalogue fact and its editor.
   */
  private void addFact(Fact template) {
    String attribute = template.attribute();

    if (selected.containsKey(attribute))
      return;

    selected.put(attribute, template);
    values.put(attribute, template.value());

    FactRow row = new FactRow(template, service.valueOptions(attribute), value -> values.put(attribute, value),
        () -> removeFact(attribute));

    rows.put(attribute, row);
    selectedPanel.addRow(row);
    selectedPanel.setCount(selected.size());
    cataloguePanel.markUsed(attribute, true);
    runButton.setDisable(false);
  }

  /**
   * Removes one selected fact and re-enables its catalogue entry.
   */
  private void removeFact(String attribute) {
    selected.remove(attribute);
    values.remove(attribute);

    FactRow row = rows.remove(attribute);

    if (row != null)
      selectedPanel.removeRow(row);

    selectedPanel.setCount(selected.size());
    cataloguePanel.markUsed(attribute, false);
    runButton.setDisable(selected.isEmpty());
  }

  /**
   * Footer with the catalogue count and the run button.
   */
  private HBox buildFooter() {
    HBox footer = new HBox();
    footer.setPadding(new Insets(13, 32, 13, 32));
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setStyle("-fx-background-color: " + DesignTokens.SURFACE + ";" + "-fx-border-color: " + DesignTokens.BORDER
        + " transparent transparent transparent;" + "-fx-border-width: 1 0 0 0;");

    HBox hint = new HBox(DesignTokens.label(catalogueSize + " attribut(s) disponibles dans facts.json", 12, DesignTokens.TXT3));
    HBox.setHgrow(hint, Priority.ALWAYS);

    runButton = new Button("Lancer le raisonnement  →", Button.Variant.PRIMARY);
    runButton.setDisable(true);
    runButton.setOnAction(event -> runAndShowResults());

    footer.getChildren().addAll(hint, runButton);
    return footer;
  }

  /**
   * Runs inference over the selected values and shows the trace page.
   */
  private void runAndShowResults() {
    List<FactInput> inputs = selected.keySet().stream().map(attribute -> new FactInput(attribute, values.get(attribute))).toList();

    showResultsPage(service.run(inputs));
  }

  /**
   * Renders the trace page for one result.
   */
  private void showResultsPage(InferenceResult result) {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + DesignTokens.BG + ";");
    root.setTop(new TopBar(true, this::showInputPage));

    VBox page = new VBox(20);
    page.setPadding(new Insets(28, 36, 32, 36));
    page.getChildren().add(new StatsBar(result.facts()));

    VBox graph = new TraceGraphView(result.facts(), result.derivations()).build();
    VBox.setVgrow(graph, Priority.ALWAYS);
    page.getChildren().add(graph);

    root.setCenter(page);
    stage.setScene(new Scene(root, 960, 660));
  }
}
```

- [ ] **Step 2: Compile to verify**

Run: `./mvnw -q compile -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ayyoub/inferenceengine/ui/EngineUI.java
git commit -m "feat(ui): compose two-page EngineUI"
```

---

### Task 12: Composition root and legacy removal

**Files:**
- Modify: `src/main/java/com/ayyoub/inferenceengine/Launcher.java` (replace)
- Modify: `src/main/java/com/ayyoub/inferenceengine/config/AppModule.java` (replace)
- Modify: `pom.xml` (remove Lombok)
- Delete: `src/main/java/com/ayyoub/inferenceengine/Fact.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/Rule.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/FactsReader.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/RulesReader.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/KnowledgeBase.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/WorkingMemory.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/InferenceEngine.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/TraceGraphView.java` (already moved in Task 10)
- Delete: `src/main/java/com/ayyoub/inferenceengine/InferenceEngineUI.java`
- Delete: `src/main/java/com/ayyoub/inferenceengine/engine/Derivation.java`
- Create: `src/test/java/com/ayyoub/inferenceengine/config/AppModuleTest.java`

**Interfaces:**
- Consumes: every new class.
- Produces: working application entry point and injector.

- [ ] **Step 1: Write the failing wiring test**

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=AppModuleTest test`
Expected: FAIL — `AppModule` has no bindings, so Guice cannot construct `InferenceServiceImpl`.

- [ ] **Step 3: Implement AppModule**

```java
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
```

- [ ] **Step 4: Replace Launcher**

```java
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
```

- [ ] **Step 5: Delete the legacy classes**

Run:

```bash
git rm src/main/java/com/ayyoub/inferenceengine/Fact.java \
  src/main/java/com/ayyoub/inferenceengine/Rule.java \
  src/main/java/com/ayyoub/inferenceengine/FactsReader.java \
  src/main/java/com/ayyoub/inferenceengine/RulesReader.java \
  src/main/java/com/ayyoub/inferenceengine/KnowledgeBase.java \
  src/main/java/com/ayyoub/inferenceengine/WorkingMemory.java \
  src/main/java/com/ayyoub/inferenceengine/InferenceEngine.java \
  src/main/java/com/ayyoub/inferenceengine/InferenceEngineUI.java \
  src/main/java/com/ayyoub/inferenceengine/engine/Derivation.java
```

Do not delete `src/main/java/com/ayyoub/inferenceengine/engine/ConditionEvaluator.java`; Task 2 already replaced it with the new implementation.

Confirm `git status --short` shows only the new tree under `src/main/java/com/ayyoub/inferenceengine/` and no old root classes.

- [ ] **Step 6: Remove Lombok from pom.xml**

Delete the Lombok dependency block:

```xml
<!-- Lombok: generates getters, setters and builders at compile time -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.32</version>
    <scope>provided</scope>
</dependency>
```

Delete the `annotationProcessorPaths` block inside `maven-compiler-plugin`, leaving only `source` and `target`.

- [ ] **Step 7: Run the wiring test and the full suite**

Run: `./mvnw -q test`
Expected: PASS, including `AppModuleTest`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/ayyoub/inferenceengine/Launcher.java \
  src/main/java/com/ayyoub/inferenceengine/config/AppModule.java \
  src/test/java/com/ayyoub/inferenceengine/config/AppModuleTest.java
git commit -m "refactor: wire application through Guice and remove legacy classes"
```

---

### Task 13: Documentation, formatting, and final verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `src/main/java` and `src/test/java` formatting via Spotless

**Interfaces:**
- Consumes: the finished tree.
- Produces: green build and accurate project documentation.

- [ ] **Step 1: Update AGENTS.md**

Replace the "Live model lives…" bullet with:

```
- Live model lives under `src/main/java/com/ayyoub/inferenceengine/`: `domain/` (immutable records), `engine/` (evaluator, engine, working memory, knowledge base), `json/` (JSON port), `service/` (use-case facade), `ui/` + `ui/component/` (JavaFX), and `config/AppModule` (Guice composition root). No JavaFX outside `ui`; no Jackson outside `json`.
```

Replace the entire "Tests are broken — known state" section with:

```
## Tests

- Run `./mvnw test`. The suite covers the evaluator, engine, knowledge base, working memory, JSON parsing, service facade, trace layout, and Guice wiring.
- UI components require a JavaFX toolkit, so they are verified by the manual `./mvnw javafx:run` smoke test, not unit tests.
```

Replace the runtime gotchas bullet about relative paths with:

```
- JSON resources load from the classpath (`/rules.json`, `/facts.json`) through `JsonParser`; loading failures raise `JsonParseException` and show an error dialog at startup.
```

Add to the style section, unchanged:

```
- `Rule.priority` is the conflict-resolution order (highest first); `KnowledgeBase` sorts rules once on construction.
```

- [ ] **Step 2: Apply formatting**

Run: `./mvnw spotless:apply`
Expected: files reformatted; no errors.

- [ ] **Step 3: Run the full verification**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, with all tests passing and `spotless:check` clean.

- [ ] **Step 4: Inspect layering**

Run:

```bash
grep -rn "javafx" src/main/java/com/ayyoub/inferenceengine --include='*.java' | grep -v '/ui/' | grep -v '/Launcher.java'
grep -rn "com.fasterxml.jackson" src/main/java/com/ayyoub/inferenceengine --include='*.java' | grep -v '/json/'
grep -rn "src/main/resources" src/main/java --include='*.java'
grep -rn "new KnowledgeBase\|new InferenceEngine\|new WorkingMemory" src/main/java/com/ayyoub/inferenceengine/ui
```

Expected: no output from all four commands.

- [ ] **Step 5: Manual smoke test**

Run: `./mvnw javafx:run`
Expected: the fact builder opens; add `A` and `B`; run; the trace page shows 2 initial facts, derived facts, and rule nodes; back resets the builder.

- [ ] **Step 6: Commit**

```bash
git add AGENTS.md src/main/java src/test/java
git commit -m "docs: describe layered architecture and verify"
```
