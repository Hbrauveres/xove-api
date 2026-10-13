# API

Every endpoint the web app and LiveKit call. All paths are under `/api`, behind the same origin as the web app. Signed-in requests carry the session cookie; state-changing ones also the CSRF token (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`).

| Method + path | Who | Does |
| --- | --- | --- |
| `GET /api/health` | Anyone | Liveness |
| `GET /api/oauth2/authorization/google` | Anyone | Starts Google login |
| `GET /api/login/oauth2/code/google` | Anyone | Google callback; redirects to `/room` or `/request-access` |
| `POST /api/auth/logout` | Signed in | Ends the session |
| `GET /api/me` | Signed in | `{ email, name, avatarUrl, status, admin }` and the CSRF cookie |
| `POST /api/access-requests` | Signed in, not a member | Creates a request with an optional message |
| `GET /api/admin/access-requests` | Admin | Pending requests |
| `POST /api/admin/access-requests/{id}/approve` | Admin | Makes the user a member |
| `POST /api/admin/access-requests/{id}/decline` | Admin | Declines; the user can ask again |
| `GET /api/admin/members` | Admin | Members |
| `DELETE /api/admin/members/{id}` | Admin | Removes a member and ends all their sessions |
| `POST /api/room/enter` | Member | Asks for a seat, or polls the queue: `{ status: "in" }`, `{ status: "waiting", place }` or `{ status: "offered", until, seconds }`. Optional body `{ participantSid }` from a page already in the video room: confirms its seat after an API restart |
| `POST /api/room/accept` | Member | Takes the seat offered: `{ status: "in" }`; 409 when there's no offer (it ran out) |
| `POST /api/room/cancel` | Member | Leaves the queue, or turns the offer down; 204 |
| `POST /api/room/leave` | Member | The waiting page is closing: the place is kept 30 seconds; 204 |
| `GET /api/streams` | Member | `{ streams: [{ kind, userId, name, avatarUrl, since, settings: { quality, mode }, mine }], free, seated, seats: { total, taken, waiting }, watching: [{ userId, sharerId, kind, mine }], here: [{ userId, since, mine }] }`, streams oldest first; `watching` lists seated people watching live streams (spec 0104); `here` lists everyone with a seat and when it was first given, kept through a reload (spec 0158) |
| `PUT /api/streams/watching` | Member | What's on my stage (spec 0104). Body `{ sharerId, kind }`, or `{}` for an empty stage; 204, 400 for a bad kind. Kept in memory; leaving the room forgets it |
| `POST /api/streams` | Member with a seat | Starts a stream. Body `{ kind, participantSid, trackSid, quality?, mode? }`: `screen` or `camera`, the LiveKit connection and track it comes from (never shown to anyone; 400 without them), and what it's sent with (a missing value is 1080p Smooth for a screen, 720p Smooth for a camera; a camera can't be 1080p). Starting the same kind again replaces your own. 409 when the 6 places are taken (with `reason: "full"`), or without a seat |
| `POST /api/streams/{kind}/settings` | Its person | Changes what the stream is sent with. Body `{ quality, mode }`; 400 for an unknown value, 409 if you don't have that stream |
| `POST /api/streams/{kind}/stop` | Member | Ends your stream of that kind; nothing to stop is fine |
| `POST /api/livekit/token` | Member with a seat | `{ url, room, identity, token }` to join the video room; 409 without a seat |
| `POST /api/livekit/webhook` | LiveKit (signature, no login) | Room events; marks seats used, keeps them after a leave, ends streams whose connection left or track stopped. 401 on a bad signature |

Errors follow RFC 9457 (problem details); the web app shows their `detail` field.

