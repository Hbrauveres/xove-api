# Xovê API

The API of [Xovê](https://xove.app): a private room where invited friends share their screens and cameras and watch each other live, up to 6 streams at once, 20 people in the room, with a queue when it's full. This repo is the server side; the web app is in [`xove-web`](https://github.com/Hbrauveres/xove-web).

## What it does

- **Who gets in:** Google sign-in, access requests, and an admin who approves them. Sessions live in the database, so removing a member ends their sessions at once.
- **The room:** 20 seats, sharers included, with a queue and a seat offered in turn when one frees up.
- **The streams:** up to 6 live screens and cameras, their quality and mode, and who's watching what.
- **LiveKit:** signs the tokens that let a seated member join the video room, and follows the room through LiveKit's signed webhooks. The video itself never goes through the API.

## How it's built

| Part | Tech |
| --- | --- |
| Code | Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Actuator), Maven |
| Data | PostgreSQL 17, schema by Flyway, server-side sessions (Spring Session JDBC) |
| Video | Tokens and webhooks for a self-hosted [LiveKit](https://livekit.io) server (a WebRTC SFU) |
| Delivery | GitHub Actions: on every push, build the image, start it with a database and check it's healthy, JUnit with Testcontainers, Trivy and Semgrep; on every merge, publish it and deploy it to staging, where a new version starts next to the serving one and takes over only once it's healthy; a version tag releases it to production, with a rollback button |

```mermaid
flowchart LR
  B[Browser<br/>React app] -->|HTTPS| P[Reverse proxy]
  P -->|/api/*| A[API<br/>this repo]
  A --> D[(PostgreSQL)]
  L[LiveKit] -->|signed webhooks| A
  B -.->|video over WebRTC| L
```

## Design choices

| Choice | Why |
| --- | --- |
| **Sessions in PostgreSQL** (Spring Session JDBC), not tokens in the browser | Removing a member deletes their sessions, so they're signed out everywhere at once; a deploy signs nobody out |
| **The room's state in memory**, in plain Java classes with an injected clock | Seats, the queue and streams change every second and only matter while people are connected; one instance serves a handful of people. Plain Java keeps the rules apart from Spring, and the clock lets the tests move time instead of sleeping |
| **LiveKit carries the video;** the API only signs tokens and reads webhooks | Media never touches the API. A token says exactly what a browser may do in which room, and LiveKit's signed webhooks tell the API when someone leaves or stops sharing |
| **Tests against a real PostgreSQL** (Testcontainers) | Constraints like "one pending request per user" and the migrations themselves are proven on the same PostgreSQL version that runs in production |
| **Errors as RFC 9457 problem details** | One error shape for every endpoint; its `detail` is the message the web app shows |

More in the docs: [Architecture](docs/architecture.md) (modules, a request's path, memory and the database), [Data model](docs/data-model.md), [Security](docs/security.md), [API](docs/api.md) (every endpoint), [Testing](docs/testing.md).

This repo was split out of the original `xove` repo with its history: a commit's `(Hbrauveres/xove#n)` is that repo's pull request.

## Run it and test it

```bash
./mvnw spring-boot:run          # the API (needs its settings, see Getting started)
docker compose -f compose.yaml -f compose.local.yaml up   # with a throwaway database
./mvnw test                     # JUnit, MockMvc, Testcontainers (needs Docker)
```

Details in [Getting started](docs/getting-started.md) and [Testing](docs/testing.md).

## How changes are made

Every change starts as a written spec with numbered requirements and acceptance criteria, then a plan and small tasks; the pull request proves each criterion with tests, and the checks run on every commit. See [CONTRIBUTING.md](CONTRIBUTING.md). The specs, decisions and operations notes are kept in a private workspace; the history and the pull requests here show the result.
