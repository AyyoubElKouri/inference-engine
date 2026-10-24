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
