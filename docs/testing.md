# Testing

Every change is tested automatically in the [checks](../.github/workflows/ci.yml) before it can be merged, and you can run the same tests locally.

## At a glance

| Where | Tool | What it covers | Run it |
| --- | --- | --- | --- |
| Code | JUnit 5, AssertJ, MockMvc, spring-security-test, Testcontainers | Business rules, HTTP endpoints, security, database constraints | `./mvnw test` |
| Build | Docker, Compose | The image builds and the API starts healthy with a database, and refuses anonymous requests | Checks, stage 2 |
| Security | Trivy, Semgrep | Known vulnerabilities, risky code patterns | Checks, stage 3 |

## API tests

Three kinds, from fastest to slowest:

- **Plain unit tests**, no Spring and no database. Example: `RoomSeatsTest` checks the seats and the queue (30 and 60 seconds kept, the seat offer, no answer to the end of the line) with a clock the test moves (`MutableClock`), so nothing sleeps. `StreamsTest` checks the 6 places. `LiveKitTokensTest` checks a token's signature, claims and expiry.
- **Web layer tests** with MockMvc and `spring-security-test`. They send real HTTP requests through the whole Spring Security filter chain as a signed-in user (`oidcLogin()`), with or without a CSRF token (`csrf()`), and check status codes and JSON. Example: `StreamsControllerTest`, `RoomControllerTest`, `AccessFlowTest`.
- **Persistence tests** against a real PostgreSQL started by Testcontainers. They check what only a real database can: unique emails, "one pending request per user", migrations applying cleanly.

Docker must be running for Testcontainers. The first run downloads the `postgres:17-alpine` image.

```bash
./mvnw test                          # everything
./mvnw test -Dtest='Screen*'         # one area
./mvnw test -Dtest=AccessFlowTest    # one class
```

Most Spring tests are `@Transactional`, so each one rolls back its data and they can run in any order. A test that changes Spring's configuration (like `CsrfCookieTest`) gets its own application context with `@DirtiesContext`.

## What a good test here looks like

- Named after the behaviour, as a sentence: `noAnswerIn60SecondsMovesYouToTheEndAndOffersTheNext`.
- Arrange, act, assert, separated by blank lines.
- Asserts on what a user or client sees (text, status codes, JSON), not on internals.
- No sleeps: inject a clock.

## Before opening a pull request

```bash
./mvnw test
```

If that's green, the checks' unit test stage will be too.
