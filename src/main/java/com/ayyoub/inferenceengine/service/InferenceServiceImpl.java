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
  public InferenceServiceImpl(JsonParser parser, @Named("factsResource") String factsResource, KnowledgeBase knowledgeBase, InferenceEngine engine) {
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
    // No asserted facts means no inference session and no vacuous absence matches.
    if (inputs.isEmpty())
      return new InferenceResult(List.of(), List.of());

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
