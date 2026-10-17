# Model configuration

**Status: Configuration, adapters, structured reviews and model selector implemented.** The backend maps `.env.example` settings through `storycheck.ai.*` properties; Compose passes them only to the app container. Native Maven/Java startup reads process environment and does not automatically load `.env`. No live model configuration has been verified yet. The browser loads only the backend choices, requires explicit selection, and identifies OpenRouter usage as potentially billable.

Configuration is read at startup; restart the app after changing environment values.

## Fixed model slots

Up to three model identifiers per provider are configured by hand:

| Provider | Required to offer a choice | Slots |
| --- | --- | --- |
| Ollama | Ollama URL and non-empty model ID | `OLLAMA_MODEL_1`, `_2`, `_3` |
| OpenRouter | Server-only API key and non-empty model ID | `OPENROUTER_MODEL_1`, `_2`, `_3` |

Blank values are ignored; IDs are trimmed and deduplicated within each provider in slot order. Only the first three slots per provider are considered. There is no model discovery or download. If no choices remain, the endpoint returns `{"models":[]}`; the page explains the missing configuration, disables submission and offers **Reload models**.

The `POST /api/reviews` service uses `ProviderChatService` with an explicit `provider` and exact allowlisted `model`. Unknown providers, model IDs, mismatched provider/model pairs and disabled OpenRouter choices produce `MODEL_NOT_AVAILABLE` (422) before any provider call.

## Important environment settings

- `OLLAMA_BASE_URL`: endpoint reachable by **Spring Boot** (when containerized, this is not the browser's localhost).
- `OLLAMA_MODEL_1..3`: installed local model IDs (examples in `.env.example` are not downloaded automatically).
- `OPENROUTER_API_KEY`: secret; empty means OpenRouter is unavailable.
- `OPENROUTER_MODEL_1..3`: OpenRouter-qualified model IDs. Select models that actually support the chosen structured-output mode.
- `OPENROUTER_BASE_URL`: HTTPS OpenAI-compatible API base, default `https://openrouter.ai/api/v1`; backend-only, not user-editable through HTTP. Plain HTTP is allowed only for loopback test endpoints. Provider URLs cannot contain user credentials, query strings or fragments.
- `AI_REQUEST_TIMEOUT_MS`: default `60000`, validated range `1000..120000` milliseconds. Both providers have zero automatic retries. OpenRouter also receives `provider.allow_fallbacks=false`.
- `APP_PORT`: local Compose website port; expected default `8080`.

Do not send provider keys to browsers, commit `.env`, print Authorization headers, or log raw inputs/responses.

## Local Ollama versus Docker

On a developer host, `http://localhost:11434` may work for native Spring Boot. From a Docker container, use `http://host.docker.internal:11434` if reachable, and consider Docker's `host-gateway` mapping on Linux. A host-installed Ollama may listen only on loopback, so verify connectivity; if configuring a broader bind address, keep it firewalled and non-public.

## Cloud use and safety

OpenRouter may charge for requests. The user must explicitly choose an OpenRouter model; failures must **not** silently reroute to another provider. A configured model might reject strict structured-output requests; surface a safe explanation and document the model actually tested rather than claiming that all models are compatible.

CI and unit tests must use mocks. Live OpenRouter smoke tests require a real key and the user's separate approval.

The transport gateway accepts at most 16,000 characters including the review instructions and converter format, requests at most 1,024 output tokens, and rejects blank output or output over 32,768 characters. The strict review parser also bounds JSON nesting and validates field/item limits from the [API contract](api-contract.md). No schema-repair retries, tools or callbacks are enabled. Failure messages contain fixed codes/text, without provider bodies, headers, endpoints, credentials or raw prompts.

| HTTP | Code | Condition |
| --- | --- | --- |
| 422 | `MODEL_NOT_AVAILABLE` | Unknown or disabled provider/model selection |
| 504 | `PROVIDER_TIMEOUT` | Transport timeout or upstream 408/504 |
| 429 | `PROVIDER_RATE_LIMITED` | Upstream 429 |
| 503 | `PROVIDER_UNAVAILABLE` | Connection failure, authentication failure, missing upstream model or server failure |
| 502 | `PROVIDER_REJECTED_REQUEST` | Upstream 400/422 |
| 502 | `INVALID_PROVIDER_RESPONSE` | Blank, oversized, malformed or contract-invalid generated content |

## Spring AI version/behavior checkpoint

Spring Boot 4.1.1 and Spring AI 2.0.1 remain pinned. The review uses `ChatClient.call().entity(converter, options -> options.useProviderStructuredOutput())` with a strict `BeanOutputConverter<StoryReview>` and Jakarta validation. Both adapters receive the same bounded schema: Ollama's `format` and OpenRouter's `response_format` with `json_schema`/`strict:true`. The optional `validateSchema()` repair loop is deliberately not enabled. Local HTTP stubs verify the actual adapter requests and fixed responses, including rejection of unsupported structured-output requests; this does not prove compatibility with a live model. Live Ollama and OpenRouter checks remain **Not run**.

Official references: [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html), [Structured output](https://docs.spring.io/spring-ai/reference/api/structured-output.html), [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html).
