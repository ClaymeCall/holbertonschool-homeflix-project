# Contributing to HomeFlix

This page describes how the team works on the repository. It complements the [working agreement](docs/team/working-agreement.md), which covers roles, availability and communication.

## Workflow in short

1. Pick an issue (or create one) and make sure it has an owner.
2. Create a branch **from the issue**.
3. Commit with Conventional Commits.
4. Open a pull request that links the issue.
5. Get one review from another learner, then merge.

Nobody pushes directly to `main`.

## Issues

- One issue per task, with one owner.
- Use the issue templates (task or bug). Every task has a **Done when** checklist, and the work is finished when every box can be ticked.
- Labels: a priority (`P0`, `P1`, `P2`) and an area (`process`, `requirements`, `architecture`, `ai-agent`, `infra`, `risk`, `documentation`).

## Branches

Create the branch from the issue page on GitHub (**Development**, then **Create a branch**). GitHub names it automatically from the issue number and title, for example:

```
2-repository-workflow-branches-commits-pr-template-review-rules
```

Always branch from an up-to-date `main`. Keep branches short-lived and focused on their issue.

## Commits

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <short description>

<optional body: what and why>
```

**Types**

| Type | Use for |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `test` | Adding or changing tests |
| `refactor` | Code change that is neither a fix nor a feature |
| `perf` | Performance improvement |
| `style` | Formatting only, no behavior change |
| `build` | Build system, dependencies, Dockerfiles, `compose.yaml` |
| `ci` | Continuous integration configuration |
| `chore` | Anything else that does not touch the product (for example repo housekeeping) |

**Scopes** are optional. Suggested ones: `identity`, `library`, `acquisition`, `frontend`, `gateway`, `infra`.

**Rules**

- Write the description in the imperative, in lowercase, without a final period, and keep the first line under 72 characters.
- Mark a breaking change with `!` after the type or scope (for example `feat(identity)!: ...`) and explain it in the body.
- One logical change per commit.

**Examples**

```
feat(library): add idempotent folder scan
fix(identity): reject sign-in for disabled accounts
docs: add data ownership map
build(infra): add postgres healthcheck to compose.yaml
```

## Pull requests

- Open the PR against `main` and fill in the PR template.
- Link the issue with `Closes #<number>` so it closes on merge.
- Keep PRs small enough to review in one sitting.
- Explain how you tested the change.
- Make sure the CI checks pass once CI is in place.

### Reviews

Every PR needs **at least one review from another learner**, and the author does not merge their own PR without it. A review can be done in a **Discord call with screen sharing**, or by asking a teammate to review through the Discord conversation. Either way, the reviewer records the result on the PR (approve or request changes).

A reviewer checks that:

- the issue's **Done when** criteria are met;
- the change does what the PR says, and nothing unrelated;
- tests exist for new business rules, and they pass;
- documentation is updated where needed;
- any architecture decision in the change has an ADR (see below);
- no secret, credential, personal data or media file is committed;
- authorization and input validation are enforced on the server, not only in the interface.

## Secrets and files that must not be committed

- Never commit real secrets, keys, passwords or tokens. Only `.env.example` with placeholders belongs in the repository, and real `.env` files stay local.
- Do not commit media files unless they are the authorized demonstration files, with their source and licence documented.

## Architecture decisions

**Every architecture decision requires an ADR** (Architecture Decision Record) in [`docs/adr/`](docs/adr/README.md). This covers choosing or replacing a technology that others depend on, service boundaries and data ownership, service communication, identity and security behavior, API conventions, deployment and infrastructure, cross-cutting conventions, and reversing an earlier ADR. The [ADR README](docs/adr/README.md) lists what counts and what does not.

- Write the ADR **before or in the same pull request** as the code or documents that depend on it. Copy [`docs/adr/template.md`](docs/adr/template.md).
- An ADR is **accepted when its pull request is approved by another learner and merged**.
- Do not edit an accepted ADR to change a decision. Write a new ADR that supersedes it.
- Reviewers **must reject** a pull request that contains an architecture decision without an ADR.

## Documentation

Update the relevant document in the same PR as the change. [`docs/architecture/tech-stack.md`](docs/architecture/tech-stack.md) summarizes the technology choices and links to their ADRs.
