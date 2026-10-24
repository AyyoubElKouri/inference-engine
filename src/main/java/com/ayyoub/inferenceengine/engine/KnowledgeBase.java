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
