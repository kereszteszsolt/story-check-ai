# Testing and verification plan

**Status: Planned; no application tests have been executed.** The baseline contains no Java sources, Maven project, or Docker Compose service.

## Smallest useful automated checks (after implementation)

| Area | Focused checks |
| --- | --- |
| Spring MVC | Health endpoint and model-list contract with MockMvc |
| Configuration | Empty slots, up to three per provider, duplicate IDs, unknown model rejection, missing OpenRouter key |
| Spring AI service | Fixed/mock responses, identical result shape for Ollama/OpenRouter, timeout/provider failures, malformed JSON and validation |
| UI | Manual keyboard flow, empty/loading/error states, copy-to-clipboard fallback, mobile width |
| Packaging | Maven unit tests/build, Docker Compose validation and localhost-only port binding |
| CI | Maven test + package using mocked providers only (no secret or billable request) |

No assertion should depend on a model generating a particular sentence. Provider tests must be deterministic and offline by default.

## Optional runtime smoke checks

When approved and a runnable application exists:

1. Start the app with Docker Compose and verify the page and `/api/health` are reachable.
2. Query `/api/models`: empty model slots are absent and OpenRouter choices require a configured key.
3. Select a **pre-installed Ollama** model, submit a fictional story and confirm a validated response or a documented compatibility error.
4. If the user supplied credentials and approved possible API charges, test exactly **one** OpenRouter model, record which model/version was used and its outcome. Otherwise write `Not run`.
5. Check wrong/unknown models, blank and long input, unavailable provider, and safe error messages. Ensure no secrets or raw prompts appear in logs.

## Evidence policy

Record exact commands, observed results, affected provider/model and limitations in [Release 0.1 verification](releases/release-0.1-mvp/verification.md). A mock-only run must not be described as live AI verification; a local Docker start is not public deployment evidence. Do not invent test counts, screenshots, passing results or model compatibility.
