# xove-api — notes for Claude

This repo is worked on from the private `xove-workspace` repo, where it's the submodule `repos/xove-api`. The workspace's `CLAUDE.md` holds every rule (workflow, git, secrets, tests); its `specs/`, decisions and the cross-repo contracts are there. Read those first.

This repo's own `docs/` (architecture, API, getting started, testing) and `README.md` describe the code: update them in the same PR as the code they describe. They're public: nothing about servers, secrets or the process.

Specific to this repo:

- **Frozen until xove-workspace#82:** `xove` still builds and deploys the API from its own `api/` folder. Until the releases move here, the API doesn't change in either repo (no features, fixes or Dependabot merges), so the two copies stay the same.
- Tests: `./mvnw test` (Docker running, for Testcontainers).
- Never push to `main`; PRs are squash-merged with a conventional-commit title.
- Never edit a Flyway migration that already ran.
- Never read `.env` files or print a secret.
