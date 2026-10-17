# Model configuration

**Status: STC-002 server-side configuration and adapters.** The backend maps `.env.example` settings through `storycheck.ai.*` properties; Compose passes them only to the app container. Native Maven/Java startup reads process environment and does not automatically load `.env`. No live model configuration has been verified yet. The model selector and review endpoint remain planned.

Configuration is read at startup; restart the app after changing environment values.

## Fixed model slots

Up to three model identifiers per provider are configured by hand:

| Provider | Required to offer a choice | Slots |
| --- | --- | --- |
| Ollama | Ollama URL and non-empty model ID | `OLLAMA_MODEL_1`, `_2`, `_3` |
| OpenRouter | Server-only API key and non-empty model ID | `OPENROUTER_MODEL_1`, `_2`, `_3` |

Blank values are ignored; IDs are trimmed and deduplicated within each provider in slot order. Only the first three slots per provider are considered. There is no model discovery or download. If no choices remain, the endpoint returns `{"models":[]}`; the empty-model UI belongs to STC-004.

The internal `ProviderChatService` accepts an explicit `provider` and exact allowlisted `model`. Unknown providers, model IDs, mismatched provider/model pairs and disabled OpenRouter choices produce `MODEL_NOT_AVAILABLE` (422) before any provider call. The STC-003 review endpoint will use this boundary; STC-002 adds no public generation endpoint.

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

The transport gateway accepts at most 16,000 prompt characters, requests at most 1,024 output tokens, and rejects blank output or output over 32,768 characters. These are internal transport limits; the final user-story and structured-review limits are defined in STC-003. No tools or callbacks are registered. Failure messages contain fixed codes/text, without provider bodies, headers, endpoints, credentials or raw prompts.

| HTTP | Code | Condition |
| --- | --- | --- |
| 422 | `MODEL_NOT_AVAILABLE` | Unknown or disabled provider/model selection |
| 504 | `PROVIDER_TIMEOUT` | Transport timeout or upstream 408/504 |
| 429 | `PROVIDER_RATE_LIMITED` | Upstream 429 |
| 503 | `PROVIDER_UNAVAILABLE` | Connection failure, authentication failure, missing upstream model or server failure |
| 502 | `PROVIDER_REJECTED_REQUEST` | Upstream 400/422 |
| 502 | `INVALID_PROVIDER_RESPONSE` | Blank or oversized generated content |

## Spring AI version/behavior checkpoint

STC-001 pins compatible Spring Boot and Spring AI versions. STC-002 checks OpenRouter base URL/path, authorization and model selection using the real adapter against a local HTTP stub. That does not prove compatibility with any live OpenRouter model. STC-003 verifies `.entity(JavaRecord.class)` typed responses, server-side validation, and provider-native structured-output behavior with selected models; do not assume every model supports strict JSON Schema.

Official references: [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html), [Structured output](https://docs.spring.io/spring-ai/reference/api/structured-output.html), [Ollama chat](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html).
