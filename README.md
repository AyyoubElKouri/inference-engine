# Inference Engine

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-UI-blue.svg)
![Maven](https://img.shields.io/badge/Build-Maven-success.svg)
![Guice](https://img.shields.io/badge/DI-Google_Guice-green.svg)

A rule-based inference engine (Expert System) developed in Java 21. This project uses the **forward chaining** mechanism to deduce new facts from a knowledge base of rules and a fact catalogue provided as JSON resources.

## Key Features

* **Forward Chaining:** Iterative evaluation to infer new facts until the fact base is saturated.
* **Advanced Rule Conditions:** Support for complex logical operators:
  * Standard operators (`=`, `!=`, `<`, `>`, `<=`, `>=`)
  * Logical groups (`all` / AND, `any` / OR, `not`, `xor`)
  * Quantifiers (`at_least`, `exactly`, `at_most`)
* **Externalized Knowledge Base:** Rules and facts are loaded from classpath JSON resources (`/facts.json`, `/rules.json`) through `json.JsonParser`.
* **Graphical User Interface (UI):** Desktop application built with JavaFX, including a derivation trace graph.
* **Dependency Injection:** Modular layered architecture wired by Google Guice.

## Tech Stack

* **Language:** Java 21
* **Graphical Interface:** JavaFX 21
* **Build Tool:** Maven (use `./mvnw`, never bare `mvn`)
* **Dependency Injection:** Google Guice 7.0.0
* **JSON Parsing:** Jackson Databind 2.17.1
* **Testing:** JUnit Jupiter 5.10.2
* **Formatting:** Spotless 2.43.0 with `eclipse-formatter.xml` (2-space indent, 150-col)

## Installation & Execution

### Prerequisites
* [Java Development Kit (JDK) 21](https://jdk.java.net/21/) (or later)
* No Maven install needed — the repo ships the `mvnw` wrapper.

### Run the project

Run the Maven wrapper from the **repo root**. Rule and fact resources are loaded from the classpath (`/rules.json`, `/facts.json`) through `json.JsonParser`, so the working directory no longer affects resource loading.

```bash
./mvnw javafx:run
```

The entry point is `com.ayyoub.inferenceengine.Launcher`, which starts `EngineApplication` and then `EngineUI` (`Launcher` → `EngineApplication` → `EngineUI`). Headless/CI environments must not launch JavaFX.

To build an executable fat JAR instead:

```bash
./mvnw package -DskipTests
java -jar target/inference-engine-0.0.1-SNAPSHOT.jar
```

## Configuration (JSON)

The rules and fact catalogue live in `src/main/resources/` and are loaded as classpath resources at startup. You can redefine the expert's business logic by modifying these files.
- `facts.json`: top-level JSON array. Defines the fact catalogue that populates the UI inputs; selected facts become the Working Memory for a run.
- `rules.json`: rules nested under `knowledge_base.rules`. Each rule has `id`, `condition`, `conclusions`, `priority` (conflict-resolution order, highest numeric value first; equal priorities keep file order), `confidence` and `explanation`.

Condition DSL: leaf nodes use `attribute`/`operator`/`value`; groups use `all`/`any`/`not`/`xor`; quantifiers use `at_least`/`exactly`/`at_most` with `of`. The engine evaluates rules in descending priority order and keeps ties stable.

## Development commands

```bash
# Fast compile check
./mvnw -q compile -DskipTests
```

```bash
# Launch the JavaFX UI (from repo root)
./mvnw javafx:run
```

```bash
# Format the code (run before committing)
./mvnw spotless:apply
```

```bash
# Build, run tests, and check formatting (fails on format drift)
./mvnw verify
```

Formatting is enforced by `spotless:check` during `verify` using `eclipse-formatter.xml` — not google-java-format.

## Tests

```bash
# Run the unit test suite
./mvnw test
```

The suite currently contains 43 passing tests covering the condition evaluator, forward-chaining engine, knowledge base, working memory, JSON parsing, service facade, trace layout, and Guice wiring. UI components that require a JavaFX toolkit are verified manually with `./mvnw javafx:run`; headless/CI environments must not launch JavaFX.
