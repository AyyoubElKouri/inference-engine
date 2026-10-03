package com.ayyoub.inferenceengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class RulesReader {
  /**
   * Read the rules from a JSON file and return them as a list of Rule objects.
   *
   * @param filePath The path to the JSON file containing the rules.
   * @return The list of rules read from the file.
   * @throws IOException If an error occurs while reading the file.
   */
  public static List<Rule> readRules(String filePath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    String rulesJson =
        mapper.readTree(new File(filePath)).path("knowledge_base").path("rules").toString();

    return mapper.readValue(
        rulesJson, mapper.getTypeFactory().constructCollectionType(List.class, Rule.class));
  }
}
