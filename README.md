# StoryCheck AI

> **Turn a user story into a clearer, testable starting point.**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-D22128?style=flat-square)](LICENSE)
![Status: MVP in progress](https://img.shields.io/badge/status-MVP%20in%20progress-64748b?style=flat-square)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square)
![Spring AI](https://img.shields.io/badge/Spring%20AI-planned-6DB33F?style=flat-square)

**StoryCheck AI** is a small, self-hosted **user story review demo** planned with Java, Spring Boot, and Spring AI. Paste a story, choose a configured Ollama or OpenRouter model, and request suggestions for **acceptance criteria**, **clarification questions**, and **test cases**. Review the result on one page and copy it as Markdown.

> **Repository status: STC-001 foundation implemented; Release 0.1 in progress.** The app serves a minimal HTML page and `GET /api/health`, with Maven tests and one-service Docker packaging. Model configuration, provider calls, story reviews, the review UI and CI remain Planned in STC-002–004. The feature descriptions and diagrams below describe that target. Implementation requires explicit story-by-story approval.

## Planned features — Release 0.1

- **One-page review:** paste a user story or concise requirement; view a structured review and copy Markdown.
- **Explicit model selection:** at most **three Ollama** and **three OpenRouter** models configured in `.env`; blank slots are hidden.
- **Two AI providers:** host-installed Ollama by default, optional OpenRouter via a server-side API key; never silently fall back to a paid provider.
- **Structured output:** the same Java record/JSON response contract for either provider, validated before returning it to the browser.
- **Small deployment:** one Spring Boot application serves the HTML/CSS/JavaScript UI and REST API; no separate frontend build, database, or application-side agent framework.

## Planned user journey

1. Open the StoryCheck AI page.
2. Select one of the configured models (Ollama or OpenRouter).
3. Paste a story, then click **Analyze Story**.
4. Read the proposed acceptance criteria, clarification questions, test cases and brief feedback.
5. Use **Copy Markdown** to paste the result into a ticket or repository story. Nothing is saved automatically.

```mermaid
sequenceDiagram
    actor User
    participant UI as Browser (HTML/JS)
    participant API as Spring Boot REST API
    participant AI as Spring AI ChatClient
    participant Provider as Selected Ollama / OpenRouter model
    User->>UI: Select model and paste story
    UI->>API: POST /api/reviews
    API->>API: Validate story and configured model
    API->>AI: Shared prompt + structured response schema
    AI->>Provider: Model request
    Provider-->>AI: Generated response
    AI-->>API: Parsed Java record
    API->>API: Validate result and limits
    API-->>UI: Structured review JSON
    UI-->>User: Show review; Copy Markdown
```

## Planned architecture

```mermaid
flowchart LR
    Browser[Browser<br/>Static HTML, CSS, JS]
    subgraph App[One Spring Boot application / Docker container]
      Static[Static UI]
      API[REST API<br/>GET /api/models<br/>POST /api/reviews]
      Review[Review service<br/>prompt + Java record validation]
      Select[Configured provider & model selection]
      Static --- API
      API --> Review
      Review --> Select
    end
    Browser <-->|Same origin HTTP + JSON| App
    Select -->|Local HTTP| Ollama[Ollama on host]
    Select -->|HTTPS, explicit opt-in| Router[OpenRouter API]
```

No database or shared Docker volume is needed. Ollama remains outside Compose; OpenRouter is a cloud API and may charge for usage. The user's story is sent to the **selected** model provider: use fictional or non-confidential material when testing external models.

More detail: [Architecture](docs/architecture.md) · [API contract](docs/api-contract.md) · [Model configuration](docs/model-configuration.md).

## Technology versions and target stack

| Layer | Technology |
| --- | --- |
| Application | Java 21, Spring Boot 4.1.1, Spring Web MVC |
| AI integration | Spring AI 2.0.1 BOM pinned; `ChatClient`, Ollama and OpenRouter integration planned |
| Output | Java records, Structured Outputs / JSON Schema, server-side validation |
| UI | HTML5, CSS3, Vanilla JavaScript (`src/main/resources/static/`) |
| Build & tests | Maven 3.6.3+ (3.x), JUnit Jupiter, Spring MockMvc; Docker build uses Maven 3.9.16 |
| Packaging | Docker, Docker Compose (one app service; external AI providers) |
| Quality | GitHub Actions planned (focused, mocked tests and build) |

`pom.xml` pins Java 21, Spring Boot 4.1.1 and the Spring AI 2.0.1 BOM. The [official compatibility guide](https://docs.spring.io/spring-ai/reference/getting-started.html) supports Spring AI 2.0.x with Boot 4.0.x/4.1.x; [Boot requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) include Java 21 and Maven 3.6.3+. Maven Enforcer requires a Java 21 JDK and Maven 3.x. No AI provider dependency is loaded by the foundation. Compatibility of structured output remains model-dependent and must be tested in later stories.

The Maven coordinates are `net.kereszteszsolt:story-check-ai`; Java sources and tests use the `net.kereszteszsolt.storycheck` package.

## Model configuration (planned)

The example configuration exposes only non-empty allowlisted model slots:

```dotenv
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL_1=qwen2.5:7b
OLLAMA_MODEL_2=llama3.2:3b
OLLAMA_MODEL_3=

OPENROUTER_API_KEY=
OPENROUTER_MODEL_1=
OPENROUTER_MODEL_2=
OPENROUTER_MODEL_3=
```

These are **example model identifiers, not automatically installed models**. Set Ollama model names to models already pulled on your host. Enable OpenRouter only by providing a server-side API key and one or more compatible model IDs. The UI never receives the key. Model names are backend-validated; arbitrary client-supplied model IDs and automatic cloud fallback are not allowed.

Copy `.env.example` to `.env` for Compose. Only `APP_PORT` is consumed in STC-001; provider settings and timeout mapping remain a contract for STC-002. Compose does not pass credentials to the foundation. See [Model configuration](docs/model-configuration.md) for the planned provider behavior.

## Local startup — STC-001 foundation

The foundation needs no model, Ollama installation or API key. Choose a Java 21 JDK with Maven 3.6.3+ (3.x), or Docker Engine/Desktop with Compose v2+. Docker builds need network access to Maven Central and the Maven/Temurin image registries.

Native development:

```bash
mvn -B -ntp verify
mvn spring-boot:run
# Or, after stopping the development process:
java -jar target/story-check-ai.jar
```

The native app binds to `127.0.0.1:8080`. Set an environment variable such as `APP_PORT=8081` to change its port. Native Maven/Java startup reads process environment variables; it does not automatically load `.env`.

Docker Compose:

```bash
cp .env.example .env
# Optionally change APP_PORT; provider settings are not used yet.
docker compose config --quiet
docker compose up --build
```

Open `http://localhost:8080` (or your `APP_PORT`) for the minimal page. `curl --fail http://localhost:8080/api/health` returns `{"status":"ok"}` without contacting a provider. Use `docker compose down` to stop the stack. The container runs as a non-root user; Compose publishes its fixed internal port 8080 only on `127.0.0.1`. `.dockerignore` allows only the Maven project, source and license into the build context, excluding `.env` and repository metadata.

Ollama remains host-installed and outside Compose. Later provider integration will use `http://localhost:11434` for native development and `http://host.docker.internal:11434` from Docker. Compose includes the Linux `host-gateway` mapping, but a host Ollama bound only to loopback may still be unreachable from a Linux bridge. Any host bind/firewall adjustment requires separate configuration; no such change or connectivity test is part of STC-001. No models are downloaded automatically.

The API foundation returns safe JSON errors (`code`, `message`) for invalid requests, missing routes, unsupported methods/formats and unexpected failures. Requests deserialized through `@RequestBody` have a 65,536-byte limit, including streams without a declared length; Jakarta validation is available for later DTOs. No review endpoint exists yet. See [API contract](docs/api-contract.md) and the actual [verification results](docs/releases/release-0.1-mvp/verification.md).

## Development workflow

The repo uses lightweight **story-driven AI-assisted development**. Read [AGENTS.md](AGENTS.md) and [Story workflow](docs/story-workflow.md), then implement **one approved** story at a time. Codex configuration has one **optional** `implementation_worker` and two technology-specific skills; no default parallel agents.

| Guide | Purpose |
| --- | --- |
| [Architecture](docs/architecture.md) | Boundaries and Mermaid diagrams |
| [API contract](docs/api-contract.md) | Planned endpoints and response shape |
| [Model configuration](docs/model-configuration.md) | Environment variables, provider selection, secrets |
| [Testing](docs/testing.md) | Focused checks and approved real-model smoke tests |
| [Roadmap](docs/roadmap.md) | Single planned release and story links |
| [Release 0.1](docs/releases/release-0.1-mvp/README.md) | STC-001–004 and acceptance criteria |
| [Verification](docs/releases/release-0.1-mvp/verification.md) | Actual results once implementation starts |
| [Future ideas](docs/future.md) | Optional possibilities, not planned stories |
| [Codex roles & skills](.codex/README.md) | Minimal agent/skill selection |

## License

Apache-2.0. Copyright © 2026 **Keresztes Zsolt** — [kereszteszsolt.hu](https://kereszteszsolt.hu).

New hand-written source files use the short, two-line SPDX header defined in [AGENTS.md](AGENTS.md). See the full [LICENSE](LICENSE).

## Contact

**Project maintainer: Keresztes Zsolt**

| Platform | Link |
| --- | --- |
| Website | [kereszteszsolt.hu](https://kereszteszsolt.hu/) |
| GitHub | [@kereszteszsolt](https://github.com/kereszteszsolt) |

> The maintainer's website is available in Hungarian (HU), English (EN), Romanian (RO), and German (DE).

## ☕ Ways to support

**Explore ways to support the maintainer and their projects.**

[https://kereszteszsolt.hu/ways-to-support](https://kereszteszsolt.hu/ways-to-support)

<p align="center">
  <a href="https://buymeacoffee.com/kereszteszsolt"><img src="docs/assets/buy-me-a-coffee-orange.png" alt="Buy Me a Coffee" width="360"></a><br>
  <strong>Every coffee counts! ☕❤️</strong>
</p>

---

<p align="center">
  <strong>Made with ❤️ by <a href="https://kereszteszsolt.hu/">Keresztes Zsolt</a></strong><br>
  ⭐ Star this repository if you find it useful!
</p>
