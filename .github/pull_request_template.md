<!-- Title: a conventional commit, e.g. "feat(api): free the screen when the sharer leaves". It becomes the commit message on main (squash merge). -->

**Issue:** Hbrauveres/xove-workspace#

**Spec:** `xove-workspace/specs/<folder>/spec.md`

## What changed

<!-- A few lines: what the user sees differently, and the main code changes. -->

## Acceptance criteria

<!-- Copy every AC from the spec. Tick it and say how it's proven. -->

- [ ] AC-1 … — proven by `…Test#…`
- [ ] AC-2 … — manual check on staging after merge

## Docs

<!-- What `scripts/docs-check` (in the workspace) asked for, and what changed: e.g. "docs/api.md: the new endpoint". If an area needs no doc change, say why. -->

## Definition of Done

- [ ] Every task in `tasks.md` is ticked
- [ ] Commit checks are green
- [ ] Database changes are new migrations, compatible with the previous code
- [ ] `docs/` updated in this PR (or nothing to update); contracts and decisions in the workspace PR
- [ ] No secrets, server details or personal data in the diff

## After merge

<!-- Manual checks to do on staging, if any. -->
