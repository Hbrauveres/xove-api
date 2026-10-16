# API

How to call the API, and where its exact description is. Every endpoint, its parameters, its request and response bodies, who may call it and its errors are in **[`openapi.json`](openapi.json)**, an OpenAPI 3.1 description generated from the controllers. A test fails when the file and the code differ ([Testing](testing.md#the-description-matches-the-code)), so it's always current.

## Every call

- **Same origin, under `/api`.** The web app and the API share one address; nothing else calls the API except LiveKit's webhook.
- **Signed in with a cookie.** Signing in with Google sets the `XOVE_SESSION` cookie; every call but the health check, sign-in and the webhook needs it. Without it: 401.
- **CSRF on every change.** `GET /api/me` (among others) sets a readable `XSRF-TOKEN` cookie; every `POST`, `PUT` and `DELETE` sends its value back in the `X-XSRF-TOKEN` header. Without it: 403 ([Security](security.md#csrf)).
- **Who may call it** is each operation's description: signed in, members, members with a seat, the stream's own person, admins. The wrong person gets 403.
- **Errors** from the API's own rules are RFC 9457 problem details (`application/problem+json`): `status`, `title`, a `detail` for the person, and sometimes a `reason` for the web app (`"full"` when the 6 places are taken). A malformed body or path value gets 400 the same way. Spring Security's answers are plainer: 401 (signed out) has no body, and a 403 for a missing CSRF token or a non-admin calling an admin endpoint has Spring's error body; a non-member calling a member endpoint gets problem details.
- **Polled every 2 seconds:** `POST /api/room/enter` while waiting for a seat, and `GET /api/streams` by every open room. A new field there reaches every open tab within a poll.
- **Changes are additive:** new fields and endpoints are fine; renaming or removing one takes a release where both exist, since the web app and the API are released separately. The web app ignores fields it doesn't know.

## The endpoints

A map of the description; the shapes and errors are in [`openapi.json`](openapi.json). The test also checks this table lists the same endpoints.

| Method + path | Who | Does |
| --- | --- | --- |
| `GET /api/health` | Anyone | Liveness (Actuator) |
| `GET /api/oauth2/authorization/google` | Anyone | Starts signing in with Google |
| `GET /api/login/oauth2/code/google` | Anyone | Google's callback; redirects to `/room` or `/request-access` |
| `POST /api/auth/logout` | Signed in | Ends the session; 204 |
| `GET /api/me` | Signed in | Who I am, and the CSRF cookie |
| `POST /api/access-requests` | Signed in, not a member | Asks for access, with an optional message |
| `GET /api/admin/access-requests` | Admins | The requests waiting |
| `POST /api/admin/access-requests/{id}/approve` | Admins | Makes the person a member |
| `POST /api/admin/access-requests/{id}/decline` | Admins | Declines; the person may ask again |
| `GET /api/admin/members` | Admins | The members |
| `DELETE /api/admin/members/{id}` | Admins | Removes a member and ends all their sessions |
| `POST /api/room/enter` | Members | Asks for a seat, or polls my place in the queue |
| `POST /api/room/accept` | Members | Takes the seat offered |
| `POST /api/room/cancel` | Members | Leaves the queue, or turns the offer down |
| `POST /api/room/leave` | Members | The waiting page is closing: keeps my place 30 seconds |
| `GET /api/streams` | Members | The live streams, the seats, who watches what and who's here |
| `POST /api/streams` | Members with a seat | Starts my screen or camera |
| `POST /api/streams/{kind}/settings` | Its person | Changes my stream's quality and mode |
| `POST /api/streams/{kind}/stop` | Members | Ends my stream of that kind |
| `PUT /api/streams/watching` | Members | What's on my stage |
| `POST /api/livekit/token` | Members with a seat | A token to join the video room |
| `POST /api/livekit/webhook` | LiveKit | The video room's events |

## What the description can't show

- **Signing in.** The first three rows above and logout are Spring Security's and Actuator's, not controllers, so they aren't in the description. "Continue with Google" goes to `/api/oauth2/authorization/google`; Google comes back to `/api/login/oauth2/code/google`, and the API redirects to `/room` (members) or `/request-access` (everyone else) ([Security](security.md#signing-in-with-google)).
- **The webhook's signature.** `POST /api/livekit/webhook` has no session: its `Authorization` header is a JWT that LiveKit signs with the secret it shares with the API, and its `sha256` claim must match the body exactly. Anything else gets 401 and changes nothing ([Security](security.md#livekit-webhooks)).

## Reading the description

- **On GitHub:** [`openapi.json`](openapi.json) itself. Each change to the API shows up as a change to this file in its pull request.
- **Interactive, on your machine:** with the API's settings exported and its database running ([Getting started](getting-started.md#run-the-api)), start it with the description on, then open its page (Swagger UI):

  ```bash
  XOVE_API_DOCS=true ./mvnw spring-boot:run
  # http://localhost:8080/api/docs/ui      the page
  # http://localhost:8080/api/docs         the description, as JSON
  ```

  Its "Try it out" sends your session cookie (sign in through the web app on `localhost` first) and, on changes, copies the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header, as the web app does. It can never do more than you could anyway.
- **From the checks:** each run keeps the description it generated, as the `openapi` artifact.

The servers never set `XOVE_API_DOCS`, so they serve neither the description nor the page: both addresses answer 404 there.
