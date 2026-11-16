# Team Working Agreement

> **Draft.** Items marked **TBD** need to be filled in or confirmed by the team.

## 1. Team

| Learner | GitHub | Service lead | Other main responsibility |
|---|---|---|---|
| Clement Callejon | [@ClaymeCall](https://github.com/ClaymeCall) | Library and playback | DevOps: Docker, environment and configuration, project and repository management |
| Benjamin Bommier | [@Benji3106](https://github.com/Benji3106) | Identity and profiles | AI agent maintainer and supervisor |
| Thomas Rousseau | [@tomsonne](https://github.com/tomsonne) | Acquisition | Frontend: initial design and conception |

Everyone also writes code across the whole project. Only the three microservices have a clearly assigned lead. Responsibilities may rotate, but **every learner must contribute to development, understand the global architecture, and be able to explain their own work** at the oral presentation.

Service ownership (one lead per service, everyone reviews the others):

- **Identity and profiles:** accounts, authentication, roles, and viewing profiles. Lead: Benjamin.
- **Library and playback:** indexing, catalogue, video access, and viewing progress. Lead: Clement.
- **Acquisition:** authorized downloads, status tracking, and handoff of completed files. Lead: Thomas.

### Availability

- **Project dates:** 16/11/2026 to 26/11/2026.
- **Hours:** everyone works at least 9:00 to 17:00. When necessary, work can continue in the evening, until 22:00 at the latest.
- **Location:** everyone works remotely, except on Fridays (on site).
- **Known absences:** Thomas is not available on the first day (16/11/2026).

## 2. Communication and rituals

- **Daily check-in** (short): what was done, what blocks, what is next. No fixed time: the team meets on a Discord call when needed. Where: Discord.
- **Channel** for day-to-day questions: Discord.
- **Every architecture decision requires an ADR** in [`docs/adr/`](../adr/README.md), accepted through a reviewed pull request (see [CONTRIBUTING.md](../../CONTRIBUTING.md#architecture-decisions)). Other decisions are noted in the relevant issue.
- **Integrated review** on day 5 and **acceptance run** on day 9, as per the project schedule.

## 3. Working with Git

- Work is tracked in **GitHub Issues**, with one issue per task and one owner.
- One **branch per issue**, branched from `main`. Branches are created from the issue itself with GitHub's "Create a branch" option, which keeps GitHub's automatic naming (details in [CONTRIBUTING.md](../../CONTRIBUTING.md)).
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (for example `feat: add library scan endpoint`).
- No direct pushes to `main`: every change goes through a **pull request** with at least **one review from another learner**.
- Reviews happen either in a **Discord call with screen sharing**, or by asking a teammate to review through the Discord conversation.
- A pull request links its issue and says how it was tested.
- **No secrets in the repository.** Only `.env.example` with placeholders is committed.

## 4. Definition of done

A task is done when:

- the acceptance criteria in its issue are met;
- the code builds, and the relevant tests pass;
- the change is reviewed and merged;
- affected documentation is updated.

## 5. Quality and ethics rules

- A reliable MVP comes before optional features.
- Only authorized, redistributable content is used for demos. Source and licence are recorded for every demo file.
- Everyone can explain what they merge, including code written with AI assistance.

## 6. AI agent role

**Role: API and Documentation Consistency Reviewer** (see [#18](../../../../issues/18)). The full, provider-agnostic persona is in [`ai-agent-persona.md`](ai-agent-persona.md). Which AI tool and model runs it is recorded in each report.

| Item | Definition |
|---|---|
| **Objective** | Find mismatches between the documentation (README, `docs/`, OpenAPI files, acceptance criteria) and the implemented code and configuration |
| **Inputs** | Repository documentation, OpenAPI files, `raw_tasks.md`, source code, `compose.yaml`, Dockerfiles, `.env.example`, migrations, tests |
| **Permitted tools** | Read, search and list files only. No command execution, no edits, no network. Enforced in the tool where possible, otherwise by running on a clean clone without secrets |
| **Boundaries** | No access to secrets (`.env`, keys, tokens), no changes to authentication or authorization, no touching media files, no downloads, no merge, publish or deploy |
| **Deliverable** | One findings report per run, with file and line references, severity, reproduction evidence, suggested correction and confidence |
| **Maintainer and supervisor** | Benjamin Bommier. He maintains the persona file, runs the agent, and checks its reports |
| **Human review** | Each finding is verified against the code by a learner and marked accepted, rejected or deferred in the report. No change is made on the agent's word alone, and every change goes through a normal pull request |
| **Runs** | Planned before the day 5 integrated review and before the day 9 acceptance run |
| **Attribution** | Reports are committed in `docs/team/ai-agent-reports/` and state that they were generated by the agent. The human reviewer's decisions are recorded in the same file |

## 7. Conflict handling

- Disagreements are discussed first in the issue or pull request.
- If still unresolved after discussion, the team decides by majority vote, and the decision is noted in the issue.
- Blockers are raised at the daily check-in rather than waited out.
