# Codex engineering guide

## Repository status and scope

**StoryCheck AI has an STC-001 application foundation.** Release 0.1 is in progress; STC-002–004 remain Planned. The foundation serves a minimal HTML page and health API, with Maven tests and Docker packaging; see release verification for actual evidence. The approved target is one Java 21 Spring Boot app with built-in HTML/CSS/JS, Spring AI, at most three configured Ollama plus three OpenRouter models, validated JSON/Java record reviews, and Docker packaging. No database, frontend framework, RAG, tool calling, or agent runtime.

## Read minimal relevant context

1. Read `AGENTS.md`, the active approved `STC-*` story, and its release README. Use `docs/story-workflow.md` for approval/evidence policy.
2. Open only affected files, relevant tests, and the matching technology skill. Consult `docs/architecture.md`, `docs/api-contract.md` or `docs/model-configuration.md` **as needed**, not by default.
3. Use `spring-boot-web` for Spring MVC/static UI/Maven/Docker changes; `spring-ai-providers` for Ollama/OpenRouter, Spring AI, JSON records and provider tests. Load both only when a story truly crosses the boundary.
4. Never load every story, skill, or historical log just to gather context.

## Architecture boundaries

- **One deployable:** Spring Boot serves static HTML/CSS/JS and `/api` routes on the same origin. No monorepo framework, separate Node.js UI, React, Angular, or database.
- **Web:** thin controller, input validation, bounded payloads, stable JSON/HTTP errors, no API keys in browser code. Keep UI simple and accessible; render model text as text, not untrusted HTML.
- **Spring AI:** prompt and ChatClient work belongs in a narrow service, not a controller. Return a validated Java record. Use the same response shape for both providers.
- **Providers:** backend reads up to three model IDs per provider; omit empty slots. Ollama is local, OpenRouter requires explicit selection and backend-only credentials. Never silently fall back to a charged provider. Reject IDs not in the configured allowlist.
- **Safety:** no model-driven tool calls, local file access, network fetches based on LLM text, or unbounded retries. Validate generated data, timeouts, and error categories. Tests must not incur OpenRouter charges by default.
- **Runtime:** one optional Spring Boot Docker service. Ollama remains host-installed and external; OpenRouter is remote. Default network must not expose Ollama or secrets publicly.

## Agent and skill policy

- Main Codex session is the default planner, implementer and reviewer for a **single approved story**.
- `implementation_worker` is an optional delegated **sole writer**, not an obligatory second agent. Never edit concurrently with the main session or spawn duplicate reviewers.
- Skills give **technology-specific implementation guidance**; user story rules and app behavior belong to `docs/` and application code, not repeated in skills.
- Prioritize narrow edits, focused tests, short summaries and avoiding repeated scans, duplicate work and unnecessary parallelism.

## Approval and verification

1. Name the active story, affected files, risks and focused checks; request explicit implementation approval.
2. Implement only its ordered acceptance criteria. Ask before widening scope or making a billable live API request.
3. Run focused/mock-based checks first; use real Ollama/OpenRouter smoke tests only after approval and configuration.
4. Record actual commands/results or `Not run` in the active release `verification.md`. Never mark Planned features as delivered without evidence.
5. Review the diff, propose a commit message and ask for separate approval before committing, pushing or starting another story. No automatic commits or force pushes.

## Short SPDX license headers

Put exactly this **two-line SPDX notice** at the start of new, hand-written Java, JavaScript, CSS, HTML, shell and other source files, using the language's native comment syntax:

```text
SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
SPDX-License-Identifier: Apache-2.0
```

Examples: `//` in Java/JS, `/* ... */` in CSS, `<!-- ... -->` in HTML, `#` in shell. Do **not** copy the full Apache license into each source file. Do **not** add headers to general configuration/build files such as `pom.xml`, `.properties`, `.gitignore`, `.dockerignore` or environment files. Markdown, TOML, YAML, JSON, generated files, dependency locks and the root Dockerfile also do not receive headers. Keep the full Apache-2.0 text in `LICENSE`; preserve third-party notices. Source comments explain non-obvious reasons, not routine code behavior.

## Boundaries

`docs/future.md` is an idea list, not implementation approval. Do not create future releases or new agents unless asked. Do not treat the STC-001 foundation as approval to implement later stories.
