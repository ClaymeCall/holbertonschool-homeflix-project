# Mandatory Requirements Checklist

This checklist turns the project brief into numbered, testable requirements. It is derived from [`raw_tasks.md`](../../raw_tasks.md), which stays the source of truth. If this page and the brief disagree, the brief wins and this page gets fixed.

## How to read this page

- Every requirement has an **ID**, which other documents, issues, tests and acceptance scenarios refer to.
- **Source** says where it comes from in `raw_tasks.md`: `Brief` followed by the section name, or `T<n> s<m>` for task *n*, step *m* (`T<n>` alone means that task's success criteria).
- **Phase** says when it is built, following the brief's two-week schedule:

| Phase | Meaning | Days |
|---|---|---|
| **S0** | Sprint 0: scope, design, contracts, runnable foundation | Day 1 |
| **V** | Vertical slice: sign-in, indexing, catalogue, first playback, in Docker | Days 2 to 4 |
| **M** | MVP completion: profiles, resume, favourites, acquisition, administration | Days 6 to 8 |
| **A** | Acceptance run, fixes, installation on another machine, delivery, presentation | Days 9 to 10 |
| **R** | Rolling: applies all through the project | all |

Day 5 is the integrated review, where priorities are adjusted.

- Every requirement here is **mandatory**. What is optional or excluded is listed separately in [Out of scope](#out-of-scope).
- Progress (done or open) is tracked in the GitHub issues and the backlog, not here.

## Non-negotiable requirements

The brief states that failing any of these is a **major non-compliance**:

- Java and Spring Boot for all business microservices ([ARC-01](#architecture)).
- Three genuine business microservices ([ARC-01](#architecture), [ARC-02](#architecture)).
- Docker Compose ([OPS-01](#docker-installation-and-operations)).

The brief also requires a **real** peer-to-peer transfer in the demo. A simulation does not count ([ACQ-14](#acquisition)).

---

## General and product

| ID | Requirement | Source | Phase |
|---|---|---|---|
| GEN-01 | HomeFlix is a self-hosted video streaming platform for a household. It serves videos stored on a computer, a personal server, a NAS, or storage attached to a home router and reachable from the host. | Brief: Description | R |
| GEN-02 | Deliver an installable MVP that connects to an authorized media folder and supports the journey from first-time setup to video playback and resume. | Brief: Product mission | V |
| GEN-03 | The main journey (setup, indexing, sign-in, profile, find, play, resume) is available **through the interface**, not only through API calls or direct database operations. | Brief: Product mission | V |
| GEN-04 | A coherent, reliable MVP takes priority over a long list of unfinished features. The main journey is completed and stabilized before any optional feature. | Brief: Scenario, Product boundaries | R |
| GEN-05 | The MVP scope is: account and role management, viewing profiles, authorized media indexing, catalogue navigation and metadata, browser playback, per-profile favourites and progress, and authorized peer-to-peer acquisition. | Brief: Product boundaries | R |
| GEN-06 | The delivered version states clearly what it does and does not support. Limitations are never presented as delivered features. | Brief: Goal; T5; T6 | A |

## Accounts and roles

| ID | Requirement | Source | Phase |
|---|---|---|---|
| ACC-01 | An administrator can create an account (first-administrator setup) through the interface, without changing application code. | Brief: Setup; T1 s2 | V |
| ACC-02 | First-administrator setup does not create a vulnerable default account. | T1 | V |
| ACC-03 | Users can sign in and sign out through the interface, using a documented, justified session or token strategy. | Brief: Viewing journey; T1 s3 | V |
| ACC-04 | Passwords are stored with an appropriate hashing algorithm and never in plain text. Configuration secrets are protected. | Brief: Safe behavior; T1 s4 | V |
| ACC-05 | Account and role models and their migrations live in storage owned by the identity service. | T1 s1 | V |
| ACC-06 | Protected operations reject anonymous requests and insufficient roles. This is enforced on the server for every protected resource. | Brief: Safe behavior; T1 | V |
| ACC-07 | Other services verify identity and permissions through a documented mechanism, without reading the identity service's tables. | Brief: Technologies; T1 s7 | V |
| ACC-08 | Forms for sign-in and account management validate input and show loading, error and expired-session states. | T1 s8 | V |
| ACC-09 | Administrators can create, disable and manage accounts and roles. | Brief: Setup; T1 s5 | M |
| ACC-10 | A disabled account cannot sign in or use protected resources. | T1 s9 | M |

## Viewing profiles

| ID | Requirement | Source | Phase |
|---|---|---|---|
| PRF-01 | An account can have multiple viewing profiles, and profiles belong to an account. | T1 s6 | M |
| PRF-02 | After signing in, the user selects a profile. How the active profile is selected is defined and documented. | Brief: Viewing journey; T1 s6 | M |
| PRF-03 | Favourites and viewing progress are separate for each profile. | Brief: Viewing journey | M |
| PRF-04 | A user cannot access another user's profiles or progress by changing an identifier in a URL or request. | Brief: Safe behavior | M |

## Library and indexing

| ID | Requirement | Source | Phase |
|---|---|---|---|
| LIB-01 | An administrator configures authorized media folders and starts indexing through the interface, without changing application code. | Brief: Setup | V |
| LIB-02 | Supported media extensions and the rules for identifying a video are defined. | T2 s1 | V |
| LIB-03 | Access to a configured folder is checked during setup and during indexing. The interface explains an inaccessible or invalid directory in clear language. | Brief: Setup; T2 s2 | V |
| LIB-04 | Video files in the authorized folders are detected. | Brief: Setup | V |
| LIB-05 | Repeated scans do not create duplicates (indexing is idempotent). | Brief: Setup; T2 s5 | V |
| LIB-06 | An administrator can re-index manually. | Brief: Setup | V |
| LIB-07 | Files that were deleted or became inaccessible are identified and marked, while keeping useful catalogue information. A later scan reflects additions, deletions and unavailable files. | Brief: Setup; T2 s6 | V |
| LIB-08 | File access is restricted to the configured roots. Path traversal is prevented, and user input cannot read files outside the authorized roots. | Brief: Safe behavior; T2 s3 | V |
| LIB-09 | Media, availability and metadata models and migrations are owned by the library service. | T2 s4 | V |
| LIB-10 | Configuration and access errors are understandable to users and usefully logged. | T2 | V |
| LIB-11 | The source media library is mounted read-only, since write access is unnecessary. | Brief: Safe behavior | V |
| LIB-12 | An administrator can remove a catalogue entry without deleting its source file. | T2 s8 | M |
| LIB-13 | HomeFlix runs on a Docker-compatible NAS, or on a computer or server that can access files on a NAS or Internet box. Tested compatible environments and network-share mount prerequisites are documented. | Brief: Setup | A |

## Catalogue and metadata

| ID | Requirement | Source | Phase |
|---|---|---|---|
| CAT-01 | Catalogue entries distinguish **available**, **unavailable** and **downloading** content. | Brief: Setup; T2 | V |
| CAT-02 | The catalogue has sections for recently added titles and for categories. | Brief: Viewing journey; T2 s7 | V |
| CAT-03 | Each movie page shows title, description, poster, genre, duration and availability. | Brief: Viewing journey; T2 | V |
| CAT-04 | Users can search by title and filter by genre. | Brief: Viewing journey; T2 s7 | V |
| CAT-05 | A placeholder poster is shown when a title has none. | T2 s9 | V |
| CAT-06 | Catalogue screens show meaningful loading, empty and error states. | Brief: Playback; T2 s9 | V |
| CAT-07 | The catalogue has sections for favourites and for continue watching. | Brief: Viewing journey; T2 s7 | M |
| CAT-08 | Users can add or remove a title from "My List", per profile. | Brief: Viewing journey; T3 s7 | M |
| CAT-09 | Administrators can add or correct metadata. | Brief: Setup; T2 s8 | M |
| CAT-10 | A title with missing metadata (no description, genre, duration or poster) is still shown, with clear placeholders, and an administrator can complete it. | Team decision, from Brief: Setup and T2 s9 | V |
| CAT-11 | An administrator supplies a poster by entering an image URL. Only `https` URLs on an allowed host are accepted. The server never fetches the URL, and the interface falls back to the placeholder poster if the image does not load. | Team decision, from Brief: Safe behavior | M |

## Playback and viewing progress

| ID | Requirement | Source | Phase |
|---|---|---|---|
| PLY-01 | One supported video format is chosen and documented (for example MP4 with H.264 video and AAC audio). Universal transcoding is not required. | Brief: Playback; T3 s1 | V |
| PLY-02 | Available videos play in the browser with controls for play, pause, seeking, volume and fullscreen. | Brief: Playback; T3 s5 | V |
| PLY-03 | The server supports HTTP Range requests, with correct partial-content responses, so users can seek without downloading the whole file first. | Brief: Playback; T3 s2 | V |
| PLY-04 | Playback permission is enforced on every stream request. Unauthorized stream access is rejected, and paths stay within the authorized roots. | T3 s3 | V |
| PLY-05 | Missing files, incompatible or unsupported formats, denied access, loading, empty results, failed requests and unavailable services produce understandable feedback, not technical details. | Brief: Playback; T3 s8 | V |
| PLY-06 | Home, movie details, search and player screens are responsive. | T3 s4 | V |
| PLY-07 | Viewing progress is saved periodically for the active profile. | Brief: Playback; T3 s6 | M |
| PLY-08 | Playback resumes from the last saved position, after signing in again. | Brief: Playback; T3 s6 | M |
| PLY-09 | Users can list started and completed titles and reset progress. Started, completed and reset states are consistent. | T3 s7 | M |
| PLY-10 | Progress and favourites persist after container restarts. | T3 s9 | M |

## Acquisition

| ID | Requirement | Source | Phase |
|---|---|---|---|
| ACQ-01 | An administrator can browse a separate catalogue of content verified by the team as downloadable and redistributable. | Brief: Acquisition | M |
| ACQ-02 | Each acquisition entry identifies its source and its licence or other authorization. The catalogue contains only content with documented authorization, and is distinguished from files already present. | Brief: Acquisition; T4 s3 | M |
| ACQ-03 | An administrator can start, monitor and cancel a download. Errors are handled. | Brief: Acquisition; T4 s6 | M |
| ACQ-04 | Download states **queued**, **downloading**, **completed** and **failed** are visible. | Brief: Acquisition | M |
| ACQ-05 | Only administrators can start, view or cancel downloads. Ordinary users cannot trigger them. | T4 s6 | M |
| ACQ-06 | A completed file is placed in a dedicated directory with controlled permissions. The directory has only the write permissions it needs. | Brief: Acquisition, Safe behavior; T4 s7 | M |
| ACQ-07 | A completed file appears in the library after indexing (triggered or documented) and plays from the catalogue. | Brief: Acquisition; T4 s8 | M |
| ACQ-08 | An existing BitTorrent client is integrated through its API. Building a BitTorrent engine or a torrent search engine is not required. | Brief: Acquisition | M |
| ACQ-09 | The peer-to-peer client is isolated, and its sources are limited to approved entries. | T4 s4 | M |
| ACQ-10 | Arbitrary URLs, internal-network access and exposure of the client's administration interface are blocked. Ordinary users cannot reach that interface. | Brief: Safe behavior; T4 s5 | M |
| ACQ-11 | A rights reminder is shown before the first download. | Brief: Acquisition; T4 s2 | M |
| ACQ-12 | Only personal videos, own creations or content whose licence permits the intended use are used. Reproduction and redistribution rights are verified, and source and authorization evidence are kept for each demonstration file. | Brief: Acquisition; T4 s1, s2 | M |
| ACQ-13 | No source that distributes unauthorized works is integrated, and no DRM circumvention is used. An in-app warning does not replace checking rights. | Brief: Acquisition | M |
| ACQ-14 | A **real** end-to-end peer-to-peer transfer of a small authorized file is run and recorded. A simulation does not meet this requirement. | Brief: Acquisition, Oral presentation; T4 s9 | M |

## Security and privacy

These apply to every service and screen.

| ID | Requirement | Source | Phase |
|---|---|---|---|
| SEC-01 | All input is validated on the server. | Brief: Safe behavior | R |
| SEC-02 | Authorization is enforced on the server for every protected resource. Forbidden operations are rejected even when the interface is bypassed. | Brief: Safe behavior; T5 | R |
| SEC-03 | User-controlled text is rendered safely. | Brief: Safe behavior | R |
| SEC-04 | Errors do not expose technical details. | Brief: Safe behavior | R |
| SEC-05 | No secret is committed to the repository. | Brief: Safe behavior | R |
| SEC-06 | Only necessary personal data is collected. | Brief: Safe behavior | R |
| SEC-07 | A user or administrator can delete an account and its associated data in every service, without deleting source media. | Brief: Safe behavior; T5 s5 | M |
| SEC-08 | Validation, injection, XSS, relevant CSRF risks, secrets and exposed errors are reviewed. | T5 s3 | A |
| SEC-09 | File paths, mounts, permissions, remote URLs and internal-network access are audited. | T5 s4 | A |
| SEC-10 | Data isolation between services is verified. Critical path, secret, data and inter-service risks are fixed or explicitly documented. | T5 s2; T5 | A |
| SEC-11 | The requirements for any future Internet exposure, including HTTPS, are documented. Remote access itself is not required. | Brief: Safe behavior | A |
| SEC-12 | The gateway restricts which external hosts the pages may load images from, using the same allowed-host list that validates poster URLs. Poster images are loaded without sending a referrer. | Team decision, from Brief: Safe behavior | M |

## Interface, accessibility and identity

| ID | Requirement | Source | Phase |
|---|---|---|---|
| UX-01 | HomeFlix feels like a welcoming household streaming service, not a technical file browser. | Brief: Product expectations | R |
| UX-02 | HomeFlix has its own visual identity. Brands, logos, content and proprietary assets of existing streaming products are not copied. | Brief: Product expectations | V |
| UX-03 | The interface is responsive and readable. | Brief: Safe behavior | V |
| UX-04 | The interface is keyboard accessible, with visible labels and clear focus states. | Brief: Safe behavior; T5 s6 | V |
| UX-05 | Loading, error and empty states are meaningful and explicit on every screen. | Brief: Safe behavior | V |
| UX-06 | An administration area brings together accounts, roles, metadata, indexing, acquisition and service status. | T5 s1 | M |
| UX-07 | The main user journeys are checked with the keyboard and on mobile. Labels, contrast, focus, empty states and messages are fixed where needed. | T5 s6; T5 | A |

## Architecture

| ID | Requirement | Source | Phase |
|---|---|---|---|
| ARC-01 | All business microservices use Java and Spring Boot. There are at least three, each running in its own container. A gateway, the frontend and the third-party BitTorrent client do not count toward the three. | Brief: Technologies; T0 | S0 |
| ARC-02 | The services have distinct responsibilities. Running three copies of the same monolith does not meet the requirement. | Brief: Technologies; T0 | S0 |
| ARC-03 | Service responsibilities and APIs are documented. | Brief: Technologies | S0 |
| ARC-04 | Each service owns its data. No service reads another service's tables. Separate databases or schemas are used, possibly on one database server. | Brief: Technologies; T0 | S0 |
| ARC-05 | Entities, relationships, schemas and cross-service consistency rules are defined. | T0 s5 | S0 |
| ARC-06 | Priority API contracts specify inputs, outputs, permissions and errors. | T0 s6; T0 | S0 |
| ARC-07 | Identity and permissions are verified for protected operations. | Brief: Technologies | V |
| ARC-08 | Errors and service outages are handled, and the outage behavior is described. | Brief: Technologies; T0 s6 | V |
| ARC-09 | The frontend technology, data stores, migrations, test tools and peer-to-peer client are chosen and justified. | Brief: Technologies; T0 s7 | S0 |
| ARC-10 | Inter-service communication can be demonstrated with a request between two services. | T0 s11; T0 | S0 |
| ARC-11 | Database migrations exist for each service's storage. | T1 s1; T2 s4 | V |

## Docker, installation and operations

| ID | Requirement | Source | Phase |
|---|---|---|---|
| OPS-01 | The frontend, business services and required dependencies run with Docker Compose. The documented startup command is `docker compose up --build`. | Brief: Docker; T0 | S0 |
| OPS-02 | Each developed component has a Dockerfile, and the repository has a `compose.yaml`. | Brief: Docker; T0 s10 | S0 |
| OPS-03 | A secret-free `.env.example` is present and contains no usable secret. | Brief: Docker; T0 | S0 |
| OPS-04 | Persistent volumes are used, with separate mounts for the source library and the downloads. | Brief: Docker | S0 |
| OPS-05 | Health checks exist and the service health is verified. | Brief: Docker; T0 s11 | S0 |
| OPS-06 | Troubleshooting instructions are provided. | Brief: Docker | A |
| OPS-07 | Another team can clone the repository, configure the environment, start HomeFlix with Docker Compose, reach a demonstration state and play a video by following the README. Nothing depends on undocumented local steps. | Brief: Reproducible delivery; T6 s1 | A |
| OPS-08 | Accounts, catalogue and progress are preserved when the containers restart. | Brief: Acceptance; T5; T6 | A |
| OPS-09 | A backup and restore guide covers the persistent data. | Brief: Security, data and quality; T6 s3 | A |
| OPS-10 | Variables, volumes, library mounts, downloads folder and migrations are documented, along with a safe first-administrator setup. | T6 s3, s4 | A |
| OPS-11 | Documentation states the tested minimum hardware resources, supported systems and environments, supported video formats, and known limits. | Brief: Security, data and quality; T6 s2 | A |
| OPS-12 | Demonstration accounts and a reset procedure are documented as a safe procedure: the administrator is created through the first-run setup screen, then demo household accounts are created through the interface. No account or password is shipped in the code or the README. | Brief: Reproducible delivery; T6 s6 | A |

## Documentation and delivery

| ID | Requirement | Source | Phase |
|---|---|---|---|
| DOC-01 | User journeys, mandatory requirements, acceptance criteria and out-of-scope ideas are written. | T0 s2 | S0 |
| DOC-02 | A risk register covers file access, identity, copyright, downloads, outages and backups, with mitigations. | T0 s12; T0 | S0 |
| DOC-03 | A user README covers prerequisites, installation, configuration, access and first playback. | Brief: Security, data and quality | A |
| DOC-04 | Technical documentation covers architecture, service ownership, data, APIs and technical choices. | Brief: Security, data and quality; T6 s5 | R |
| DOC-05 | A backlog shows completed work and remaining limitations. | Brief: Security, data and quality | R |
| DOC-06 | Authorized demonstration files are provided with their sources and licences. | Brief: Security, data and quality | M |
| DOC-07 | A copyright and peer-to-peer awareness note is provided. | Brief: Security, data and quality | M |
| DOC-08 | Presentation slides are provided. | Brief: Security, data and quality | A |
| DOC-09 | Environment variables, tests, the acceptance plan, demonstration accounts, supported formats and environments, and known limitations are documented. | Brief: Reproducible delivery | A |
| DOC-10 | The evaluated commit or tag is identified, together with known limits and possible future work. | T6 s11 | A |

## Testing and acceptance

| ID | Requirement | Source | Phase |
|---|---|---|---|
| TST-01 | Important business rules and critical inter-service communication are tested. | Brief: Reproducible delivery | R |
| TST-02 | Authentication tests cover sign-in, disabled accounts, anonymous access, administrator permissions and profile isolation. | T1 s9 | V |
| TST-03 | Indexing tests cover duplicate scans, forbidden paths, denied access, missing files and storage errors. | T2 s10 | V |
| TST-04 | Playback tests cover Range responses, profile isolation, resume and persistence after restart. | T3 s9 | M |
| TST-05 | Business-rule and inter-service integration tests are completed. | T5 s8 | A |
| TST-06 | An acceptance plan covering every MVP acceptance scenario is written and run, and the results are recorded. | T5 s7; T5 | A |
| TST-07 | Known defects, residual risks and required corrections are recorded. | T5 s11 | A |
| TST-08 | Critical business and integration tests can be rerun from the documentation. Test commands and acceptance results are available for the evaluated version. | T5; T6 | A |

## AI agent

| ID | Requirement | Source | Phase |
|---|---|---|---|
| AGT-01 | At least one clearly bounded project role is delegated to an AI agent and included in the working agreement. Occasional questions to a general chatbot do not satisfy this. | Brief: Mission, Agentic contribution; T0 s8 | S0 |
| AGT-02 | The role has a clear objective, named inputs, permitted tools, explicit boundaries, expected deliverables and a human review step. | Brief: Agentic contribution | S0 |
| AGT-03 | The supervising learner is named, and the attribution of the agent's contribution is documented. | Brief: Agentic contribution | S0 |
| AGT-04 | The agent performs its bounded review and produces evidence-backed findings. | T5 s9 | A |
| AGT-05 | A learner verifies each finding and approves any proposed change before it is merged. The assignment, report, review evidence and accepted or rejected findings are traceable. | T5 s10; T5 | A |
| AGT-06 | The team explains the agent's role, shows its output and describes how a learner reviewed it. | Brief: Oral presentation; T6 s10 | A |

## Process and collaboration

| ID | Requirement | Source | Phase |
|---|---|---|---|
| PRC-01 | The team has exactly two or three learners. Their availability, responsibilities and collaboration rules are recorded. | Brief: Team; T0 s1 | S0 |
| PRC-02 | Every learner contributes to development, understands the global architecture, and can explain their own work. | Brief: Team and evaluation | R |
| PRC-03 | Git, a shared repository, tickets, branches, code reviews, continuous integration and commit conventions are set up and used. | Brief: Security, data and quality; T0 s9 | S0 |
| PRC-04 | A short daily team check-in shares progress, blockers and priorities. | Brief: Schedule | R |
| PRC-05 | The MVP scope and exclusions are prioritized in a backlog. The first priority is the vertical journey: sign-in, indexing a test folder, catalogue and HTTP Range playback. | T0 s3; T0 | S0 |
| PRC-06 | The team follows the schedule: Sprint 0, vertical slice, integrated review on day 5, completion, acceptance run on day 9, presentation on day 10. | Brief: Schedule | R |

## Oral presentation

| ID | Requirement | Source | Phase |
|---|---|---|---|
| PRS-01 | The presentation lasts 30 minutes: 5 on need, scope and team organization; 10 on the demo; 5 on architecture, Docker, security and technical choices; 10 on questions and lessons learned. | Brief: Oral presentation; T6 s8 | A |
| PRS-02 | Every learner speaks and answers questions about their own contribution, and explains a full technical journey. | Brief: Oral presentation; T6 s9 | A |
| PRS-03 | The demo shows local playback, resume playback and a real authorized peer-to-peer download of a small file. | Brief: Oral presentation | A |
| PRS-04 | The team connects each feature to its interface, API, validation rule and data operation, and demonstrates successful and rejected requests. | Brief: Reproducible delivery | A |
| PRS-05 | The team can explain its architecture, business rules and security choices. | Brief: Product mission | A |
| PRS-06 | The authorized peer-to-peer file, its rights evidence and a repeatable demo procedure are prepared. | T6 s7 | A |

## Application demo video

| ID | Requirement | Source | Phase |
|---|---|---|---|
| VID-01 | A video of 2 to 6 minutes presents the application, and its accessible YouTube link is delivered. | T7 | A |
| VID-02 | Part 1 explains the problem HomeFlix solves and who it is for, shows the main features in a real demonstration, and shows its value. | T7 | A |
| VID-03 | Part 2 presents the tech stack with the reasons for each choice, the microservice architecture and how services communicate, the database and data organization, how video streaming and peer-to-peer downloading work, and the design process and the role of the AI agent. | T7 | A |
| VID-04 | The explanations accurately reflect the work completed, the audio is clear, and the visual content is readable. | T7 | A |

---

## MVP acceptance scenarios

The brief lists ten scenarios the finished product must pass. Detailed criteria are written in the acceptance criteria document (issue #7). The requirements each scenario exercises are listed here.

| ID | Scenario | Passes when | Main requirements |
|---|---|---|---|
| AS-01 | Install from the repository | An external user succeeds by following the README. | OPS-01, OPS-07 |
| AS-02 | Configure a media library | Videos in an authorized folder appear without duplicates. | LIB-01 to LIB-07 |
| AS-03 | Search and play a movie | Search finds it, and the player can seek within it. | CAT-04, PLY-02, PLY-03 |
| AS-04 | Pause and resume | The profile restores its saved position after sign-in. | PLY-07, PLY-08 |
| AS-05 | Switch profiles | Favourites and progress remain isolated. | PRF-03, PRF-04, CAT-08 |
| AS-06 | Acquire missing content | An authorized peer-to-peer download completes and becomes playable. | ACQ-01 to ACQ-14 |
| AS-07 | Attempt an unauthorized action | The server rejects the operation. | ACC-06, SEC-02, PLY-04, ACQ-05 |
| AS-08 | Restart the containers | Accounts, catalogue and progress are preserved. | OPS-08, PLY-10 |
| AS-09 | Make a file or service unavailable | The application shows an understandable error. | PLY-05, ARC-08, LIB-07 |
| AS-10 | Review the AI agent | The team shows its assignment, report and human review. | AGT-01 to AGT-06 |

## Out of scope

These are not required. Each may only be proposed once the MVP is complete and stable. An extension must answer a documented user need, preserve the existing journeys, and include its own tests and documentation. The detailed list with reasons is kept in the out-of-scope document (issue #8).

- Universal transcoding, and support for more video formats.
- Mobile or TV applications.
- Recommendations based on viewing history.
- Series, seasons and episodes.
- Parental controls, household invitations and additional profile controls.
- Remote Internet access (only its requirements are documented, see SEC-11).
- Universal support for network protocols, and support for every NAS or router.
- Building a BitTorrent engine, and a general-purpose torrent search engine.
- Mockups. The brief's Day 1 schedule lists them, but the team decided to skip them: the frontend lead (Thomas) designs the interface directly in code.

## Open questions and gaps

Points where the brief is silent, ambiguous or in tension. They should be settled by the team and recorded, in an ADR where they are architecture decisions.

1. **Number of working days. Resolved.** The team works from 16/11/2026 to 26/11/2026, and the presentation is on the afternoon of 27/11/2026, which is day 10 of the schedule. The team may also work some of the weekend of 21 and 22 November to make up time.
2. **Demonstration accounts versus no default account. Resolved.** The brief asks for demonstration accounts (OPS-12), but first-administrator setup must not create a vulnerable default account (ACC-02). Decision: no account exists on first start. The administrator is created through the first-run setup screen with a password they choose, and demo household accounts are created through the interface. The documentation describes this procedure and ships no account or password.
3. **Roles. Resolved.** The brief mentions roles and administrators versus ordinary users, but defines no list of roles. Decision: there are two roles, **administrator** and **user**. They are stored in a single account table in the identity service, with an `is_admin` column. Administrators manage accounts, the catalogue, indexing and acquisition. Users browse, play and keep their own profiles, favourites and progress. The detailed permissions are written in the identity and permissions design (issue #14).
4. **Where metadata comes from. Resolved.** The brief does not say how a new title gets its title, description, genre, duration and poster. A downloaded title only gets what the torrent file contains, which is essentially its name and size. The team accepts that metadata can be incomplete (CAT-10). For the demo, either a downloaded file is shown with incomplete data, or some files are curated beforehand, with metadata entered by an administrator (CAT-09). Posters are supplied as an external image URL on an allowed host (CAT-11, SEC-12, ADR-0018). Uploading an image file is a possible later improvement.
5. **The "downloading" state. Resolved.** A catalogue entry can be "downloading" (CAT-01), yet the file does not exist in the library yet. How does the acquisition service tell the library service, and when does the entry become available? Decision: acquisition tells the library when a download starts, completes or fails, through internal calls, and the entry becomes available when the library confirms the completed file. See the [cross-service consistency rules](../architecture/consistency-rules.md#acquisition-handoff).
6. **Deleting an account across services. Resolved.** SEC-07 requires removing associated data in every service. The identity service must tell the library service to remove that profile's favourites and progress. Decision: identity asks the library to delete each profile's data first, then deletes the account, so a failure never leaves orphan rows. See the [cross-service consistency rules](../architecture/consistency-rules.md#deleting-an-account).
7. **"Tested" environments.** OPS-11 requires documented minimum resources and compatible environments. These must be measured on real machines before the delivery, and the machines chosen.
8. **Continue watching. Resolved.** The brief puts the "continue watching" section in the library step (T2) but it depends on viewing progress (T3). It is scheduled for phase M, with progress.
9. **Daily check-in. Resolved.** The brief requires a short daily check-in (PRC-04), while the working agreement says the team has no fixed time. Decision: a written daily message on Discord is enough. Each learner says what was done, what blocks and what is next. A call is held only when needed.
