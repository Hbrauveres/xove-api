# Testing

Every change is tested automatically in the [checks](../.github/workflows/ci.yml) before it can be merged, and you can run the same tests locally.

## At a glance

| Where | Tool | What it covers | Run it |
| --- | --- | --- | --- |
| Code | JUnit 5, AssertJ, MockMvc, spring-security-test, Testcontainers | Business rules, HTTP endpoints, security, database constraints | `./mvnw test` |
| Build | Docker, Compose | The image builds and the API starts healthy with a database, and refuses anonymous requests | Checks, stage 2 |
| Security | Trivy, Semgrep | Known vulnerabilities, risky code patterns | Checks, stage 3 |

## API tests

Three kinds, from fastest to slowest. Each one tests what it must at the lowest cost.

### Plain unit tests

No Spring, no database: a class and its rules. Used for the room's logic (`RoomSeats`, `Streams`, `Watching`) and LiveKit's tokens and signatures (`LiveKitTokens`, `LiveKitWebhookVerifier`). They run in milliseconds.

Time is a constructor argument of `RoomSeats` and `Streams`: the tests pass a `MutableClock` and move it, so a 30-second rule is tested without waiting 30 seconds. From `RoomSeatsTest` (two seats; Ana and Bruno are in, Caio waits):

```java
@Test
void closingTheTabKeepsTheSeatFor30Seconds() {
    fillTheRoom();
    seats.enter(CAIO);

    seats.left(ANA, "PA_ana", true);
    wait(29);
    seats.enter(CAIO);
    assertThat(seats.isSeated(ANA)).isTrue();
    assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));

    wait(1);
    assertThat(seats.isSeated(ANA)).isFalse();
}
```

`wait(29)` is `clock.advance(Duration.ofSeconds(29))`. Also: `StreamsTest` (the 6 places), `LiveKitTokensTest` (a token's signature, grants and expiry), `LiveKitWebhookVerifierTest` (every way a signature is refused).

### Web layer tests

Real HTTP requests through the whole app with MockMvc: the Spring Security filters, the controller, the logic and the database, without a server or a browser. Used for every endpoint: who may call it, what it answers, and what it changes. `spring-security-test` signs the request in as a Google user (`oidcLogin()`) and adds the CSRF token (`csrf()`), or leaves it out to prove it's needed.

From `AccessFlowTest`:

```java
@Test
void removingAMemberRevokesAccessAndSignsThemOut() throws Exception {
    friend.changeStatus(UserStatus.MEMBER);
    users.saveAndFlush(friend);
    saveSessionFor("sub-friend");
    assertThat(sessions.findByPrincipalName("sub-friend")).hasSize(1);

    mvc.perform(delete("/api/admin/members/" + friend.getId()).with(asAdmin()).with(csrf()))
            .andExpect(status().isNoContent());

    assertThat(statusOf(friend)).isEqualTo(UserStatus.NONE);
    assertThat(sessions.findByPrincipalName("sub-friend")).isEmpty();
}
```

`asAdmin()` is `oidcLogin()` as the admin's Google id with the `ADMIN` role; the test class lists the admin's email in `xove.admin-emails` (`@TestPropertySource`) and signs the admin in once before each test. Also: `StreamsControllerTest`, `RoomControllerTest`, `LiveKitControllerTest`, `LiveKitWebhookControllerTest` (events signed with a test key by `WebhookSigner`), `AuthTest`.

Tests that need a real server start the app on a random port and send plain HTTP: `HealthEndpointTest`, and `ErrorStatusTest`, since MockMvc never forwards a refused request to the error page as a running server does (a signed-out change without the CSRF token gets 403, not a 401 for the error page).

### Persistence tests

Against a real PostgreSQL 17, started in Docker by [Testcontainers](https://testcontainers.com) (`TestcontainersConfiguration`, `@ServiceConnection` points Spring at it). Used for what only the database can prove: its constraints and the migrations. An in-memory database would accept SQL PostgreSQL refuses, and the other way round.

From `PersistenceTest`:

```java
@Test
void allowsOnlyOnePendingRequestPerUser() {
    User user = users.saveAndFlush(new User("b@gmail.com", "B", null));
    requests.saveAndFlush(new AccessRequest(user, "let me in"));
    assertThatThrownBy(() -> requests.saveAndFlush(new AccessRequest(user, "again")))
            .isInstanceOf(DataIntegrityViolationException.class);
}
```

The partial unique index from `V1` refuses the second pending request ([Data model](data-model.md#what-the-database-guarantees-itself)). Every test that starts the app with its database applies these migrations first, so a migration that doesn't apply fails them all.

### The description matches the code

`OpenApiDescriptionTest` starts the API with its description on, asks for it, and compares it with the committed [`openapi.json`](openapi.json); it also checks that [API](api.md)'s table lists the same endpoints. When an endpoint changes, it fails until the file is written again:

```bash
./mvnw test -Dtest=OpenApiDescriptionTest -Dopenapi.write=true
```

So a pull request that changes the API also shows the change in `openapi.json`. It and `ApiDocsOffTest` (the description is off by default) start the app on a random port and send plain HTTP, like `HealthEndpointTest`. Each run writes what it generated to `target/openapi.json`, kept with the checks' test reports.

### Running them

Docker must be running for Testcontainers. The first run downloads the `postgres:17-alpine` image.

```bash
./mvnw test                          # everything
./mvnw test -Dtest='Room*'           # one area
./mvnw test -Dtest=AccessFlowTest    # one class
```

Most Spring tests are `@Transactional`, so each one rolls back its data and they can run in any order. Test classes with different settings (`@TestPropertySource`) get their own application context automatically. `CsrfCookieTest` asks for a fresh one (`@DirtiesContext`), because the other classes' `csrf()` helper replaces the CSRF token store with one that never writes cookies, and this test checks the cookie.

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
