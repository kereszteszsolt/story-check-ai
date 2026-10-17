# Model configuration — planned

**Status: Planned.** The root `.env.example` is a specification for future application configuration, not working Spring properties yet.

## Fixed model slots

Up to three model identifiers per provider are configured by hand:

| Provider | Required to offer a choice | Slots |
| --- | --- | --- |
| Ollama | Ollama URL and non-empty model ID | `OLLAMA_MODEL_1`, `_2`, `_3` |
| OpenRouter | Server-only API key and non-empty model ID | `OPENROUTER_MODEL_1`, `_2`, `_3` |

Blank values are ignored. Do not invent placeholder models or fetch a provider-wide catalogue. Deduplicate equal IDs within one provider. If no choices remain, show a clear empty-model state rather than an unusable selector.

A model is selected by explicit `provider` and `model` fields in each review request. Backend allowlist checks prevent arbitrary model IDs from being submitted.

## Important environment settings

- `OLLAMA_BASE_URL`: endpoint reachable by **Spring Boot** (when containerized, this is not the browser's localhost).
- `OLLAMA_MODEL_1..3`: installed local model IDs (examples in `.env.example` are not downloaded automatically).
- `OPENROUTER_API_KEY`: secret; empty means OpenRouter is unavailable.
- `OPENROUTER_MODEL_1..3`: OpenRouter-qualified model IDs. Select models that actually support the chosen structured-output mode.
- `OPENROUTER_BASE_URL`: HTTPS OpenAI-compatible API endpoint; backend-only, not user-editable through HTTP.
- `AI_REQUEST_TIMEOUT_MS`: bounded provider request timeout. Input/output and retries must also have bounded server limits.
- `APP_PORT`: local Compose website port; expected default `8080`.

Do not send provider keys to browsers, commit `.env`, print Authorization headers, or log raw inputs/responses.

## Local Ollama versus Docker

On a developer host, `http://localhost:11434` may work for native Spring Boot. From a Docker container, use `http://host.docker.internal:11434` if reachable, and consider Docker's `host-gateway` mapping on Linux. A host-installed Ollama may listen only on loopback, so verify connectivity; if configuring a broader bind address, keep it firewalled and non-public.

## Cloud use and safety

OpenRouter may charge for requests. The user must explicitly choose an OpenRouter model; failures must **not** silently reroute to another provider. A configured model might reject strict structured-output requests; surface a safe explanation and document the model actually tested rather than claiming that all models are compatible.

CI and unit tests must use mocks. Live OpenRouter smoke tests require a real key and the user's separate approval.

## Spring AI version/behavior checkpoint

STC-001 pins compatible Spring Boot and Spring AI versions. STC-002 verifies OpenAI-compatible OpenRouter `base-url` support. STC-003 verifies `.entity(JavaRecord.class)` typed responses, server-side validation, and provider-native structured-output behavior with the selected models; do not assume every model supports strict JSON Schema.

Official references: [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html), [Structured output](https://docs.spring.io/spring-ai/reference/api/structured-output.html), [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html).
