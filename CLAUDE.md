# xove-api — notes for Claude

This repo is worked on from the private `xove-workspace` repo, where it's the submodule `repos/xove-api`. The workspace's `CLAUDE.md` holds every rule (workflow, git, secrets, tests); its `specs/`, decisions and the cross-repo contracts are there. Read those first.

This repo's own `docs/` (architecture, data model, security, API, getting started, testing) and `README.md` describe the code: update them in the same PR as the code they describe. They're public: nothing about servers, secrets or the process.

Specific to this repo:

- Every merge to `main` releases the API to staging (`stage.yml`); a version tag `vX.Y.Z` releases it to production (`release.yml`). The API changes first: see the version rule in `release.yml`'s header.
- Tests: `./mvnw test` (Docker running, for Testcontainers).
- Never push to `main`; PRs are squash-merged with a conventional-commit title.
- Never edit a Flyway migration that already ran.
- Never read `.env` files or print a secret.
