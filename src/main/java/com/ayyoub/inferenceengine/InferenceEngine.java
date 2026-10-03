package com.ayyoub.inferenceengine;

import java.util.List;
import java.util.Map;

public class InferenceEngine {

  private final WorkingMemory workingMemory;
  private final KnowledgeBase knowledgeBase;

  public InferenceEngine(WorkingMemory workingMemory, KnowledgeBase knowledgeBase) {
    this.workingMemory = workingMemory;
    this.knowledgeBase = knowledgeBase;
  }

  public void forwardChaining() {
    List<Rule> rules = knowledgeBase.getRules();
    int counter = workingMemory.getFacts().size();
    boolean factsChanged = true;

    // Keep applying rules until a complete pass adds no new facts. This
    // reaches the fixed point needed for conclusions derived in stages.
    while (factsChanged) {
      factsChanged = false;
      for (Rule rule : rules) {
        if (evaluate(rule.getCondition(), workingMemory.getFacts())) {
          for (Map<String, Object> conclusion : rule.getConclusions()) {
            String attribute = (String) conclusion.get("attribute");
            String operator = (String) conclusion.get("operator");
            Object value = conclusion.get("value");

            if (!workingMemory.factExists(attribute, operator, value)) {
              // A fact's level is one greater than the deepest
              // fact used by the rule that produced it.
              int newLevel = computeLevel(rule.getCondition(), workingMemory.getFacts()) + 1;

              workingMemory.addFact(
                  new Fact(
                      "F" + counter++,
                      attribute,
                      operator,
                      value,
                      rule.getConfidence(),
                      rule.getRuleId(),
                      newLevel));
              factsChanged = true;
            }
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private boolean evaluate(Map<String, Object> condition, List<Fact> facts) {
    // A condition is represented as a JSON object. An object with an
    // attribute is a leaf; the other keys describe boolean/n-ary nodes.
    if (condition.containsKey("attribute")) {
      String attr = (String) condition.get("attribute");
      String op = (String) condition.get("operator");
      Object val = condition.get("value");
      return facts.stream()
          .anyMatch(f -> f.getAttribute().equals(attr) && compareValues(f.getValue(), op, val));
    }

    if (condition.containsKey("all"))
      return evaluateAll((List<Map<String, Object>>) condition.get("all"), facts);
    if (condition.containsKey("any"))
      return evaluateAny((List<Map<String, Object>>) condition.get("any"), facts);
    if (condition.containsKey("not"))
      return !evaluate((Map<String, Object>) condition.get("not"), facts);
    if (condition.containsKey("xor"))
      return evaluateXOR((List<Map<String, Object>>) condition.get("xor"), facts);
    if (condition.containsKey("at_least"))
      return evaluateAtLeast(
          (int) condition.get("at_least"), (List<Map<String, Object>>) condition.get("of"), facts);
    if (condition.containsKey("exactly"))
      return evaluateExactly(
          (int) condition.get("exactly"), (List<Map<String, Object>>) condition.get("of"), facts);
    if (condition.containsKey("at_most"))
      return evaluateAtMost(
          (int) condition.get("at_most"), (List<Map<String, Object>>) condition.get("of"), facts);

    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean compareValues(Object real, String operator, Object expected) {
    if (real == null || expected == null) return false;
    // Values are read from JSON, so both equality and ordering must use
    // the runtime value types rather than converting everything to text.
    return switch (operator) {
      case "=" -> real.equals(expected);
      case "!=" -> !real.equals(expected);
      case ">" -> ((Comparable<Object>) real).compareTo(expected) > 0;
      case "<" -> ((Comparable<Object>) real).compareTo(expected) < 0;
      case ">=" -> ((Comparable<Object>) real).compareTo(expected) >= 0;
      case "<=" -> ((Comparable<Object>) real).compareTo(expected) <= 0;
      default -> throw new IllegalArgumentException("Unknown operator: " + operator);
    };
  }

  private boolean evaluateAll(List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().allMatch(c -> evaluate(c, facts));
  }

  private boolean evaluateAny(List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().anyMatch(c -> evaluate(c, facts));
  }

  private boolean evaluateXOR(List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().filter(c -> evaluate(c, facts)).count() == 1;
  }

  private boolean evaluateAtLeast(int n, List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().filter(c -> evaluate(c, facts)).count() >= n;
  }

  private boolean evaluateExactly(int n, List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().filter(c -> evaluate(c, facts)).count() == n;
  }

  private boolean evaluateAtMost(int n, List<Map<String, Object>> conds, List<Fact> facts) {
    return conds.stream().filter(c -> evaluate(c, facts)).count() <= n;
  }

  private int computeLevel(Map<String, Object> condition, List<Fact> facts) {
    if (condition.containsKey("attribute")) {
      String attr = (String) condition.get("attribute");
      // For compound conditions, using the maximum matching level
      // preserves the dependency depth of the derived conclusion.
      return facts.stream()
          .filter(f -> f.getAttribute().equals(attr))
          .mapToInt(Fact::getLevel)
          .max()
          .orElse(0);
    }

    return getSubConditions(condition).stream()
        .mapToInt(c -> computeLevel(c, facts))
        .max()
        .orElse(0);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getSubConditions(Map<String, Object> condition) {
    if (condition.containsKey("all")) return (List<Map<String, Object>>) condition.get("all");
    if (condition.containsKey("any")) return (List<Map<String, Object>>) condition.get("any");
    if (condition.containsKey("xor")) return (List<Map<String, Object>>) condition.get("xor");
    if (condition.containsKey("of")) return (List<Map<String, Object>>) condition.get("of");
    if (condition.containsKey("not")) return List.of((Map<String, Object>) condition.get("not"));
    return List.of();
  }
}
