# REST contract

**Status: Health, model listing and structured reviews implemented.** Both providers share one validated review contract. Deterministic mocks/local HTTP stubs verify the API and adapters; live model compatibility remains untested.

## `GET /api/health`

Returns HTTP 200 with `{"status":"ok"}`. It does not require a configured LLM or call a provider. The same Spring Boot process serves `/` as a minimal static HTML entry point.

## Foundation errors and input boundary

Errors use `application/json` and the shape `{"code":"INVALID_REQUEST","message":"The request is invalid."}`. Raw input, exception details, paths and secrets are omitted. Framework headers such as `Allow` are retained.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Malformed/missing body or failed input validation |
| 404 | `NOT_FOUND` | Missing resource or unimplemented endpoint |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method |
| 406 | `NOT_ACCEPTABLE` | Unsupported response format |
| 413 | `PAYLOAD_TOO_LARGE` | Body exceeds 65,536 bytes |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported request format |
| 500 | `INTERNAL_ERROR` | Unexpected failure |

All bodies read through Spring MVC `@RequestBody` are bounded to 65,536 bytes before deserialization, even without a reliable `Content-Length`. JSON requests reject unknown/duplicate fields, trailing documents, missing/null required fields and incorrect value types; numbers and booleans are not converted to strings. The review endpoint validates the domain constraints below before calling any model. Provider failures use the safe mappings in [Model configuration](model-configuration.md).

## `GET /api/models`

Returns only backend-configured, non-empty model slots. Empty slots are omitted. Model choices are grouped or labelled by provider and stable ID. An OpenRouter choice is exposed only if the server has credentials and a configured model. No secrets or unrestricted provider base URLs are returned.

Example shape:

```json
{
  "models": [
    {"provider":"ollama", "model":"qwen2.5:7b", "label":"Ollama · qwen2.5:7b"},
    {"provider":"ollama", "model":"llama3.2:3b", "label":"Ollama · llama3.2:3b"}
  ]
}
```

Example model values are illustrative; actual values come from backend environment configuration. With no enabled choices, the response is `{"models":[]}`. This endpoint never contacts providers, downloads models or tests model health. It accepts no arbitrary model or endpoint overrides.

## `POST /api/reviews`

Request example:

```json
{
  "provider": "ollama",
  "model": "qwen2.5:7b",
  "story": "As a user, I want to reset my password so that I can access my account."
}
```

Response example (mock/example text, **not a measured model output**):

```json
{
  "summary": "The reset mechanism and token expiration need clarification.",
  "acceptanceCriteria": ["A user can request a reset link.", "An expired token cannot reset a password."],
  "clarificationQuestions": ["How long should a reset token remain valid?"],
  "suggestedTests": ["An expired token is rejected without changing the password."]
}
```

The request must be a JSON object with exactly these three nonblank strings:

| Field | Constraint |
| --- | --- |
| `provider` | At most 16 characters; exact enabled `ollama` or `openrouter` selection |
| `model` | At most 200 characters; exact allowlisted ID for that provider |
| `story` | 10–4,000 characters of plain text; retained unchanged, including literal braces |

Lengths use Java string length (UTF-16 code units). Invalid structure, blank values and size violations return 400 `INVALID_REQUEST`; a well-formed but unknown/disabled provider/model pair returns 422 `MODEL_NOT_AVAILABLE`. Neither reaches a provider. A valid request returns HTTP 200 with exactly these fields:

| Field | Constraint |
| --- | --- |
| `summary` | Nonblank string, at most 600 characters |
| `acceptanceCriteria` | 1–8 nonblank strings, at most 500 characters each |
| `clarificationQuestions` | 0–6 nonblank strings, at most 300 characters each |
| `suggestedTests` | 1–8 nonblank strings, at most 500 characters each |

**Zero clarification questions** are valid when nothing material is missing. One shared prompt asks for concise feedback in the story's language, preserving its intent and avoiding invented business rules or questions to fill a quota. This is guidance for the model, not a guarantee of semantic correctness.

Spring AI sends the same native JSON Schema to both adapters and parses the result into a Java record. Server validation rejects malformed JSON, markdown/prose around JSON, missing/unknown/duplicate fields, wrong types and all size violations with 502 `INVALID_PROVIDER_RESPONSE`. A model that rejects structured-output requests with upstream 400/422 produces 502 `PROVIDER_REJECTED_REQUEST`. There is no repair retry or provider fallback. Other safe failures are 429 `PROVIDER_RATE_LIMITED`, 503 `PROVIDER_UNAVAILABLE` and 504 `PROVIDER_TIMEOUT`.

Responses contain validated fields only; no raw provider reply, headers, API key, reasoning trace or local filesystem data is added. The endpoint does not persist or edit the story. The planned STC-004 frontend will create the **Markdown copy** locally from these fields.

## Data handling

The text is transmitted only to the **user-selected** model provider. No automatic switching from Ollama to OpenRouter. Do not log raw story text or the provider secret. Automated CI must use mocks and must never call paid APIs.
