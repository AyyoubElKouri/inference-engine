package com.ayyoub.inferenceengine;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeBase {

  /**
   * List of rules in the knowledge base.
   */
  private List<Rule> rules;

  /**
   * Constructor — loads rules from rules.json. Si le fichier est illisible, initialise rules à une liste vide pour éviter tout NullPointerException
   * en aval.
   */
  public KnowledgeBase() {
    try {
      this.rules = RulesReader.readRules("src/main/resources/rules.json");
    } catch (Exception e) {
      System.err.println("[KnowledgeBase] Erreur de chargement des règles : " + e.getMessage());
      this.rules = new ArrayList<>(); // ← jamais null
    }
  }

  /**
   * Getter
   */
  public List<Rule> getRules() {
    return rules;
  }

  /**
   * Add a new rule to the knowledge base.
   */
  public void addRule(Rule rule) {
    this.rules.add(rule);
  }

  /**
   * Remove a rule by its ID.
   */
  public void removeRuleById(String ruleId) {
    rules.removeIf(rule -> rule.getRuleId().equals(ruleId));
  }

  /**
   * Find a rule by its ID.
   */
  public Rule getRuleById(String ruleId) {
    return rules.stream().filter(rule -> rule.getRuleId().equals(ruleId)).findFirst().orElse(null);
  }
}
