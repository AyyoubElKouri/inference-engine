package com.ayyoub.inferenceengine.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbsenceWitnessTest {

  @Test
  void labelNamesTheMissingAttribute() {
    assertEquals("C absent", new AbsenceWitness("C", "=", true).label());
  }
}
