# Getting started

How to run the API on your own machine. On Windows, do all of this inside WSL.

## Prerequisites

| Tool | Version | Why |
| --- | --- | --- |
| Git | any recent | |
| JDK | 21 (Temurin recommended) | The Maven wrapper (`./mvnw`) downloads Maven itself |
| Docker | Docker Desktop with WSL integration, or Docker Engine on Linux | The tests start a real PostgreSQL with Testcontainers; the API with a database runs in Compose |

## Clone

```bash
git clone https://github.com/Hbrauveres/xove-api.git
cd xove-api
```

## Settings

The API reads its settings from environment variables (the table at the end). The maintainers export them into each new terminal with their private tools, the same way the servers get theirs; nothing is written to disk. Anyone else sets the same variables by hand: a Google OAuth client, a local PostgreSQL and a LiveKit server (`livekit-server --dev`).

## Run the API

**1. A database**, a throwaway container:

```bash
docker run -d --name xove-dev-db -p 5432:5432 \
  -e POSTGRES_DB="$POSTGRES_DB" -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  postgres:17-alpine
```

An older `xove-dev-db` made with another password won't accept the new one: remove it first (`docker rm -f xove-dev-db`).

**2. Start it:**

```bash
./mvnw spring-boot:run          # http://localhost:8080/api/health
```

Flyway creates the tables on the first start. Your email in `ADMIN_EMAILS` makes you an admin and a member. The Google client must allow `http://localhost:5173`, the web app's address, where sign-in comes back.

With the web app running too (see [`xove-web`'s Getting started](https://github.com/Hbrauveres/xove-web/blob/main/docs/getting-started.md)), open http://localhost:5173, sign in with Google, and you land in the room.

**3. Video (optional).** Sharing needs a LiveKit server. Run one in development mode with your dev key pair:

```bash
docker run -d --name xove-dev-livekit -p 7880:7880 -p 7881:7881 -p 7882:7882/udp \
  livekit/livekit-server:v1.13.7 --dev --bind 0.0.0.0 \
  --keys "$LIVEKIT_API_KEY: $LIVEKIT_API_SECRET"
```

Restart the API after starting it. Without LiveKit everything works except video, and the room shows "Video offline". This dev server sends no webhooks, so a closed tab's streams stay listed until the page comes back, and a seat isn't confirmed by LiveKit: it runs out after a minute and the open page asks for it again with its video connection, which confirms it. On the servers, LiveKit runs one per environment, with webhooks.

## Run it in Docker with a database

`compose.yaml` is the file the servers deploy; `compose.local.yaml` adds a throwaway database (on servers the database runs outside this repo). With the variables exported above, and `POSTGRES_HOST=db`:

```bash
docker network create web                  # once; shared with the reverse proxy on servers
docker network create "xove-${APP_ENV}-db"  # once; the database's private network
docker compose -f compose.yaml -f compose.local.yaml up -d --build
docker compose -f compose.yaml -f compose.local.yaml ps
```

Its database is named `xove-${APP_ENV}-db`, like the one `docker run` started under "Run the API" when `APP_ENV` is `dev`: stop that one first (`docker stop xove-dev-db`), or use another `APP_ENV`. This is mainly useful to check that the image builds and starts healthy; the checks' "Validate build" does the same.

## Environment variables

| Name | Used by | Meaning |
| --- | --- | --- |
| `APP_ENV` | Compose | `stage`, `prod`, or anything locally; goes into container names |
| `IMAGE_TAG` | Compose | Which published image version to run; set by the deploy script, per app. Unset means "build locally" |
| `POSTGRES_HOST` | Compose | Where the database is, for the API's `SPRING_DATASOURCE_URL`: on servers the database's host on its private network, with `compose.local.yaml` `db`. Run directly, the API reads `SPRING_DATASOURCE_URL` itself |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | db, api | Database name and credentials |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | api | Google OAuth client |
| `ADMIN_EMAILS` | api | Comma-separated admin emails |
| `LIVEKIT_URL` | api | Address browsers use to reach LiveKit (`wss://…` on servers) |
| `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | api | The pair shared with that environment's LiveKit: the API signs tokens with it, LiveKit checks them and signs its webhooks. Secret ≥ 32 characters |
| `LIVEKIT_ROOM` | api | This environment's room, a different one per environment; defaults to `xove` |
| `XOVE_ROOM_SEATS` | api | People in the room at once, sharers included: 1 to 20 (fewer on a test environment, to try the queue); defaults to 20. Anything else stops the API at start-up |

Their values live outside this repo, one set per environment. There's no `.env` here: each server builds its own.

## Next

- Run the tests: [Testing](testing.md)
- Open a pull request: [Contributing](../CONTRIBUTING.md)
