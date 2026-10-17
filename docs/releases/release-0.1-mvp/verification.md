# Release 0.1 verification

## Status

**In progress — STC-001 implemented and checked locally.** STC-002–004 remain Planned. The foundation has no model integration or story review UI, and the release is not complete.

## Evidence table

| Story | State | Implementation checks | Real provider / browser check | Notes |
| --- | --- | --- | --- | --- |
| STC-001 | Implemented | 22 JUnit/MockMvc/HTTP tests passed; executable JAR and Docker build passed; Compose config/start passed | Packaged app: 8 HTTP checks passed; visual browser and providers: Not run | Localhost-only port 18081; no native host JVM; local commit separately approved after review |
| STC-002 | Planned | Not run | Not run | Providers not implemented |
| STC-003 | Planned | Not run | Not run | Structured review not implemented |
| STC-004 | Planned | Not run | Not run | UI and CI not implemented |

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
