# AGENTS.md

## Commands (use `./mvnw`, never `mvn`)

- `./mvnw -q compile -DskipTests` — fast check.
- `./mvnw spotless:apply` — format before committing; `verify` runs `spotless:check` and fails on format drift.
- `./mvnw verify` — build, run tests, and check formatting.
- UI: `./mvnw javafx:run` (main class `com.ayyoub.inferenceengine.Launcher`). Headless/CI work must not launch JavaFX.

## Do not trust README/HELP blindly

- `README.md` lists `./mvnw spring-boot:run` — wrong, there is no Spring dependency in `pom.xml`.
- `HELP.md` is the default Spring template — irrelevant to this repo.

## Source layout (critical)

- Maven compiles only `src/main/java`. Never add new source roots at repo top level.
- Live model lives under `src/main/java/com/ayyoub/inferenceengine/`: `domain/` (immutable records), `engine/` (evaluator, engine, working memory, knowledge base), `json/` (JSON port), `service/` (use-case facade), `ui/` + `ui/component/` (JavaFX), and `config/AppModule` (Guice composition root). No JavaFX outside `ui`; no Jackson outside `json`.

## Tests

- Run `./mvnw test`. The suite covers the evaluator, engine, knowledge base, working memory, JSON parsing, service facade, trace layout, and Guice wiring.
- UI components require a JavaFX toolkit, so they are verified by the manual `./mvnw javafx:run` smoke test, not unit tests.

## Runtime gotchas

- JSON resources load from the classpath (`/rules.json`, `/facts.json`) through `JsonParser`; loading failures raise `JsonParseException` and show an error dialog at startup.
- `facts.json` is a top-level JSON array; `rules.json` nests under `knowledge_base.rules`. Value types are inferred (boolean/int/double/string) — numeric comparisons must stay Number-safe (`Double.compare`), never raw `(Comparable)` casts across int/double.
- Condition DSL keys: leaf `attribute/operator/value`, groups `all/any/not/xor`, quantifiers `at_least/exactly/at_most` + `of`.

## Style — CRITICAL: apply before writing any code

- `Rule.priority` is the conflict-resolution order (highest first); `KnowledgeBase` sorts rules once on construction.
- Formatter is `eclipse-formatter.xml` via Spotless (2-space, 150-col), not google-java-format.
- CRITICAL: Group code into logical blocks, one step or intention each. Separate blocks with exactly one blank line. No exceptions.
- CRITICAL: Javadoc is always multi-line, never `/** one-liner */`. The formatter cannot enforce this (stock JDT has no always-expand switch — the boundary flags only preserve the shape), so every new comment must be written expanded:
  ```java
  /**
   * ID of the fact.
   */
  ```
