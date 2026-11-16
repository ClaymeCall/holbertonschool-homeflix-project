# HomeFlix: Original Project Brief and Tasks

Raw copy of the project requirements, in their original Markdown. Do not edit; summaries and decisions belong in `docs/`.

# Project brief

## Description

HomeFlix is a self-hosted video streaming platform inspired by familiar video-on-demand experiences.
It lets an individual or household browse and watch videos stored on a computer, personal server,
NAS, or storage attached to a home router and accessible to the host.

This is a fun, two-week educational project for a team of two or three learners. It practices Java
development, microservice architecture, project management, testing, and delivery of a complete
application. The final day is reserved for an oral presentation.

The challenge is to let someone outside the team install HomeFlix, connect a media library, and play
a video by following the project documentation alone.

## Scenario: Movie night at home

A household has a collection of videos on a NAS but no convenient way to browse and watch them on
the living-room laptop. The team is asked to build a small, self-hosted streaming hub that makes
movie night easier: an administrator connects the library, household members choose their profiles,
and everyone can find, play, and resume a film.

The product should be enjoyable to build and pleasant to use while keeping media access and
downloads safe. A coherent and reliable MVP is more important than a long list of unfinished
features.

## Product mission

Your team must deliver an installable MVP that connects to an authorized media folder and supports
the journey from first-time setup to video playback and resume. The main journey must be available
through the interface, not only through isolated API calls or direct database operations.

The application must include at least three genuine Java/Spring Boot business microservices, a
Docker Compose environment, and a real peer-to-peer download of an authorized demonstration file.
The team must be able to explain its architecture, business rules, security choices, and each
learner's contribution.

The team must also delegate at least one clearly bounded project role to an AI agent. Define what
the agent is responsible for, what information and tools it may use, what it must deliver, and how a
learner reviews its work. Every learner remains responsible for understanding the delivered product.

## Product expectations

HomeFlix should feel like a welcoming household streaming service, not a technical file browser.
Product decisions, interface behavior, and implementation must support these expectations.

HomeFlix must have its own visual identity. Existing streaming products may inspire familiar
interaction patterns, but their brands, logos, content, or proprietary visual assets must not be
copied.

### A clear and enjoyable viewing journey

A household member should be able to sign in, select a viewing profile, browse or search for an
available movie, open its details, start playback, pause, and later resume from the saved position.
Favourites and viewing progress must remain separate for each profile.

The catalogue should include useful sections such as recently added titles, categories, favourites,
and continue watching. Each movie page should show its title, description, poster, genre, duration,
and availability. Users should be able to search by title, filter by genre, and add or remove a
title from “My List.”

### A simple setup and reliable media library

An administrator should be able to create an account, configure authorized media folders, and start
indexing without changing application code. The interface must explain inaccessible or invalid
directories in clear language.

The application should detect video files, avoid duplicates across repeated scans, support manual
re-indexing, and identify files that have been deleted or become inaccessible. Catalogue entries
must distinguish available, unavailable, and downloading content. Administrators should be able to
add or correct metadata and manage accounts and roles.

HomeFlix may run on a Docker-compatible NAS or on a computer or server that can access files on a
NAS or Internet box. Support for every NAS or router is not expected; tested compatible environments
and any network-share mount prerequisites must be documented.

### Predictable playback and feedback

Available videos should play in the browser with controls for play, pause, seeking, volume, and
fullscreen. The server must support HTTP Range requests so users can seek without downloading the
entire file first. Viewing progress must be saved per profile and restored at the last saved
position.

The MVP must document one supported format, for example MP4 with H.264 video and AAC audio.
Universal transcoding is not required. Missing or incompatible files, loading, empty results, failed
requests, and unavailable services must produce understandable feedback rather than technical
details.

### Safe and authorized acquisition

An administrator should be able to browse a separate catalogue of content verified by the team as
downloadable and redistributable. Each entry must identify its source and licence or other
applicable authorization. The administrator can start, monitor, and cancel a download. Visible
states are queued, downloading, completed, and failed. A completed file is placed in a dedicated
directory and appears in the library after indexing.

A real peer-to-peer download of a small authorized test file is required during the presentation; a
simulation does not meet this requirement. Teams may integrate an existing BitTorrent client through
its API. Building a BitTorrent engine or a general-purpose torrent search engine is not required.

Peer-to-peer is a transfer method; it does not grant permission to download or share a work. Teams
must use personal videos, their own creations, or content whose licence permits the intended use.
Verify both reproduction and redistribution rights, retain source and authorization evidence for
demonstration files, and show a rights reminder before the first download.

Do not integrate sources that distribute unauthorized works or any DRM circumvention. A file being
available online or on a NAS does not prove that downloading or sharing it is permitted. An in-app
warning does not replace checking rights for the content actually used.

### Safe, accessible, and respectful behavior

Protect accounts with secure authentication, safe password storage, server-side validation, and
authorization on every protected resource. Users must not access another user's profiles or progress
by changing an identifier in a URL or request. User-controlled text must be rendered safely, errors
must not expose technical details, and secrets must not be committed to the repository.

Restrict file access to configured roots and prevent path traversal. Validate remote sources;
arbitrary URLs must not provide access to internal services. Ordinary users must not be able to
access the BitTorrent client's administration interface. Mount the source media library read-only
when write access is unnecessary and give the download directory only the write permissions it
needs.

Collect only necessary personal data and provide a way to delete accounts and associated data.
Document the requirements for any future Internet exposure, including HTTPS; remote access is not
required for the MVP.

The interface must be responsive, keyboard accessible, readable, and explicit. Use visible labels,
clear focus states, and meaningful loading, error, and empty states.

### A bounded agentic contribution

Each team must delegate at least one bounded project role to an AI agent and include it in the
team's working agreement. The role must have a clear objective, named inputs, permitted tools,
explicit boundaries, expected deliverables, and a human review step. Merely asking a general chatbot
occasional questions does not satisfy this requirement.

A suggested role is **API and Documentation Consistency Reviewer**:

- **Objective:** find mismatches between documented API contracts, OpenAPI definitions, and
  implemented behavior.
- **Inputs:** repository documentation, OpenAPI files, relevant source files, and the team's
  acceptance criteria.
- **Permitted tools:** read and search files and run approved, non-destructive checks; the team may
  explicitly allow proposed edits in an isolated branch.
- **Boundaries:** do not access secrets, change authentication or authorization behavior, alter
  media files, initiate downloads, merge changes, or publish or deploy.
- **Deliverable:** a concise findings report with file and line references, severity, reproduction
  evidence, and suggested corrections.
- **Human review:** a learner verifies each finding against the implementation and approves any
  change before it is merged.

Teams may choose another role if it is similarly specific, useful to the project, and safely
bounded. Document which learner supervises the agent and how its contribution is attributed. The
two or three learners remain responsible for the project and must understand the final result.

### Reproducible and explainable delivery

Another team must be able to clone the repository, configure the environment, start HomeFlix with
Docker Compose, reach a demonstration state, and play a video by following the README. The
installation process must not depend on undocumented local steps.

Document the service boundaries and APIs, data ownership, technical choices, environment variables,
tests, acceptance plan, demonstration accounts, supported formats and environments, and known
limitations. Important business rules and critical inter-service communication must be tested.
During the presentation, learners should connect each feature to its interface, API, validation
rule, and data operation, and demonstrate successful and rejected requests.

## Product boundaries

The MVP covers account and role management, viewing profiles, authorized media indexing, catalogue
navigation and metadata, browser playback, per-profile favourites and progress, and authorized
peer-to-peer acquisition. Universal transcoding, mobile or TV apps, recommendations, series,
parental controls, remote Internet access, and universal support for network protocols are possible
extensions.

The team should complete and stabilize the main journey from setup through playback and resume
before attempting optional features. Support for every NAS or router, a BitTorrent engine, and a
general-purpose torrent search engine are outside the required scope.

## Technologies

All business microservices developed by the team must use Java and Spring Boot. The frontend
technology is open and must be justified. The application must include at least three business
microservices, each running in a separate container. A gateway, frontend, or third-party BitTorrent
client does not count toward the three-service minimum.

Suggested service boundaries:

- **Identity and profiles:** accounts, authentication, roles, and viewing profiles.
- **Library and playback:** indexing, catalogue, video access, and viewing progress.
- **Acquisition:** authorized downloads, status tracking, and handoff of completed files.

Another decomposition is acceptable when justified. Service responsibilities and APIs must be
documented. Each service owns its data; no service may access another service's tables directly. Use
separate databases or schemas, although one database server may host them. Handle errors and service
outages, and verify identity and permissions for protected operations. Running three copies of the
same monolith does not meet the microservices requirement.

## Delivery constraints

### Docker and configuration

The frontend, business services, and required dependencies run with Docker Compose. The repository
includes Dockerfiles for developed components, `compose.yaml`, a secret-free `.env.example`,
persistent volumes, separate mounts for the source library and downloads, health checks, and
troubleshooting instructions. The documented startup command is `docker compose up --build`.

### Security, data, and quality

Use Git, a shared repository, tickets, branches, and code reviews. Provide a user README covering
prerequisites, installation, configuration, access, and first playback; technical documentation for
architecture, service ownership, data, APIs, and technical choices; a backlog showing completed work
and remaining limitations; a test plan and acceptance report; authorized demonstration files with
their sources and licences; a backup and restore guide for persistent data; a copyright and
peer-to-peer awareness note; and presentation slides.

Documentation must state the tested minimum hardware resources, supported video formats, and
compatible environments. Include a team working agreement that defines the AI agent's role, tools,
boundaries, outputs, supervisor, and review process.

### Two-week schedule

- **Day 1: Sprint 0:** scope, backlog, mockups, architecture, data model, and API contracts.
- **Days 2–4:** sign-in, indexing, catalogue, and the first playback journey in Docker.
- **Day 5:** integrated review, blockers, and adjusted priorities.
- **Days 6–8:** profiles, resume, favourites, authorized acquisition, and administration.
- **Day 9:** acceptance run, fixes, installation on another machine, and presentation prep.
- **Day 10:** oral presentation, demo, questions, and handoff.

Hold a short daily team check-in to share progress, blockers, and priorities. Responsibilities may
change, but all two or three learners must contribute to development and explain the overall architecture
and their own work.

### MVP acceptance scenarios

- **Install from the repository:** an external user succeeds by following the README.
- **Configure a media library:** videos in an authorized folder appear without duplicates.
- **Search and play a movie:** search finds it and the player can seek within it.
- **Pause and resume:** the profile restores its saved position after sign-in.
- **Switch profiles:** favourites and progress remain isolated.
- **Acquire missing content:** an authorized peer-to-peer download completes and becomes playable.
- **Attempt an unauthorized action:** the server rejects the operation.
- **Restart the containers:** accounts, catalogue, and progress are preserved.
- **Make a file or service unavailable:** the application shows an understandable error.
- **Review the AI agent:** the team shows its assignment, report, and human review.

### Oral presentation

The presentation lasts 30 minutes: 5 minutes for the need, scope, and team organization; 10 minutes
for the demo; 5 minutes for architecture, Docker, security, and technical choices; and 10 minutes
for questions and lessons learned. Every learner must speak and answer questions about their
contribution.

The demo must show local playback, resume playback, and a real authorized peer-to-peer download
using a small file. The team must also explain the AI agent's role, show its output, and describe
how a learner reviewed it.

### Proposed assessment weights

| Criterion                                                | Weight |
| -------------------------------------------------------- | ------ |
| MVP functionality and user experience                    | 30%    |
| Microservice architecture and Java/Spring Boot quality   | 25%    |
| Dockerization and ease of installation                   | 15%    |
| Security, rights management, and testing                 | 15%    |
| Collaboration, documentation, agentic workflow, and demo | 15%    |

Failure to use Java/Spring Boot, deliver three genuine business microservices, or provide Docker
Compose is a major non-compliance with the project brief.

## Team and evaluation

This is a group project for exactly two or three learners over two weeks. Responsibilities may be shared or
rotated, but every learner must contribute to development, understand the global architecture, and
explain their own work. The mentor evaluates the working product, engineering quality,
collaboration, documentation, agentic workflow, and oral presentation using the accompanying
assessment rubric.

## Goal

Deliver a platform that another team can install, configure, and use without your help. Prioritize a
coherent, reliable MVP over optional features, and state clearly what the delivered version does and
does not support.

## Resources

- Java documentation
- Spring Boot documentation
- Docker Compose documentation
- [HTTP Range requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Range_requests)
- OWASP Top 10

## Possible Extensions

Once the MVP is complete and stable, the team may propose additional features such as:

- support for more video formats or optional transcoding;
- household invitations and additional profile controls;
- series, seasons, and episodes;
- recommendations based on viewing history;
- native mobile or TV applications;
- remote access with documented security requirements.

Every extension must respond to a documented user need, preserve the existing journeys, and include
its own tests and documentation.

---

# Tasks

## 0. Scope and Architecture

### Context

HomeFlix combines several connected domains: identity, profiles, indexing, catalogue, playback,
acquisition, and administration. A two or three-person team has two weeks. Without a shared scope and
contracts, services may become a distributed monolith, interfaces may diverge, and the demo may
never come together.

### Your mission

Form the team, turn the project brief into a prioritized backlog, and design a genuine architecture
with at least three Java/Spring Boot business microservices. Prepare a runnable foundation everyone
can use and prove that the components can communicate.

### Steps

1. Record the two or three learners, availability, responsibilities, and collaboration agreement.
2. Turn the brief into user journeys, mandatory requirements, acceptance criteria, and out-of-scope
   ideas.
3. Prioritize a vertical journey: sign-in, indexing a test folder, catalogue, and HTTP Range
   playback.
4. Define service boundaries, data ownership, and documented interfaces.
5. Define entities, relationships, schemas, and cross-service consistency rules.
6. Describe API contracts, identity, permissions, errors, and service-outage behavior.
7. Choose and justify the frontend, data stores, migrations, test tools, and peer-to-peer client.
8. Assign at least one bounded project role to an AI agent. Document its objective, inputs, tools,
   limits, deliverables, supervisor, and human review.
9. Set up tickets, branches, reviews, continuous integration, and commit conventions.
10. Initialize Spring Boot services, the frontend, Dockerfiles, `compose.yaml`, and `.env.example`.
11. Start the containers, verify service health checks, and demonstrate a request between two
    services.
12. Create a risk register covering file access, identity, copyright, downloads, outages, and
    backups.

### Success criteria

The task is complete when:

- The team has two or three learners and its responsibilities and collaboration rules are recorded.
- The MVP scope and exclusions are prioritized in a backlog.
- At least three business microservices have distinct responsibilities and data ownership.
- Priority API contracts specify inputs, outputs, permissions, and errors.
- The frontend, services, and dependencies start with Docker Compose.
- `.env.example` is present and contains no usable secrets.
- Inter-service communication can be demonstrated.
- The AI agent has a bounded assignment and a human reviewer.
- Major risks and mitigation measures are documented.

## 1. Identity, Roles, and Viewing Profiles

### Context

Favourites, viewing progress, administrative operations, and downloads depend on the signed-in user
and active viewing profile. Authorization enforced only in the interface would let users call
protected APIs directly.

### Your mission

Build HomeFlix's identity foundation: sign-in, sign-out, accounts, roles, and viewing profiles. Give
other services a documented way to verify identity and permissions without allowing them to query
the identity service's tables.

### Steps

1. Create account and role models and migrations in storage owned by the identity service.
2. Implement secure first-administrator setup.
3. Implement sign-in and sign-out with a justified session or token strategy.
4. Hash passwords with an appropriate algorithm and protect configuration secrets.
5. Let administrators create, disable, and manage accounts.
6. Support multiple profiles per account and define how the active profile is selected.
7. Define inter-service identity validation and server-side role checks.
8. Build frontend forms with validation and loading, error, and expired-session states.
9. Test authentication, disabled accounts, anonymous access, administrator permissions, and profile
   isolation.

### Success criteria

- Users can sign in and out through the interface.
- First-administrator setup does not create a vulnerable default account.
- Passwords are never stored in plain text.
- Protected operations reject anonymous requests and insufficient roles.
- Profiles belong to an account, and their favourites/progress can be isolated.
- Other services verify identity without reading identity-service tables.
- Errors and expired sessions have understandable feedback.
- Critical authentication and authorization rules are covered by tests.

## 2. Indexing, Catalogue, and Metadata

### Context

HomeFlix must turn files from an authorized directory into a reliable catalogue. Paths can become
unavailable, and repeated scans must not create duplicates or show a missing file as available.

### Your mission

Implement manual indexing and a browsable catalogue. The library service owns its metadata and
permits file access only within explicitly configured directories.

### Steps

1. Define supported media extensions and identification rules.
2. Configure authorized paths and check access during setup and indexing.
3. Prevent path traversal and reads outside authorized roots.
4. Create media, availability, and metadata models and migrations.
5. Implement idempotent manual indexing that avoids duplicates across scans.
6. Mark deleted or inaccessible files while preserving useful catalogue information.
7. Implement title search, genre filters, new arrivals, and continue-watching sections.
8. Let administrators edit metadata and remove a catalogue entry without deleting its source file.
9. Provide empty, loading, and error states plus a placeholder poster.
10. Test duplicate scans, forbidden paths, denied access, missing files, and storage errors.

### Success criteria

- Scanning a mounted test folder adds its videos to the catalogue without duplicates.
- A later scan reflects additions, deletions, and unavailable files.
- Available, unavailable, and downloading content are distinguished.
- User input cannot read files outside authorized roots.
- The catalogue shows the required fields and supports search and filtering.
- An entry can be edited or removed without deleting its source file.
- Configuration and access errors are understandable and usefully logged.

## 3. Video Playback, Profiles, and Viewing Progress

### Context

A catalogue is useful only when videos play in a browser and playback resumes after an interruption.
Progress belongs to the active profile, and the server must support partial file requests so viewers
can seek within a video.

### Your mission

Deliver a complete browse, play, pause, seek, and resume journey for a documented video format. Add
favourites and viewing progress without mixing data between profiles.

### Steps

1. Choose and document a demonstration format, for example MP4 with H.264/AAC.
2. Stream authorized files with correct HTTP Range and partial-content responses.
3. Enforce playback permissions on every request and keep paths within authorized roots.
4. Build responsive home, movie details, search, and player screens.
5. Implement play, pause, seeking, volume, and fullscreen.
6. Periodically save progress for the active profile and resume from its last position.
7. Add/remove favourites, list started/completed titles, and reset progress.
8. Explain missing-file, denied-access, and unsupported-format errors clearly.
9. Test Range responses, profile isolation, resume, and persistence after restart.

### Success criteria

- A video in the documented format plays through the interface.
- The player can seek without first downloading the entire media file.
- Progress is saved and restored for the correct profile after signing in again.
- Favourites and progress remain separate for two profiles.
- Started, completed, and reset states are consistent.
- Missing or unsupported files produce useful feedback.
- The server rejects unauthorized stream access.

## 4. Authorized Peer-to-Peer Acquisition

### Context

Acquisition is a special demo requirement: it must perform a real transfer, but only for content
whose download and redistribution rights are established. A peer-to-peer client may automatically
share downloaded pieces and must be isolated from ordinary users.

### Your mission

Integrate an existing peer-to-peer client and deliver the complete lifecycle of a small authorized
download, from a verified catalogue entry to an indexed file available in HomeFlix.

### Steps

1. Select a small test file and retain its source and licence or authorization evidence.
2. Document reproduction and redistribution rights and show a reminder before the first download.
3. Define the authorized catalogue metadata and distinguish it from files already present.
4. Isolate the peer-to-peer client and limit sources to approved entries.
5. Block arbitrary URLs, internal-network access, and exposure of the client's admin interface.
6. Implement administrator-only start, status, cancellation, and error handling.
7. Save completed files in a dedicated directory with controlled permissions.
8. Trigger or document indexing and confirm that the file plays from the catalogue.
9. Run and record a real end-to-end transfer using the authorized test file.

### Success criteria

- The peer-to-peer catalogue contains only content with documented authorization.
- The interface reminds users of rights obligations before the first download.
- An administrator can start, monitor, and cancel a real transfer.
- Queued, downloading, completed, and failed states are represented.
- A completed file enters the download folder and becomes available after indexing.
- Ordinary users cannot trigger downloads or access the client interface.
- The demo uses a real transfer of a small authorized file; simulation does not count.

## 5. Administration, Agent Review, Security, and Acceptance

### Context

The main features must handle ordinary failures and unauthorized access. Security also covers local
files, secrets, inter-service APIs, and data persistence after restarts. The team's AI agent must
contribute a specific, reviewable deliverable within agreed boundaries.

### Your mission

Complete administration and run an end-to-end acceptance review. Fix high-impact risks and gather
evidence for the required journeys, including the work delegated to the AI agent.

### Steps

1. Bring administration together: accounts, roles, metadata, indexing, acquisition, and service
   status.
2. Verify server-side authorization for every operation and data isolation between services.
3. Review validation, injection, XSS, relevant CSRF risks, secrets, and exposed errors.
4. Audit file paths and mounts, permissions, remote URLs, and internal network access.
5. Verify account/data deletion without accidentally deleting source media.
6. Check keyboard and mobile use; fix labels, contrast, focus, empty states, and messages.
7. Write and run an acceptance plan covering every scenario in the project brief.
8. Complete business-rule and inter-service integration tests.
9. Have the assigned AI agent perform its bounded review and produce evidence-backed findings.
10. Ask a learner to verify each finding and approve any proposed change before merge.
11. Record results, known defects, residual risks, and required corrections.

### Success criteria

- Acceptance scenarios are run and their results are recorded.
- Services reject forbidden operations even when the interface is bypassed.
- Critical path, secret, data, and inter-service risks are fixed or explicitly documented.
- Persistent data survives container restarts.
- Main user journeys work with keyboard and on mobile.
- Critical business and integration tests can be rerun from the documentation.
- The AI agent's assignment, report, review evidence, and accepted/rejected findings are traceable.
- Remaining limitations are described honestly and are not presented as completed features.

## 6. Reproducible Delivery and Oral Presentation

### Context

HomeFlix is intended for installation by someone outside the team. The delivery must document
hardware prerequisites, supported formats, mounts, commands, authorized demo data, and MVP
limitations.

### Your mission

Prepare a reproducible repository and presentation. Prove that an external user can launch the
system, configure a media library, play and resume a video, and complete an authorized peer-to-peer
transfer.

### Steps

1. Test a clean install with `docker compose up --build`, following only the README.
2. Document the minimum tested resources, supported systems, and known limits.
3. Document variables, volumes, library mounts, downloads folder, migrations, backup, and restore.
4. Provide a secret-free `.env.example` and a safe first-administrator setup.
5. Publish architecture, service responsibilities, API contracts, data models, and technical
   choices.
6. Provide test commands, acceptance results, safe demo accounts, and reset procedures.
7. Prepare the authorized peer-to-peer file, its rights evidence, and a repeatable demo procedure.
8. Prepare a 30-minute presentation: need/scope/team (5 min), demo (10 min), architecture/security
   (5 min), questions/retrospective (10 min).
9. Give every learner time to explain their contribution and a full technical journey.
10. Explain the AI agent's assignment, tools, boundaries, output, and how a learner reviewed it.
11. Identify the evaluated commit or tag, known limits, and possible future work.

### Success criteria

- An external user can install and configure the MVP from the README.
- Documentation states supported video formats, tested platforms/resources, mounts, backup, and
  restore.
- Accounts, catalogue, and progress persist after restart.
- Test and acceptance results are available for the evaluated version.
- The demo proves local playback, resume, and a real authorized peer-to-peer transfer.
- The team demonstrates and explains its bounded AI-agent workflow and human review.
- All two or three learners present their contributions and understand the overall architecture.
- Product limitations are distinct from delivered functionality.

## 7. Application Demo Video

### Your mission

Create a **2 to 6-minute video** presenting your HomeFlix application.

Your video must include two parts:

#### 1. Application Presentation and Demo

- Explain the problem HomeFlix solves and who it is designed for.
- Show your application's main features in action through a real demonstration.
- Highlight its value and make people want to use it.

#### 2. Technical Presentation

- Present your tech stack and explain your technology choices.
- Explain your microservice architecture and how the services communicate.
- Present your database and how your data is organized.
- Explain how video streaming and peer-to-peer downloading work.
- Present your design process and the role of your AI agent.

Do not simply list technologies. Explain why you chose them and how you built your application.

### Resources

Read the complete video guidelines: GitHub - Portfolio Video Guidelines.

For inspiration, watch this video created by Maxime Bernier C29 Lille for his meloDrop project
("meloDrop – Presentation Video"): https://www.youtube.com/embed/D7tE25X23Eg

The format is up to you: screen recording, voice-over, editing, animation, talking to the camera… Be creative!

### Deliverable

An accessible Youtube link to your application demo video.

### Success criteria

- The video is between 2 and 6 minutes long.
- Both parts are clearly identifiable.
- The application is demonstrated in action.
- Technical choices are explained and justified.
- Explanations accurately reflect the work completed.
- Audio is clear and visual content is readable.
- The video Youtube link is accessible.

**Make people want to use your application, then show that you can explain how and why you built it.**
