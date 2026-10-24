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
