# Release 0.1 verification

## Status

**STC-001–004 implemented and checked locally.** The responsive UI, Markdown copy/manual fallback and APIs have Maven, loopback-provider, Docker and real Chromium evidence with synthetic responses. Live Ollama/OpenRouter, hosted GitHub Actions execution and public deployment remain unverified. This records local MVP implementation, not a published release or live model compatibility.

## Evidence table

| Story | State | Implementation checks | Real provider / browser check | Notes |
| --- | --- | --- | --- | --- |
| STC-001 | Implemented | 22 JUnit/MockMvc/HTTP tests passed; executable JAR and Docker build passed; Compose config/start passed | Packaged app: 8 HTTP checks passed; visual browser and providers: Not run | Localhost-only port 18081; no native host JVM; local commit separately approved after review |
| STC-002 | Implemented | 64 tests passed, including 12 real-adapter HTTP-stub cases; executable JAR, Docker build and Compose checks passed | Packaged app: 6 HTTP checks passed; live Ollama/OpenRouter and visual browser: Not run | Explicit selection; no cloud fallback; no final review endpoint; local commit separately approved after review |
| STC-003 | Implemented | 162 tests passed, including strict converter/MockMvc and 20 real-adapter HTTP-stub cases; executable JAR, Docker build and Compose checks passed | Packaged app: 13 HTTP checks passed; live Ollama/OpenRouter and visual browser: Not run | Shared prompt/schema, bounded validated records, no repair retry/fallback; local commit separately approved after review |
| STC-004 | Implemented | 163 Maven tests, executable JAR, independent Docker build, Compose startup, 13 packaged HTTP checks, CI YAML and docs audits passed | Chromium: 20 scenarios with fixed API fixtures, real clipboard and mobile checks passed; live providers: Not run | Hosted CI: Not run; no paid calls; local commit separately approved after review |

## Recording rules

Once a story is explicitly approved and implemented, append the date, platform, exact commands, observed result and any blockers under a heading for that story. Distinguish mocked tests from real Ollama/OpenRouter calls; cloud calls may incur charges. A blocked criterion stays unchecked; do not claim a completed release based only on passing mocks.

Separate implementation approval, commit approval and a successful commit hash are required before recording a commit as delivered.

## STC-001 — 2026-10-17

Implementation was approved by the user's explicit request to implement STC-001. After implementation and the namespace/header corrections, the maintainer separately authorized a local commit subject to successful review on 2026-10-17. The remaining stories were not started.

### Environment and versions

- Workspace: `/mnt/c/P/ghpub/www-temp/story-check-ai`, Linux/WSL with Docker Desktop 4.82.0, Engine 29.6.1 and Compose 5.3.0.
- Build: `maven:3.9.16-eclipse-temurin-21`, Maven 3.9.16 / Java 21.0.12. Runtime image: `eclipse-temurin:21-jre-jammy`, Java 21.0.12.1.
- `pom.xml`: Java 21, Spring Boot 4.1.1 and Spring AI BOM 2.0.1. The [official Spring AI guide](https://docs.spring.io/spring-ai/reference/getting-started.html) confirms the 2.0.x / Boot 4.1.x compatibility line. No AI provider runtime dependency is installed yet.
- Host `java -version` failed (`java: command not found`); host `mvn -version` reported missing `JAVA_HOME`. Java build and application checks therefore used Docker, without installing a host JDK.
- Port 8080 was already occupied by another project. The runtime check used `APP_PORT=18081`; no other stack was changed.

### Maven and packaging

Commands executed from the workspace:

```bash
mkdir -p /tmp/stc001-m2
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dmaven.compiler.showDeprecation=true verify
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp clean verify
```

The first run found one test setup error: `HttpHeaders.setContentLength(-1)` is invalid. The test now leaves the header absent to represent an unknown length. The next verification passed all 22 tests and packaged the JAR. That run also exposed deprecated HTTP 413 names and dynamic Mockito attachment warnings/thread dumps; current Spring API names and an explicit test JVM Mockito agent resolved those issues.

The final **clean verify passed: 22 tests, 0 failures, 0 errors, 0 skipped**, producing `target/story-check-ai.jar`. Only the JVM class-data-sharing warning from the test agent remains; the final run had no Surefire channel corruption warning. The intentional failure fixture logs only its exception class, without its secret-like message.

Coverage: health contract without provider configuration; 400 validation/malformed/missing JSON; 404/405/406/413/415/500 safe errors; preserved `Allow`; 64 KiB byte boundary with absent or understated length; real embedded-server startup serving HTML and JSON from one port; safe `/error` fallback; test fixture absent from the production application context. DTO validation uses a test-only controller and does not introduce a review API.

### Docker Compose and runtime

```bash
docker compose config --quiet
docker compose config --format json
APP_PORT=18081 docker compose up --build -d
docker compose ps
docker compose exec -T app id
docker compose exec -T app java -version
docker inspect story-check-ai-app-1 --format '{{json .NetworkSettings.Ports}} user={{.Config.User}} image={{.Image}}'
docker compose logs --no-color --tail 20 app
```

All commands passed. The Docker build independently ran `mvn -B -ntp verify`: **22 tests passed**, JAR repackaging succeeded, and the container started. Compose has exactly one `app` service and no Ollama service or data volume. Runtime user: `uid=999(storycheck) gid=999(storycheck)`. Observed port binding: `127.0.0.1:18081 -> 8080/tcp`. Image ID: `sha256:c0d5e9210c583ca3933747c6f6fbbb1c83a3b8f4c6b5d42486970cabde8793f9`.

The following Python HTTP smoke ran against the packaged application:

```python
import json, urllib.request, urllib.error
base='http://127.0.0.1:18081'
cases=[('GET','/', 'text/html',200,None),('GET','/api/health','application/json',200,None),('POST','/api/health','application/json',405,'METHOD_NOT_ALLOWED'),('GET','/api/health','text/html',406,'NOT_ACCEPTABLE'),('GET','/api/models','application/json',404,'NOT_FOUND'),('POST','/api/reviews','application/json',404,'NOT_FOUND'),('GET','/api/test/failure','application/json',404,'NOT_FOUND'),('GET','/error?message=secret&trace=true','text/html',500,'INTERNAL_ERROR')]
for method,path,accept,expected,code in cases:
    req=urllib.request.Request(base+path,method=method,headers={'Accept':accept})
    try:
        res=urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as e:
        res=e
    with res:
        body=res.read().decode()
        assert res.status==expected,(path,res.status,body)
        if path=='/':
            assert '<h1>StoryCheck AI</h1>' in body and res.headers['Content-Type'].startswith('text/html')
        else:
            assert res.headers['Content-Type'].startswith('application/json'),(path,res.headers)
            data=json.loads(body)
            if code:
                assert data['code']==code and set(data)=={'code','message'},data
            else:
                assert data=={'status':'ok'},data
        if expected==405:
            assert 'GET' in res.headers['Allow']
        assert 'secret' not in body and 'stackTrace' not in body
        print(method, path, '->', res.status, code or ('HTML' if path=='/' else 'status=ok'))
print('Packaged Compose HTTP smoke: 8 checks passed.')
```

Result: **8 checks passed**, including HTML 200 and exact health `{"status":"ok"}`. The app was left running at `http://localhost:18081`; `docker compose down` stops this stack. No `.env` file was created.

### Source and evidence boundary

`git diff HEAD --check` passed. Following the maintainer's clarification, license headers were removed from general configuration/build files (`pom.xml`, `.dockerignore`, `application.properties`), and the repository rule was updated. The follow-up Python audit confirmed exactly one two-line SPDX notice at the start of all 10 new Java/HTML source files, with no license headers in the general configuration files. This comment-only follow-up was checked with XML parsing and whitespace checks; Maven/Docker checks above were not rerun. `LICENSE` and `docs/assets` have no diff; affected guide links resolve locally. JAR inspection confirmed Java 21 bytecode, the static entry point, no test fixture classes and no AI provider libraries. The Docker build context uses an allowlist excluding secrets and Git metadata.

Native host Maven/Java startup, visual browser inspection, host-Ollama connectivity and live Ollama/OpenRouter requests: **Not run**. No provider settings, models or credentials were needed, no models were downloaded, and no billable API request was made. This is local foundation evidence, not a completed MVP or public deployment.

### Namespace follow-up — 2026-10-17

At the maintainer's request, the Maven `groupId` is now `net.kereszteszsolt`, with application and test packages under `net.kereszteszsolt.storycheck`. All nine Java files were moved to matching directories; source license notices retain the prescribed personal website URL. General configuration files remain without license headers.

```bash
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp clean verify
git diff HEAD --check
```

Result: **BUILD SUCCESS — 22 tests, 0 failures, 0 errors, 0 skipped**. The startup tests again served HTML and health over a real embedded HTTP server. A Python ZIP/XML/source audit confirmed the new Maven coordinates, matching package paths, the JAR `Start-Class`, Maven metadata and class entries under the new namespace, with no old package classes. The Compose image was not rebuilt for this naming change; the earlier container evidence above refers to the original image. No commit was created during this namespace follow-up.

### Commit review — 2026-10-17

Reviewed the complete tracked diff and all 15 new files; no blocking findings. `git diff HEAD --check` and new-file whitespace checks passed. The current Surefire reports confirm 22 passing tests under `net.kereszteszsolt`; source/configuration files are unchanged since that packaged build, so tests were not repeated for commit preparation. The JAR manifest, Maven metadata and class paths match the requested namespace. Approved commit message: `feat: implement STC-001 Spring Boot foundation`. The resulting commit identity is recorded in Git history.

## STC-002 — 2026-10-17

The maintainer explicitly requested implementation of the second story after STC-001 commit `570c051`, then separately authorized an STC-002 local commit subject to successful review on 2026-10-17. No live-model approval or billable request was given; STC-003–004 were not started.

### Configuration and adapter boundary

Java 21, Maven 3.9.16, Spring Boot 4.1.1 and Spring AI 2.0.1 are unchanged. The build now includes `spring-ai-client-chat`, `spring-ai-ollama` and `spring-ai-openai` directly, without provider auto-configuration starters. The [official Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) and [OpenAI-compatible adapter](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html) guidance and the pinned module source JARs were checked against the compiled APIs.

The registry reads exactly three slots per provider, trims/ignores blanks, deduplicates within each provider and returns immutable public descriptors. OpenRouter requires a nonblank key. The internal gateway checks the exact provider/model pair before any call; the configured model overrides the adapter default. Timeouts default to 60,000 ms and must be 1,000–120,000 ms. Application/SDK retries are zero, no provider fallback is implemented, and OpenRouter receives `provider.allow_fallbacks=false`. The gateway bounds prompts to 16,000 characters, requests 1,024 output tokens and rejects blank or over-32,768-character output. No tools or callbacks are registered.

Only `GET /api/models` is added publicly. Prompt completion is an internal adapter boundary for the next story, not a public review endpoint or the final review prompt/schema. Error rendering tests use test-only controllers, absent from the packaged JAR.

### Commands and results

Commands ran from `/mnt/c/P/ghpub/www-temp/story-check-ai` using the existing Java 21 Maven container/cache:

```bash
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -DskipTests compile
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ModelRegistryTests,ProviderConfigurationTests,ProviderChatServiceTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ModelRegistryTests,ProviderConfigurationTests,ProviderChatServiceTests,ProviderWireTests,ModelsApiTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ProviderWireTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ProviderWireTests#configuredTimeoutIsAppliedToBothAdapters test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp clean verify
docker compose config --quiet
git diff HEAD --check
```

Early checks exposed Spring AI 2.0 API differences (builder-based request options and the SDK transport location), a mock returning null default options, and a wire assertion that incorrectly required an explicit `stream:false` field when the SDK legitimately omits it. Those were corrected. Initial timeout probes included cold SDK class loading in their four-second total budget; they now warm the adapter against the same local stub before measuring the one-second timeout against a two-second delayed response. Temporary socket-timeout tuning was removed; both adapters pass using the configured request timeouts. The final isolated wire suite passed all 12 cases.

The final **clean verify passed: 64 tests, 0 failures, 0 errors, 0 skipped**, and produced `target/story-check-ai.jar`. Negative configuration fixtures intentionally log safe startup failures; the test JVM still emits its known class-data-sharing warning. Neither is a test failure.

| Suite | Tests | Evidence |
| --- | --- | --- |
| ModelRegistryTests | 8 | Empty/duplicate slots, slot cap, per-provider identity, missing credentials, exact allowlist, redacted settings |
| ProviderConfigurationTests | 7 | Environment mapping, ignored fourth slot, missing key, timeout bounds and safe endpoint validation |
| ProviderChatServiceTests | 5 | Mock routing, literal prompt text, unknown models before calls, no fallback on timeout, input/output bounds |
| ProviderWireTests | 12 | Real Spring AI adapters against loopback stubs: exact paths, selected model, backend Authorization, output-token limit, OpenRouter fallback/tool-choice flags, 429/500/404/401/400/504 and timeouts; one request per failure |
| ModelsApiTests | 9 | Public descriptors only, no key/endpoint disclosure, invalid selection 422, safe provider HTTP errors |
| ApiFoundationTests | 15 | Existing foundation behavior plus empty model list; review route remains absent |
| StartupTests | 5 | Real same-origin HTTP, local-only choices and health with a missing cloud key, test routes absent |
| BoundedRequestBodyAdviceTests | 3 | Existing byte-limit regressions |

JAR/source audits confirmed the three pinned Spring AI modules, no provider auto-configuration starters, no test fixtures in the JAR, correct `net.kereszteszsolt` packages, and the prescribed source headers across 21 Java/HTML files. General config/build files remain header-free. XML parsing, local guide links and whitespace checks passed. A Compose JSON audit with dummy settings verified all six model slots, the backend key and timeout forwarding, one app service and localhost-only publishing without printing credentials.

### Packaged runtime and final review

```bash
APP_PORT=18081 OLLAMA_MODEL_1= OLLAMA_MODEL_2= OLLAMA_MODEL_3= OPENROUTER_API_KEY= OPENROUTER_MODEL_1= OPENROUTER_MODEL_2= OPENROUTER_MODEL_3= docker compose up --build -d
docker compose ps
docker inspect story-check-ai-app-1 --format '{{json .NetworkSettings.Ports}} user={{.Config.User}} image={{.Image}}'
docker compose exec -T app id
```

Docker independently built the current source with **64 passing tests**, repackaged the JAR and started `story-check-ai-app-1`. Image: `sha256:d7a3c02d4f1d938b5ba0701c2e55b233389bd75353a8ca61d7ceadecb51970b1`. It runs as `uid=999(storycheck)` and publishes only `127.0.0.1:18081 -> 8080/tcp`. Model slots/key were explicitly empty for this runtime check; no `.env` was created or edited.

A Python `urllib.request` loop with a five-second request timeout checked six paths on `http://127.0.0.1:18081`: `/` returned HTML 200; `/api/health` returned 200 with exactly `{"status":"ok"}`; `/api/models` returned 200 with exactly `{"models":[]}`; `/api/reviews`, `/api/test/select` and `/api/test/provider-error` each returned JSON `NOT_FOUND` with HTTP 404. **All six passed.** The application was left running on that port; startup and listing made no model requests.

The final tracked/untracked diff was reviewed with no blocking findings. Scope remains STC-002, `net.kereszteszsolt` and the configuration-header exception are retained, and STC-003–004 files are unchanged. Commit message: `feat: implement STC-002 configured AI providers`; the maintainer separately approved the local commit after implementation.

### Real-provider boundary

**Live Ollama: Not run. Live OpenRouter: Not run.** No real provider/model was tested or downloaded. All generated test text is fixed fixture content; adapter requests went only to local loopback HTTP stubs with dummy credentials. No compatibility claim is made for the example `qwen2.5:7b` / `llama3.2:3b` models or any cloud model. No billable API call, host Ollama bind/firewall change, visual browser test or final structured-review test was performed.

### Commit review — 2026-10-17

Reviewed the full tracked diff and the 11 new Java files. Current Surefire reports confirm 64 tests with zero failures/errors/skips; source/configuration files are unchanged since the packaged build, so tests were not repeated for commit preparation. The JAR module/namespace/fixture audit, Compose configuration, source headers and whitespace checks passed. No blocking findings or unrelated files were found. The resulting commit identity is recorded in Git history.

## STC-003 — 2026-10-17

Implementation was approved by the maintainer's explicit request to implement STC-003, starting from STC-002 commit `9a5d1c3`. The maintainer separately authorized a local commit subject to successful review on 2026-10-17. STC-004 was not started.

### Review contract and provider boundary

`POST /api/reviews` now validates exactly three nonblank strings: provider (maximum 16 characters), model (maximum 200) and unchanged plain-text story (10–4,000). The existing 65,536-byte body boundary still applies. Strict JSON parsing rejects duplicate/unknown fields, trailing documents, missing/null required fields and scalar coercion before any provider call. Exact allowlist validation rejects unknown/disabled pairs with 422.

One service builds the same system instructions and literal user message for either provider. Spring AI's `entity(converter, options -> options.useProviderStructuredOutput())` sends a shared bounded schema and returns a validated `StoryReview` record: summary up to 600 characters, 1–8 criteria/tests up to 500 each, and 0–6 questions up to 300 each. Zero questions are deliberately valid. The custom `BeanOutputConverter` uses strict parsing without stripping markdown/thinking text, bounds content to 32,768 characters and nesting to 32, and discards parser causes that could expose generated content. The existing 1,024-token request cap and configured timeouts remain; SDK retries, schema-repair retries and provider fallback are disabled.

The [official structured-output reference](https://docs.spring.io/spring-ai/reference/api/structured-output.html) and the pinned Spring AI 2.0.1 source JARs were checked for native schema options and converter behavior. No dependency or provider configuration change was needed.

### Commands and deterministic results

The existing WSL/Docker environment, Maven 3.9.16, Java 21.0.12, Spring Boot 4.1.1 and Spring AI 2.0.1 are unchanged. Commands ran from the repository:

```bash
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -DskipTests compile
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ReviewOutputConverterTests,ReviewsApiTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=ProviderWireTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp clean verify
docker compose config --quiet
git diff HEAD --check
```

All passed. The first focused suite passed **91 tests**; the adapter wire suite passed **20 tests**. The final **clean verify passed: 162 tests, 0 failures, 0 errors, 0 skipped**, and produced `target/story-check-ai.jar`. Known test-agent class-data-sharing and intentional negative-fixture log messages remain; no raw story or secret-like provider exception appeared in the captured error-path logs.

| Suite | Tests | Evidence |
| --- | --- | --- |
| ReviewOutputConverterTests | 54 | Typed/immutable record, zero and nonzero questions, schema limits, upper bounds; malformed/extra/duplicate/missing/null/wrong-type/oversized/over-nested output rejected safely |
| ReviewsApiTests | 37 | Full Spring context with mocked models: both providers, literal story, input boundaries, no calls for invalid selection/input, safe provider/output errors, no fallback/retry, no raw input/error logs |
| ProviderWireTests | 20 | Real adapters against loopback fixtures: prior transport/error/timeout checks plus native schema on both providers, fixed review response, blank/invalid output and unsupported structured-output rejection with one request |
| Existing regression suites | 51 | Registry/configuration, gateway, health/models/errors, real embedded-server startup and request byte bounds; obsolete review-route 404 case removed because GET now correctly returns 405 |

### Packaged runtime and final review

```bash
APP_PORT=18081 OLLAMA_MODEL_1= OLLAMA_MODEL_2= OLLAMA_MODEL_3= OPENROUTER_API_KEY= OPENROUTER_MODEL_1= OPENROUTER_MODEL_2= OPENROUTER_MODEL_3= docker compose up --build -d
docker inspect story-check-ai-app-1 --format '{{json .NetworkSettings.Ports}} user={{.Config.User}} image={{.Image}}'
docker compose exec -T app id
python3 /tmp/stc003-smoke.py
docker compose logs --no-color --tail 60 app
```

Docker independently ran `mvn -B -ntp verify` with **162 passing tests** and built image `sha256:990837c59744db64a1e1ec0206b98a35778ecadf0982865ec82f85590a9d716a`. The current app runs as `uid=999(storycheck)` and publishes only `127.0.0.1:18081 -> 8080/tcp`. All six slots and the cloud key were explicitly empty; no `.env` was created or edited.

The temporary Python `urllib.request` smoke script checked the packaged app with a five-second timeout per request. Its first sandboxed invocation could not open a socket (`Operation not permitted`); the authorized invocation outside that restriction passed **all 13 checks**:

- HTML `/` 200; exact health JSON 200; exact empty model list JSON 200.
- Review with an unconfigured model: 422 `MODEL_NOT_AVAILABLE`.
- Blank, 4,001-character, numeric and duplicate-field review inputs: 400 `INVALID_REQUEST`.
- A 65,537-byte body: 413 `PAYLOAD_TOO_LARGE`.
- GET reviews: 405 with `Allow: POST`; text/plain POST: 415.
- Test-only route: 404; safe `/error` fallback: 500 without reflected query content.

Every error had exactly `code`/`message`, JSON content type, and no fictional private-story/secret marker or stack trace. Container logs contained no raw marker or provider request. The app is left running at `http://localhost:18081`.

The complete tracked/untracked diff was reviewed with no blocking findings. Source/configuration behavior is covered by the build; the only following test-source cleanup removed an unused import and was included in the independent Docker verification. Python audits passed for the prescribed SPDX notices in all 30 Java/HTML source files, header-free general configuration files, new-file whitespace, changed-document local links and the executable JAR's namespace/review/static files. No test fixture or `.env` is packaged. `net.kereszteszsolt`, build/config files, UI assets and STC-004 scope are retained; no unrelated changes were found. `git diff HEAD --check` passed.

### Real-provider and commit boundary

**Live Ollama: Not run. Live OpenRouter: Not run.** No real model was called/downloaded, no key was used beyond dummy loopback test credentials, and no billable request was made. Model/schema compatibility and semantic quality remain unverified for every example or configured live model. Native host Java startup and visual browser checks were not run; the review UI and Markdown copy belong to STC-004.

### Commit review — 2026-10-17

Reviewed the full tracked diff and all nine new Java files; no blocking findings or unrelated changes. Current Surefire reports confirm 162 tests with zero failures/errors/skips. Application/test sources and configuration are unchanged since the verified Docker build; tests were not repeated for this documentation-only commit preparation. Whitespace, source headers and packaging evidence remain valid. Approved commit message: `feat: implement STC-003 structured story reviews`. The resulting commit identity is recorded in Git history. No push was requested.

## STC-004 — 2026-10-17

The maintainer explicitly requested implementation of STC-004 after STC-003 commit `756e4cb`. That request authorized the UI, deterministic browser checks, CI configuration and release documentation; no live-model request or paid API call was approved. The maintainer separately authorized a local commit subject to successful review on 2026-10-17.

### UI and behavior

Spring Boot now serves `index.html`, `styles.css` and `app.js` from the same process. The responsive page loads only configured choices and requires explicit selection. Empty/loading/model-list failure states disable submission appropriately and offer reload. An OpenRouter selection identifies the provider and possible charges. Story validation matches the 10–4,000-character server boundary; pending requests prevent duplicate submissions and selection/story changes. Requests use a 10-second model-list timeout and a 150-second review timeout (above the server's maximum configured 120 seconds), with no client retry or provider fallback.

Four labelled result sections render model text using `textContent`, not HTML. Zero questions have a clear empty message. Editing input or changing the model invalidates the displayed review. Copy Markdown creates headings/lists locally, escaping model Markdown/HTML metacharacters. Clipboard failure or absence exposes a labelled, selected read-only field for manual copy. Nothing is saved by the app. Labels, described errors, live status messages, visible keyboard focus and focus on the completed review support keyboard use.

### Maven, Docker and HTTP evidence

The existing Java 21 / Maven 3.9.16 / Spring Boot 4.1.1 / Spring AI 2.0.1 environment is unchanged. No dependency, backend provider logic, model setting or general build/configuration header changed.

```bash
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=StartupTests,ReviewsApiTests test
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp clean verify
docker compose config --quiet
APP_PORT=18081 OLLAMA_MODEL_1= OLLAMA_MODEL_2= OLLAMA_MODEL_3= OPENROUTER_API_KEY= OPENROUTER_MODEL_1= OPENROUTER_MODEL_2= OPENROUTER_MODEL_3= docker compose up --build -d
docker inspect story-check-ai-app-1 --format '{{json .NetworkSettings.Ports}} user={{.Config.User}} image={{.Image}}'
python3 /tmp/stc003-smoke.py
```

The focused suite passed **43 tests**. The full clean verification passed **163 tests, 0 failures, 0 errors, 0 skipped** and packaged the executable JAR. Startup tests verify CSS/JavaScript HTTP content types from the real application and that browser test sources are not served. Docker independently passed the same 163 tests and built image `sha256:2505ec1629cc79e9de407edebe38ff8cad9999861a57baa2464548f328226790`. It runs as `storycheck`, publishes only `127.0.0.1:18081 -> 8080/tcp`, and is left running with all model slots and the key empty. No `.env` was created or edited.

The existing STC-003 Python smoke script passed all **13 HTTP checks** again: HTML, health, empty models, invalid/oversized/unknown-model reviews, method/media type errors, absent fixtures and safe error fallback. Known negative-fixture log messages and the test-agent class-data-sharing warning are unchanged.

Final review corrected the startup test's browser-script URL from an unused `.spec.mjs` name to the actual `review-ui.mjs` filename. The focused rerun passed **all six startup tests** with no failures/errors/skips. Production sources are unchanged, so the verified image and browser evidence above remain current.

```bash
docker run --rm -v /mnt/c/P/ghpub/www-temp/story-check-ai:/workspace -v /tmp/stc001-m2:/root/.m2 -w /workspace maven:3.9.16-eclipse-temurin-21 mvn -B -ntp -Dtest=StartupTests test
```

### Actual Chromium checks with synthetic responses

Node 24.18.0, existing Playwright 1.62.1 and Chromium 151.0.7922.34 were used only as test tools; there is no Node runtime/build requirement for the app. Missing NSS/NSPR/ALSA libraries came from an already available temporary library directory. The first sandboxed browser launch was blocked by its socket restrictions; the authorized run outside that restriction succeeded. No host packages were installed.

```bash
/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node --check src/main/resources/static/app.js
/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node --check browser-tests/review-ui.mjs
PLAYWRIGHT_MODULE=/tmp/open-nook-onk001-browser/node_modules/playwright LD_LIBRARY_PATH=/tmp/task-flow-svg-check/browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node browser-tests/review-ui.mjs
```

All **20 scenarios passed** against the Spring Boot-served page. The first uses the real empty `/api/models` response; generation scenarios intercept API requests in the browser and supply fixed responses. These are real browser UI checks, not a live model or browser-to-provider end-to-end result. External origins are blocked by the harness.

Coverage: loading/empty/failing/malformed model lists and reload; explicit cloud selection/cost notice; literal brace/Unicode story payload; keyboard Tab/Enter flow and completed-review focus; safe literal HTML/script-like output; zero/nonzero questions; real clipboard write/read and denied/absent clipboard manual fallback; input limits without a request; pending controls/one submission; stale result reset; 422/429/502/503/504 messages; network/invalid JSON-contract output; accelerated client abort; long content/manual copy at 320px and 390px with no horizontal overflow. No uncaught browser errors occurred.

Desktop (1366px) and mobile screenshots were saved to `/tmp/stc004-browser-artifacts/`; desktop and 390px images were visually inspected. They contain synthetic fixture text, including deliberate HTML-injection test strings. They are not live AI output or promotional assets.

### CI, diagrams, links and review

`.github/workflows/verify.yml` defines push/PR Maven verification on Java 21 with read-only repository permissions, no persisted checkout credentials, blank production model/key settings and no secret references. It uses the verified official [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) and [setup-java v6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0) releases. A Python YAML structural audit passed. The identical Maven command passed locally and in Docker. **Hosted GitHub Actions: Not run** because no push was authorized; no hosted-run claim is made. Browser regression remains an optional local check.

```bash
PATH=/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin:$PATH npm install --prefix /tmp/stc004-doc-check --no-save --no-audit --no-fund mermaid@11.12.1
LD_LIBRARY_PATH=/tmp/task-flow-svg-check/browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/stc004-check-diagrams.cjs
git diff HEAD --check
```

The first Mermaid check found a pre-existing sequence-diagram semicolon that was parsed as a statement separator. Its label now says "Show review and copy Markdown". All **three Mermaid diagrams parsed and rendered** with Mermaid 11.12.1 in Chromium afterward. The temporary checker writes SVGs under `/tmp`; the npm tools are outside the repository and runtime image. Python audits passed for local Markdown link targets, all 33 source SPDX notices, header-free general configuration/CI files, new-file whitespace, and byte-for-byte static assets in the JAR. Browser tooling, fixtures and secrets are not packaged.

README, architecture, roadmap, model/API/testing guides and release status now describe implemented behavior with these explicit evidence limits. The final diff review found no blocking issue. No later story/release, database, framework, persisted history or public deployment was added.

### Unverified and approval boundary

**Live Ollama: Not run. Live OpenRouter: Not run.** No model was downloaded or called, no real cloud key was used, and no billable API charge occurred. Live model compatibility/quality, host-Ollama networking, native host Java startup, browsers other than Chromium, a hosted CI run and public deployment remain unverified.

### Commit review — 2026-10-17

Reviewed all 17 changed/new files with no blocking findings or unrelated changes. Current Surefire reports confirm 163 tests with zero failures/errors/skips, and packaged UI assets match the current sources byte for byte. The 20 browser scenarios, 13 HTTP checks and Docker verification above remain applicable; application and test sources are unchanged since their successful checks, so tests were not repeated for this documentation-only commit preparation. New-file headers/whitespace, CI configuration and local documentation links passed review. Approved commit message: `feat: implement STC-004 review UI and verification`. The resulting commit identity is recorded in Git history. No push was requested.

### Docker redeployment — 2026-10-17

The maintainer requested a Docker redeployment after commit `833255e`. The application/build sources were clean and unchanged. A maintainer-created, ignored `.env` now configures two Ollama and three OpenRouter model slots with a backend cloud key. Its contents were preserved and never printed. The existing localhost port 18081 was retained with an explicit override rather than switching to the Compose default 8080.

```bash
APP_PORT=18081 docker compose up --build --force-recreate --wait --wait-timeout 60 -d app
python3 /tmp/stc-redeploy-smoke.py
LD_LIBRARY_PATH=/tmp/task-flow-svg-check/browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/stc-redeploy-browser.cjs
```

Docker rebuilt the current source, independently passed **163 tests with zero failures/errors/skips**, packaged the JAR and recreated the app successfully. Current image: `sha256:204e148cbdc190ea8ac9fabd76c5af75e53a485ceb8cd8609fbb44d03c368337`; user: `storycheck`; binding: `127.0.0.1:18081 -> 8080/tcp`. No volume or other application stack was removed.

The temporary Python smoke compared the resolved Compose/runtime environment internally without printing secret values. It passed **11 HTTP checks**: exact health JSON, byte-for-byte current HTML/CSS/JS, the exact configured model registry (two Ollama and three OpenRouter choices, no key disclosure), and six safe review validation/method/media-type failures before any model call. Actual Chromium checks verified all five choices, explicit selection, the cloud charge notice and layouts at 1366/390/320 pixels; all POSTs and external origins were blocked by the browser harness, and no uncaught browser error occurred.

The app is running at `http://localhost:18081` with the existing `.env` loaded. Live Ollama/OpenRouter completion and model compatibility remain **Not run**; no model was downloaded and no billable API request was made. This entry records operational evidence only; application code and `.env` were not edited or committed during the redeployment.

### Branding and localhost-port adjustment — 2026-10-18

Maintenance adjustment to the existing release; no new story or release was added. The temporary STC-005 plan/index entry was removed. Existing maintainer branding metadata, SVG/PNG posters and README content were preserved and integrated.

Implemented: packaged `/brand/branding.json` and artwork; a matching hand-written SVG app icon and simplified SVG favicon; a header About button and native dialog with a 4px blurred/dimmed backdrop, original large SVG poster, metadata-driven description and author/project links, grouped support buttons, voluntary-support note, and current-year copyright/license in About and the page footer. Hover/focus tooltips show destination descriptions and full URLs, including hu | en | ro | de on the website/support links. Escape dismisses a tooltip before the dialog, keyboard focus stays inside About, and closing restores button focus without resetting the story/model/review. Brand loading errors leave the review flow available.

Compose and `.env.example` now default to host port `33070`; only `APP_PORT` was changed in the existing ignored `.env`, with a byte-level comparison confirming all other settings were preserved. Port availability was checked before startup. The app is running as `storycheck` with only `127.0.0.1:33070 -> 8080/tcp`, replacing the earlier `18081` binding. Original SVG/PNG poster hashes are unchanged. README, branding guide, model-port instructions and browser-test instructions were updated.

Actual commands:

```bash
docker compose config --quiet
docker compose build app
docker compose up -d --force-recreate --wait --wait-timeout 60 app
docker run --rm -d --name storycheck-branding-verification -p 127.0.0.1:33071:8080 -e APP_BIND_ADDRESS=0.0.0.0 story-check-ai-app
STC_BASE_URL=http://127.0.0.1:33071 STC_BROWSER_ARTIFACTS=/tmp/storycheck-branding-browser-artifacts STC_CHROMIUM_PATH=/home/zsolti-wsl/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome PLAYWRIGHT_MODULE=/tmp/cca008-browser/node_modules/playwright LD_LIBRARY_PATH=/tmp/type-nudge-linux-deps/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node browser-tests/review-ui.mjs
python3 /tmp/storycheck-branding-smoke.py
STC_CHROMIUM_PATH=/home/zsolti-wsl/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome LD_LIBRARY_PATH=/tmp/type-nudge-linux-deps/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/storycheck-artwork-preview.cjs
docker stop storycheck-branding-verification
git -c core.whitespace=cr-at-eol diff --check
```

Both Docker builds ran Maven `verify`: **167 tests, 0 failures, 0 errors, 0 skipped**, including startup HTTP checks for the packaged JSON, SVG content types and exclusion of the brand guide/browser fixtures. The first Chromium pass found that Shift+Tab from the initial control could leave dialog focus. Explicit Tab boundary handling was added; the final image was rebuilt, retested and redeployed. Final image: `sha256:f6a3fa309dca5557f8541169cf8b056abb7fb5d65133ae012fcc3e4e569f9b96`.

**27 Chromium scenarios passed**, covering the previous 20 review/copy/error/mobile scenarios plus metadata/link targets, poster decoding, actual backdrop blur, keyboard focus boundaries, Escape priority and return focus, hover/focus tooltip replacement and hoverable content, unchanged drafts/results, review completion while About remains open, branding-load failure isolation and About at 320/390 pixels without horizontal overflow. APIs use synthetic browser fixtures except the real empty model list on the temporary container. External origins are blocked. The final source-matching image is the same on the temporary test port and the configured production port; no real provider completion was invoked.

The runtime smoke against `33070` compared **9 HTTP assets and their JAR entries byte for byte** with current sources, checked health/model metadata, private-file 404s, non-root user, exact localhost port binding and the resolved runtime environment without printing credentials. The key was absent from application classes/resources. Desktop and 320px About screenshots and an SVG preview at 128/48/32/16px on light/dark backgrounds were visually inspected under `/tmp/storycheck-branding-browser-artifacts/`.

Browser tooling initially lacked its expected default executable; the existing Chromium 151.0.7922.34 was selected through the new optional `STC_CHROMIUM_PATH`. A sandboxed launch was blocked by socket restrictions; the authorized run succeeded using existing temporary libraries. No browser/tool dependency was added to the app or image.

Final review passed JavaScript syntax, SVG parsing/asset paths, new-source SPDX notices, local README links and CRLF-aware diff whitespace checks. No staged changes or additional story remain.

Live Ollama/OpenRouter requests, other browser engines, hosted CI and public deployment: **Not run**. No model download or paid API call occurred. The temporary verification container was stopped/removed; the configured app remains running on localhost `33070`. No commit or push was made. Proposed commit message: `fix: integrate branding and About with a localhost Docker port`.

### Normal-zoom layout and link polish — 2026-10-19

Maintenance adjustment in `styles.css` and `branding.js`: reduced header/intro/panel/footer spacing, bounded the starting story-field height while retaining native vertical resizing, removed empty-status margins and corrected character-count alignment. Desktop viewports up to 800px tall use tighter spacing. Content and review results remain naturally scrollable; no page zoom, transform or clipping workaround is applied.

Apache-2.0 links in the footer and About now use muted ochre (`#8a5a1f`) with no default underline; only hover adds the underline, while keyboard focus keeps its existing outline. The Ways to support tooltip has a 368px preferred maximum width, still clamped to the viewport/dialog. Its original wording/URL are unchanged. Language lists are an inline non-wrapping span, so German cannot wrap onto its own line on narrower screens.

Actual commands:

```bash
/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node --check src/main/resources/static/branding.js
docker compose build app
docker compose up -d --force-recreate --wait --wait-timeout 60 app
LD_LIBRARY_PATH=/tmp/type-nudge-linux-deps/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/storycheck-layout-adjustment.cjs
python3 /tmp/storycheck-branding-smoke.py
docker run --rm -d --name storycheck-layout-verification -p 127.0.0.1:33071:8080 -e APP_BIND_ADDRESS=0.0.0.0 story-check-ai-app
STC_BASE_URL=http://127.0.0.1:33071 STC_BROWSER_ARTIFACTS=/tmp/storycheck-layout-regression STC_CHROMIUM_PATH=/home/zsolti-wsl/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome PLAYWRIGHT_MODULE=/tmp/cca008-browser/node_modules/playwright LD_LIBRARY_PATH=/tmp/type-nudge-linux-deps/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node browser-tests/review-ui.mjs
docker stop storycheck-layout-verification
git -c core.whitespace=cr-at-eol diff --check
```

Docker build/Maven `verify`: **167 tests, zero failures/errors/skips**. Current image: `sha256:9e9c68e70471f7f7bb5281f401a4a16f77e83d9506ba6aabc922a334e0c512c0`. The configured app was recreated and remains bound only to `127.0.0.1:33070 -> 8080/tcp`; the temporary regression container was removed. This adjustment did not edit environment values or poster/icon assets.

The focused Chromium script passed **seven viewports** against the final Docker-served files, with synthetic model-list responses and all POST/external requests blocked. At normal zoom (`visualViewport.scale === 1`), the initial configured-model screen, including footer, fits without vertical or horizontal overflow at 1078×929, 1440×900, 1366×768, 1280×720 and 1024×768. Footer bottoms measured approximately 885.4, 892.0, 718.4, 709.8 and 738.9 CSS pixels respectively. The textarea remains vertically resizable. Normal mobile scrolling remains at 320×844 and 390×844 with no horizontal overflow.

Computed styles verified the ochre license color, no default underline and hover underline in both footer/About. DOM Range measurements verified two tooltip lines on desktop (description with all four languages, then the full URL). Mobile tooltips use three lines when needed, with all four language codes together and every tooltip inside its viewport. Desktop start/tooltip screenshots under `/tmp/storycheck-layout-adjustment/` were visually inspected. Source overrides were used during initial sizing experiments; the final checks above ran without overrides against the rebuilt app.

The existing **27 browser regression scenarios passed** again on the same final image with synthetic provider responses, including review/copy, error states, literal output, keyboard focus, Escape, About preservation and mobile bounds. The runtime smoke also passed: all **9 HTTP assets and JAR entries match source**, health/model metadata and private-file 404s are correct, environment matches resolved Compose without credential output, and original posters are unchanged. JavaScript syntax and the CRLF-aware diff check passed. No new story, persistent test tooling, commit, push or live provider request was added. Other browser engines, hosted CI and public deployment: **Not run**.

### Branding/layout commit review — 2026-10-19

The maintainer explicitly approved committing the completed maintenance adjustments. Reviewed the complete worktree and staged changes on `fix_patch_20261018`; no blocking issue was found. A credential audit covered 72 tracked/untracked files and all 20 staged files without printing values: configured secrets/private-key patterns were absent, `.env` remains ignored and unstaged, and the example API key is blank. Staged content matches reviewed source after normal Git line-ending normalization; existing worktree CRLF is preserved.

The final application/build/test sources are unchanged since the successful 167-test Maven build, 27 Chromium scenarios, seven-viewport layout check and runtime source/JAR comparison above. Those checks were not repeated for this documentation-only commit preparation. CRLF-aware staged whitespace checks passed. Approved commit message: `fix: integrate branding and polish the review layout`. Commit identity is recorded in Git history. No push was requested.

### Footer alignment and header polish — 2026-10-19

User-requested STC-004 maintenance in `index.html` and `styles.css`: use a viewport-height flex column so the initial desktop footer reaches the screen bottom, let the introductory sentence use the available width, remove the User story review demo badge and give About a rounded pale-green button with a decorative information icon. Content can still grow and scroll naturally, and the story field retains native vertical resizing.

Actual commands:

```bash
docker compose build app
docker compose up -d --force-recreate --wait --wait-timeout 60 app
LD_LIBRARY_PATH=/tmp/storycheck-browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/storycheck-footer-layout.cjs
docker compose ps
git -c core.autocrlf=true -c core.whitespace=cr-at-eol diff --check
```

The Docker build ran Maven `verify`: **167 tests, zero failures/errors/skips**. Image: `sha256:8f8831229615d216ba12dd78fefd22cd691f547997b50e50da793c041367f2c7`. The recreated app is running on `127.0.0.1:33070 -> 8080/tcp`. A Python `urllib.request` check confirmed that both changed HTTP assets match source bytes and `/api/health` returns `{"status":"ok"}`.

Focused Chromium checks passed **nine viewports** against the rebuilt app: 1529×964 (the supplied screenshot size), 1440×900, 1366×768, 1280×720, 1024×768, 1078×929, 1920×1080, 390×844 and 320×844. All seven desktop starting screens fit without scrolling at normal zoom; footer bottoms equal viewport heights, the author website ends 8 or 16px above the bottom, and the introductory sentence occupies one line. Mobile content wraps and scrolls with no horizontal overflow. Growing the desktop story field to 900px moves the footer below the content without overlap. About opens with Enter, closes with Escape and restores button focus at every tested size. No uncaught browser errors occurred. Desktop and 390px screenshots under `/tmp/storycheck-footer-layout/` were visually inspected.

The browser checks used synthetic model-list responses and blocked all non-GET and external requests. Temporary Playwright and extracted Chromium libraries live only under `/tmp`; no application dependencies were added. The diff review and CRLF-aware whitespace check passed, preserving existing CRLF source files. The full 27-scenario browser regression suite was not rerun for this small layout change. Live providers, other browser engines, hosted CI and public deployment: **Not run**. No commit or push was made. Proposed commit message: `fix: align the footer and polish header controls`.

### Simple About control and explicit Docker defaults — 2026-10-19

User-requested maintenance replaces the decorative About pill with a plain text button, retaining keyboard focus and a subtle hover background. The support notice keeps its exact wording, with an explicit `<br>` before the second sentence. `compose.yaml` now states `restart: "no"` explicitly; its existing localhost-only publication is retained. README documents restart behavior and clicking outside About. The existing backdrop-click handler in `branding.js` already supports dismissal and required no change.

Actual commands:

```bash
docker compose config --quiet
docker compose build app > /tmp/storycheck-final-build.log 2>&1
docker compose up -d --force-recreate --wait --wait-timeout 60 app
python3 /tmp/storycheck-compose-check.py
LD_LIBRARY_PATH=/tmp/storycheck-browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node /tmp/storycheck-simple-about.cjs
git -c core.autocrlf=true -c core.whitespace=cr-at-eol diff --check
```

Final Docker build/Maven `verify`: **167 tests, zero failures/errors/skips**. The final image is `sha256:2ff8c43084b7d54e0f705d885b22ebbe9b2cd3bddb2e3b0098474756f752683e`. An earlier build completed before the late support-text request; the final rebuild above includes that additional change. The app was recreated successfully. A credential-safe Python audit checked resolved Compose and runtime inspection: exactly `127.0.0.1:33070 -> 8080/tcp` in both port-binding fields, restart policy `{"Name":"no","MaximumRetryCount":0}`, no publish-all or host networking, and a running container. Both changed HTTP assets match current source bytes; `/api/health` is healthy. No Docker daemon restart was simulated.

Focused Chromium checks passed the same **nine desktop/mobile viewports** listed above. They verified the plain button has no icon, border, fill or shadow; Enter/Escape opening/closing and focus return; clicking inside keeps About open; clicking the backdrop closes it, restores page scrolling and preserves draft/model selection; and the support text has the requested rendered line break. The temporary check initially asserted scroll restoration before the native dialog's asynchronous `close` event; waiting for that event's visible effect resolved the check without application changes. Footer alignment, single-line desktop introduction, normal mobile scrolling and the vertically resizable story field still pass. No uncaught browser errors occurred. Desktop start/About and 390px About screenshots under `/tmp/storycheck-simple-about/` were visually inspected.

Model choices were synthetic browser fixtures; all non-GET and external browser requests were blocked. No credentials were printed or changed. The full browser regression suite, live model calls, other browser engines, hosted CI and public deployment: **Not run**. Existing CRLF files are preserved and diff whitespace checks passed. No commit or push was made. Proposed commit message: `fix: simplify About and make local Docker defaults explicit`.

### Shared branding structure — 2026-10-19

User-requested STC-004 maintenance adapts the supplied 15-field branding structure and field order to StoryCheck AI in `brand/branding.json`. Existing StoryCheck names, description, repository URL and artwork paths are retained. `appTagline` supplies the existing main heading, `supportRemarkText` supplies the exact support notice with its second sentence on a new line, and `authorWebsiteLanguages` supplies the website/support tooltip language list without hardcoded languages. Text is inserted through DOM text nodes. Empty language arrays omit availability notes, and a single-sentence support notice does not add a line break. The pre-existing removal of license metadata from the catalog is preserved; Apache-2.0 remains determined by the repository. Branding and root README documentation were updated.

Actual commands:

```bash
/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node --check src/main/resources/static/branding.js
/home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node --check browser-tests/review-ui.mjs
docker compose config --quiet
docker compose build app > /tmp/storycheck-branding-structure-build.log 2>&1
docker compose up -d --force-recreate --wait --wait-timeout 60 app
python3 /tmp/storycheck-branding-runtime.py
docker run --rm -d --name storycheck-branding-schema-check --restart=no -p 127.0.0.1:33071:8080 -e APP_BIND_ADDRESS=0.0.0.0 story-check-ai-app
STC_BASE_URL=http://127.0.0.1:33071 STC_BROWSER_ARTIFACTS=/tmp/storycheck-branding-structure-browser STC_CHROMIUM_PATH=/home/zsolti-wsl/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome PLAYWRIGHT_MODULE=/tmp/storycheck-layout-browser/node_modules/playwright LD_LIBRARY_PATH=/tmp/storycheck-browser-libs/extracted/usr/lib/x86_64-linux-gnu /home/zsolti-wsl/.nvm/versions/node/v24.18.0/bin/node browser-tests/review-ui.mjs
docker stop storycheck-branding-schema-check
git -c core.autocrlf=true -c core.whitespace=cr-at-eol diff --check
```

Docker build/Maven `verify`: **167 tests, zero failures/errors/skips**. Image: `sha256:1e98d78c87dd034a4af096fb36174c7653d085b13bdda4802d709604d10810cb`. The runtime audit confirms all **four HTML/CSS/branding JS/JSON HTTP assets match current source bytes**, health is OK, the only published address remains `127.0.0.1:33070 -> 8080/tcp` and restart policy remains `no`. The temporary app on localhost `33071` had no provider configuration and was removed after testing. Its first health poll preceded readiness and received a connection reset; the subsequent health check succeeded before browser execution.

**29 Chromium browser scenarios passed**, including the existing review/copy/error/About/mobile flows and two additional branding-fixture cases. Custom tagline/support text containing HTML-like content stays literal, ordered `fr | en` languages replace the default list in both modal and footer tooltips, empty languages omit availability notes, and one support sentence remains intact. Default support wording and the requested line break are verified. Desktop and 320px About screenshots under `/tmp/storycheck-branding-structure-browser/` were visually inspected. JavaScript syntax and diff checks passed; each edited file retains its current LF/CRLF format. No artwork, credentials, backend logic, additional service or frontend dependency was changed.

Provider responses were fixed browser fixtures; external origins were blocked. Live providers, other browser engines, hosted CI and public deployment: **Not run**. No commit or push was made. Proposed commit message: `fix: adopt the shared StoryCheck branding structure`.

### UI/branding maintenance commit review — 2026-10-19

The maintainer explicitly approved committing the completed layout, About, shared-branding and Docker-default adjustments on `fix_patch_20261018`. Reviewed all nine changed files across the worktree and index; no blocking issue was found. JavaScript syntax and CRLF-aware whitespace checks pass. A credential audit of 72 tracked/untracked files found no configured secrets or private-key patterns without exposing values; `.env` remains ignored and untracked. Staged content receives the same audit and is compared with reviewed source before the authorized commit.

The current runtime audit again confirms the final four UI/branding HTTP assets match source, health is OK, publication is only `127.0.0.1:33070` and restart policy is `no`. Application and browser-test sources are unchanged since the successful **167-test Maven build and 29 Chromium scenarios** above; those checks were not repeated for commit preparation. The earlier nine-viewport layout evidence remains recorded above. Approved commit message: `fix: polish review UI, branding and Docker defaults`. Commit identity is recorded in Git history. No push was requested.
