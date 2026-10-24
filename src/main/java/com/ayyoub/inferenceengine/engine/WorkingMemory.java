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
