# Inference Engine

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-UI-blue.svg)
![Maven](https://img.shields.io/badge/Build-Maven-success.svg)
![Guice](https://img.shields.io/badge/DI-Google_Guice-green.svg)

A rule-based inference engine (Expert System) developed in Java 21. This project uses the **forward chaining** mechanism to deduce new facts from a knowledge base (rules and initial facts) provided in JSON format.

## Key Features

* **Forward Chaining:** Iterative evaluation to infer new facts until the fact base is saturated.
* **Advanced Rule Conditions:** Support for complex logical operators:
  * Standard operators (`=`, `!=`, `<`, `>`, `<=`, `>=`)
  * Logical groups (`all` / AND, `any` / OR, `not`, `xor`)
  * Quantifiers (`at_least`, `exactly`, `at_most`)
* **Externalized Knowledge Base:** Rules and facts are dynamically loaded from JSON files (`facts.json`, `rules.json`).
* **Graphical User Interface (UI):** Desktop application built with JavaFX.
* **Dependency Injection:** Modular architecture powered by Google Guice.

## Tech Stack

* **Language:** Java 21
* **Graphical Interface:** JavaFX 21
* **Build Tool:** Maven
* **Dependency Injection:** Google Guice 7.0.0
* **JSON Parsing:** Jackson Databind
* **Boilerplate:** Lombok

## Installation & Execution

### Prerequisites
* [Java Development Kit (JDK) 21](https://jdk.java.net/21/) (or later)
* [Apache Maven](https://maven.apache.org/download.cgi)

### Run the project

At the root of the project, simply execute the Maven command configured for the JavaFX plugin:

```bash
mvn javafx:run
```

## Configuration (JSON)

The rules and initial facts are defined in the `src/main/resources/` directory. You can redefine the expert's business logic by directly modifying these files.
- `facts.json`: Represents the Working Memory at startup.
- `rules.json`: Represents the system's rule base.

## Development commands

| Command | What it does |
|---|---|
| `./mvnw spotless:apply` | Format the code |
| `./mvnw verify` | Build, run tests, check formatting |
| `./mvnw test` | Run tests only |
| `./mvnw spring-boot:run` | Start the app (if using Spring Boot) |

## Tests
To run the test scenarios:
```bash
mvn clean test
```