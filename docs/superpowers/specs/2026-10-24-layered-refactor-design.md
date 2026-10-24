# Layered Refactor Design

Date: 2026-10-24
Status: awaiting review
Scope: Restructure the JavaFX inference engine into layered packages with a service facade, a JSON port, dependency injection, and a reusable UI component library. Behavior changes are limited to those listed in section 9.

## 1. Goal

Replace the current god-class UI and hidden dependencies with an architecture that is readable, testable, and easy to maintain. The UI must depend on one interface, the reasoning core must stay free of JavaFX, and every external concern must sit behind an adapter.

## 2. Current problems

- `InferenceEngineUI` mixes page layout, component styling, Guice setup, JSON loading, and reasoning orchestration in 704 lines.
- `KnowledgeBase` loads `rules.json` in its constructor through a relative filesystem path and swallows failures, so a wrong working directory silently yields zero rules.
- `RulesReader` and `FactsReader` are static helpers with hardcoded paths.
- `WorkingMemory` instantiates its own `ConditionEvaluator`, duplicating comparison logic.
- `AppModule` is empty, while the UI calls `new KnowledgeBase()` and `new InferenceEngine(...)` directly.
- Domain, engine, and UI share one package, so JavaFX leaks into everything.

## 3. Target architecture

```
Launcher ──► EngineUI ──► InferenceService ◄── InferenceServiceImpl
                │                                   │
                ├──► UI components                  ├──► InferenceEngine ──► WorkingMemory
                │                                   │         └───────────► KnowledgeBase
                │                                   ├──► WorkingMemory
                │                                   ├──► KnowledgeBase
                │                                   └──► JsonParser ◄── JsonParserImpl
                └──► domain records
```

Dependency direction:

- `ui` depends on `service` and `domain`.
- `service` depends on `engine`, `json`, and `domain`.
- `engine` and `json` depend on `domain`.
- `config` may depend on every layer.

Hard rules:

- No `javafx` import outside `ui`, except `Launcher` (the JavaFX entry point).
- No Jackson import outside `json`.
- No file or classpath I/O outside `json`.
- No constructor reads files except `JsonParserImpl` and `InferenceServiceImpl`.

## 4. Packages

```
com.ayyoub.inferenceengine
├── Launcher.java
├── config/AppModule.java
├── domain/
│   ├── Fact.java
│   ├── Rule.java
│   ├── Derivation.java
│   └── AbsenceWitness.java
├── engine/
│   ├── ConditionEvaluator.java
│   ├── InferenceEngine.java
│   ├── WorkingMemory.java
│   └── KnowledgeBase.java
├── json/
│   ├── JsonParser.java
│   ├── JsonParserImpl.java
│   └── JsonParseException.java
├── service/
│   ├── InferenceService.java
│   ├── InferenceServiceImpl.java
│   ├── FactInput.java
│   └── InferenceResult.java
└── ui/
    ├── EngineUI.java
    └── component/
        ├── DesignTokens.java
        ├── Button.java
        ├── Input.java
        ├── BooleanInput.java
        ├── IntegerInput.java
        ├── DecimalInput.java
        ├── TextInput.java
        ├── ChoiceInput.java
        ├── TypeBadge.java
        ├── TopBar.java
        ├── CataloguePanel.java
        ├── CatalogueRow.java
        ├── SelectedFactsPanel.java
        ├── FactRow.java
        ├── StatsBar.java
        ├── TraceGraphView.java
        └── Dialogs.java
```

## 5. Domain records

```java
public record Fact(String factId, String attribute, String operator,
                   Object value, double confidence, String derivedBy, int level) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record Rule(@JsonProperty("id") String ruleId, String name,
                   Map<String, Object> condition, List<Map<String, Object>> conclusions,
                   int priority, double confidence, String explanation) {}

public record Derivation(String ruleId, String conclusionId,
                         List<String> premiseIds, List<AbsenceWitness> absences) {}

public record AbsenceWitness(String attribute, String operator, Object value) {
  public String label() {
    return attribute + " absent";
  }
}
```

`Rule` keeps explicit `@JsonProperty` annotations so Jackson deserializes records without relying on parameter-name metadata.

## 6. Engine and JSON contracts

### JsonParser

```java
public interface JsonParser {
  List<Rule> parseRules(String classpathResource);
  List<Fact> parseFacts(String classpathResource);
}
```

`JsonParserImpl` reads the resource from the classpath with the receiver's class loader; the argument is a leading-slash resource name such as `/rules.json`. `parseRules` unwraps `knowledge_base.rules`. `parseFacts` reads a top-level array and infers each value as boolean, int, long (narrowed to int), double or float (widened to double), or string. Both methods throw `JsonParseException` with the resource name and cause.

### KnowledgeBase

```java
public final class KnowledgeBase {
  public KnowledgeBase(List<Rule> rules); // stable sort, descending priority
  public List<Rule> rules();
}
```

The class is passive and read-only. It has no file access and no mutation methods.

### WorkingMemory

```java
public final class WorkingMemory {
  public void addFact(Fact fact);
  public List<Fact> facts();                  // unmodifiable view
  public void addDerivation(Derivation derivation);
  public List<Derivation> derivations();      // unmodifiable view
  public int currentLevel();
}
```

`WorkingMemory` stores data only. The duplicate-conclusion check moves to `InferenceEngine`, which delegates comparison to `ConditionEvaluator`.

### InferenceEngine

```java
public final class InferenceEngine {
  public InferenceEngine(KnowledgeBase knowledgeBase, ConditionEvaluator evaluator);
  public void run(WorkingMemory memory);
}
```

`run` saturates the rule set. It preserves the current fact-ID allocation (`F1..Fn` past the highest seed ID), level definition (deepest consulted fact level plus one), duplicate suppression, confidence propagation, and provenance capture. Rule order comes from the priority-sorted knowledge base.

## 7. Service contract

```java
public interface InferenceService {
  List<Fact> factCatalogue();
  List<String> valueOptions(String attribute);
  InferenceResult run(List<FactInput> inputs);
}

public record FactInput(String attribute, Object value) {}
public record InferenceResult(List<Fact> facts, List<Derivation> derivations) {}
```

- `factCatalogue` returns the templates parsed from `facts.json`.
- `valueOptions` returns the initial string value plus every string conclusion value for that attribute, in encounter order and without duplicates. This preserves the current combo-box behavior without exposing rules to the UI.
- `run` creates a fresh `WorkingMemory`, assigns IDs `F1..Fn` in input order with operator `"="`, the template confidence, and level 0, then runs the engine and returns an immutable result.

`InferenceServiceImpl` receives `JsonParser`, `@Named("factsResource") String`, `KnowledgeBase`, and `InferenceEngine`. It loads the catalogue eagerly and fails fast.

## 8. UI design

`EngineUI` becomes a plain class of about 150 lines. It holds the service, the stage, and the input-page state (`LinkedHashMap<String, Fact> selected`, `Map<String, Input> inputs`). It composes components and coordinates add, remove, run, and back. It contains no styling, engine calls, or file loading.

Component responsibilities:

| Component | Responsibility |
|---|---|
| `DesignTokens` | Colors, spacing, and font sizes shared by all components. |
| `Button` | Extends `javafx.scene.control.Button`; variants PRIMARY, SECONDARY, GHOST, DANGER with hover and disabled styling. |
| `Input` | Interface `{ Node node(); Object value(); }` plus `forFact(...)` factory. |
| `BooleanInput` | Animated toggle. |
| `IntegerInput` | Editable spinner. |
| `DecimalInput` | Validated text field. |
| `TextInput`, `ChoiceInput` | Text field, or combo box when more than one option exists. |
| `TypeBadge` | Value-type badge. |
| `TopBar` | Title, breadcrumb, optional back button. |
| `CataloguePanel`, `CatalogueRow` | Left panel; a used row dims and becomes non-clickable. |
| `SelectedFactsPanel`, `FactRow` | Right panel; attribute, badge, remove button, and input. |
| `StatsBar` | Totals, initial, derived, levels, alert. |
| `TraceGraphView` | Moved unchanged; keeps the pure `layout`, `routeEdge`, `upstreamOf`, and `downstreamOf` API. |
| `Dialogs` | Error dialog used by `Launcher`. |

Page flow:

1. `Launcher.start` builds the injector, obtaining `EngineUI` and calling `show(stage)`.
2. `EngineUI` requests the catalogue once and renders both panels.
3. Adding a fact stores the template and initial value, asks for value options, creates a `FactRow`, and marks the catalogue row used.
4. Removing a fact reverses those steps.
5. Running builds `List<FactInput>` in insertion order and renders the results page.
6. Back returns to a fresh input page, matching current reset behavior.

`Button` shadows `javafx.scene.control.Button` inside `ui`. Classes that extend JavaFX controls fully qualify the JavaFX type; callers import the component.

## 9. Dependency injection

`AppModule` is the only composition root:

- Bind `JsonParser` to `JsonParserImpl` as a singleton.
- Bind `ConditionEvaluator` and `InferenceEngine` as singletons.
- Provide `KnowledgeBase` from `JsonParser` and `@Named("rulesResource")`.
- Bind `InferenceService` to `InferenceServiceImpl` as a singleton.
- Bind the classpath paths: `rulesResource = "/rules.json"`, `factsResource = "/facts.json"`.
- Bind `EngineUI`.

`Launcher` extends `Application`, creates the injector in `start`, and obtains `EngineUI` from it. No static injector and no service locator. Implementations annotate their constructors with `@Inject`.

## 10. Error handling

- Missing, unreadable, or malformed JSON raises `JsonParseException` with the resource name and cause.
- `Launcher` catches `JsonParseException` and Guice `ConfigurationException`, shows a `Dialogs` error alert, and exits.
- The application never runs with a silent empty catalogue.

## 11. Testing

All current tests are deleted. New tests follow red-green-refactor:

- `ConditionEvaluatorTest`: operators, cross-type numeric comparison, connectives, quantifiers, provenance, absence witnesses.
- `InferenceEngineTest`: fixed point, derivations, levels, duplicate suppression, descending priority order.
- `KnowledgeBaseTest`: stable priority sort and read-only rules.
- `JsonParserTest`: rules wrapper, fact type inference, missing resource, malformed JSON.
- `InferenceServiceTest`: catalogue, value options, ID assignment, fresh memory per run, immutable result.
- `TraceGraphViewTest`: layout, routing, upstream and downstream traversal on the headless model.

UI components are not unit-tested because they require a JavaFX toolkit. They are verified with a manual `./mvnw javafx:run` smoke test.

## 12. Behavior changes

The refactor keeps the two-page UI and the reasoning model, with these deliberate changes:

1. Rules fire in descending numeric priority order (170 before 10), sorted once and stably. Fact IDs, levels, and derivation order may change.
2. Loading fails fast instead of falling back to empty rules or facts.
3. Resources load from the classpath instead of `src/main/resources/...` relative paths.
4. `Fact`, `Rule`, and `Derivation` become immutable records; Lombok is removed.
5. Method names use domain language, for example `forwardChaining()` becomes `run()`.

## 13. Out of scope

- The abandoned Spring Boot, React, and Postgres microservices design in `docs/superpowers/specs/2026-09-30-inference-microservices-design.md`.
- New reasoning features, persistence, authentication, or networking.
- Changes to `rules.json` or `facts.json` content.

## 14. Acceptance criteria

- The layering rules in section 3 hold, verified by import inspection.
- `AppModule` is the only composition root and all dependencies use constructor injection.
- Every class has one responsibility and can be understood without reading its collaborators' internals.
- `./mvnw verify` passes: compile, new tests, and Spotless.
- The manual smoke test shows both pages and the trace graph.
- `AGENTS.md` describes the new layout, contracts, and commands.
