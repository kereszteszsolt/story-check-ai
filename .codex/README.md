# Codex roles and technology skills

StoryCheck AI keeps the default Codex session as the main worker. `implementation_worker` is one **optional delegated sole writer** for an approved story. It is never started automatically and must not edit alongside the main session.

| Skill | Load only for |
| --- | --- |
| `spring-boot-web` | Spring Boot REST/static UI, validation, Maven, Docker, CI |
| `spring-ai-providers` | Spring AI prompts, Ollama/OpenRouter adapters, model allowlists and structured output |

Skills describe **how to use the project's technology**, not application-specific acceptance criteria. The active STC story is the source of truth for scope and behavior.

Start with `AGENTS.md`, one story and focused files. Use one writer, minimal context, targeted tests, real verification records and **separate approvals** for implementation and commit. Do not spawn redundant agents or duplicate review/test work.
