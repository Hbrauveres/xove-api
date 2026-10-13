# Contributing

## The flow

1. Every change starts as an issue and a written spec (numbered requirements and testable acceptance criteria), then a plan and small tasks. Code comes after they're approved.
2. Branch from an up-to-date `main`: `feat/…`, `fix/…`, `docs/…`, `ci/…`, `chore/…`.
3. Make the change **with its tests**, and run them locally ([Testing](docs/testing.md)).
4. Commit with [Conventional Commits](https://www.conventionalcommits.org/): `feat(room): free the seat when the sharer leaves`.
5. Push. **Every push runs the commit checks** (build, validate, tests, vulnerability scan, SAST), even before a pull request exists.
6. Open the pull request when the branch is green. It's **squash-merged**: its title becomes the one commit on `main`, so it must be a conventional commit too. Merging releases the API to staging; a version tag (`vX.Y.Z`) releases it to production.

## Rules that don't bend

- Tests come with every change.
- Never edit a Flyway migration that already ran: add a new one, compatible with the previous code (a rollback doesn't undo migrations).
- **The API changes first.** The API and the web app are released separately, so they can run at different versions. A change the web app needs ships here first, compatible with the web app that's live: new fields and endpoints are fine; renaming or removing one needs a release where both exist. The web release that uses it is tagged only once this one is live in production.
- No secrets, server details or personal data in code, config or docs. Settings are environment variables; their values live outside this repo.
- `docs/` changes in the same pull request as the behaviour it describes.
- A red check on `main` is fixed before anything else is merged.
