# User journeys

Step-by-step journeys of the people who use HomeFlix (DOC-01, issue #5). They come from the brief's scenario "Movie night at home" and its viewing journey, and from the decisions already taken. They feed the acceptance criteria and the demonstration. Requirement IDs refer to the [requirements](requirements.md), and `AS-nn` to its MVP acceptance scenarios.

## Who uses HomeFlix

| Person | Role in the product | Goal |
|---|---|---|
| **Administrator** | `admin` account. Sets HomeFlix up and keeps it running. | Make the household's videos available and keep the household's accounts in order. |
| **Household member** | `user` account, with one or more profiles. | Find something to watch tonight, watch it, and pick it up again later. |

An administrator is also a household member: they can watch like anyone else once they have a profile.

## Decisions every journey follows

- **No account exists on first start.** The first administrator is created on the first-run screen, with a password they choose. HomeFlix ships no default account (ACC-02, requirements open question 2).
- **A profile is chosen after sign-in** (PRF-02). The account signs in, the profile is who is watching. Favourites and progress belong to the profile (PRF-03).
- **The whole interface is in English** and shows no technical detail in its messages (ADR-0021, SEC-04).
- **The main journey is available through the interface** (GEN-03), not only through the API.
- **The rights reminder is shown before the first download** (ACQ-11).
- **A session lasts about 15 minutes and is renewed while the page is open.** An expired session sends the user back to sign in, with a message that says so ([identity design](../architecture/identity-and-permissions.md)).

## Journey 1: first-time setup (administrator)

**Who:** the person who installs HomeFlix. **Goal:** reach a state where the household can watch. **Before:** HomeFlix is started with Docker Compose and a folder of videos is mounted read-only (OPS-01, OPS-04, LIB-11). **Scenarios:** AS-01, AS-02.

| # | The administrator... | HomeFlix... |
|---|---|---|
| 1 | Opens the address of HomeFlix in a browser. | Finds that no administrator exists and shows the first-run screen, not the sign-in screen. |
| 2 | Enters an email address and a password, and confirms the password. | Checks the fields as they type (email format, at least 12 characters, at most 72 bytes) and again on the server (SEC-01). |
| 3 | Submits the form. | Creates the administrator and offers to sign in. The password is stored hashed and is never shown again (ACC-04). |
| 4 | Signs in with the same email and password. | Starts a session and, as the account has no profile yet, asks to create the first profile. |
| 5 | Creates a profile (a name of 1 to 30 characters) and selects it. | Shows the home screen, empty for now, with a short message that says there is nothing to watch yet and where to add folders. |
| 6 | Opens the administration area and adds a media folder. | Checks the folder: it must exist, be readable, be a folder, and be inside the mounted paths. Otherwise it says which of these failed, in plain language (LIB-03, LIB-10, R-02). |
| 7 | Starts indexing. | Scans the folder, finds the supported videos (MP4 with H.264 and AAC), and reports what was added. A video in another format is reported and left out (LIB-04, LIB-02). |
| 8 | Starts indexing a second time. | Adds nothing that is already there: no duplicate (LIB-05). |
| 9 | Opens the home screen. | Shows the new titles under "Recently added" and by category, with a placeholder poster where there is none (CAT-02, CAT-05). |
| 10 | Completes the missing details of a title: description, genre, poster address (optional). | Saves them. Only an `https` poster address on an allowed host is accepted, and the server never fetches it (CAT-09, CAT-11). |
| 11 | Creates an account for each household member, and gives it a role. | Creates the accounts and lists them (ACC-09). |

**If something goes wrong**
- Step 3: another administrator already exists (for example setup was done in another tab). HomeFlix says it is already set up and offers to sign in.
- Step 6: the folder is outside the mounted paths. HomeFlix refuses it and says it is not inside the folders shared with HomeFlix. The technical detail goes to the log only.
- Step 7: the folder becomes unreadable between steps 6 and 7. Indexing stops with a clear message, and the titles already known are not lost.

**The administrator should leave this journey knowing** that setup has to be finished right away, because until it is, the first-run screen is open to anyone who reaches HomeFlix (R-07). The README says so.

## Journey 2: an evening of viewing (household member)

**Who:** a household member with an account. **Goal:** find a film, watch it, and come back to it. **Before:** Journey 1 is done and the member has an account. **Scenarios:** AS-03, AS-04, AS-05.

| # | The member... | HomeFlix... |
|---|---|---|
| 1 | Opens HomeFlix and signs in with an email and a password. | Starts a session. A wrong password, an unknown email and a disabled account all get the same answer, so nobody can tell which accounts exist (ACC-10). |
| 2 | Sees the profiles of the account and picks one. | Makes it the active profile. From now on favourites and progress are those of this profile (PRF-02, PRF-04). |
| 3 | Looks at the home screen. | Shows "Continue watching", "My List", "Recently added" and the categories. Sections with nothing in them are hidden or say they are empty (CAT-07, UX-05). |
| 4 | Types part of a title in the search box, and optionally filters by genre. | Lists the matches, or says nothing matched (CAT-04). |
| 5 | Opens a title. | Shows its poster, description, genre, duration and whether it is available (CAT-03). |
| 6 | Adds it to "My List". | Saves it for this profile only (CAT-08). |
| 7 | Presses play. | Starts the video in the browser, from the beginning or from the saved position (PLY-02, PLY-08). |
| 8 | Pauses, jumps ahead in the timeline, changes the volume, goes full screen. | Answers each at once. Seeking works without downloading the whole file first, because the server answers range requests (PLY-03). |
| 9 | Stops watching in the middle and closes the tab. | Has already saved the position every few seconds (PLY-07). |
| 10 | Comes back the next day, signs in, and chooses the same profile. | Shows the title under "Continue watching". Playing it resumes where the member stopped (PLY-08, AS-04). |
| 11 | Switches to another profile of the same account. | Shows that profile's own list and progress. Nothing of the first profile is visible (AS-05, PRF-03). |
| 12 | Finishes a title. | Counts it as completed. The member can reset its progress to watch it again (PLY-09). |

**If something goes wrong**
- Step 5 or 7: the file was deleted or moved. HomeFlix says the title is not available at the moment. It does not give a file path (PLY-05, AS-09).
- Step 5: the title is still downloading. The page says so and playback is not offered.
- Step 7: the browser cannot play the file. HomeFlix says the format is not supported, with no technical detail.
- Any step: the session has expired. HomeFlix says so and goes to sign-in, then returns to the page the member was on.
- Any step: a service is down. The screen shows a short message and a retry button. Sign-in still works if only the library is down (error model).
- A member changes an identifier in an address to look at another profile's list. The server refuses, whatever the interface shows (AS-07, PRF-04).

## Journey 3: adding a title by peer-to-peer download (administrator)

**Who:** the administrator. **Goal:** get a title that is not in the library, from a source that may legally be shared, and watch it. **Before:** Journey 1 is done, and the peer-to-peer client is running on its own network. **Scenarios:** AS-06, AS-07.

| # | The administrator... | HomeFlix... |
|---|---|---|
| 1 | Opens the acquisition area. | Shows a separate catalogue of content the team has verified as downloadable and redistributable, with its source and its licence (ACQ-01, ACQ-02). |
| 2 | Picks an entry and presses download for the first time. | Shows the rights reminder and asks to confirm before anything starts (ACQ-11). The reminder does not replace checking the rights. |
| 3 | Confirms. | Starts the download if the library answers, and shows it as queued. There is no field that accepts an address or a magnet link (ACQ-09, ACQ-10). |
| 4 | Watches the screen. | Shows the state (queued, downloading, completed or failed) and the progress, updated as it changes (ACQ-04). |
| 5 | Waits. | When the file is complete, checks its size and format, and hands it to the library (ACQ-06, R-19). |
| 6 | Goes to the catalogue. | Shows the title as available. Until then it was shown as downloading, with no play button (ACQ-07, CAT-01). |
| 7 | Plays it. | Plays like any other title, as in Journey 2. |

**If something goes wrong**
- Step 3: the library does not answer. Nothing is started, and HomeFlix says to try again.
- Step 4: the download fails or is cancelled. The state is failed, with a short reason. The title stops showing as downloading, and the administrator can start it again (ACQ-03).
- Step 5: the size or format is not what the catalogue says. The job fails, and nothing is added to the library.
- Step 5: the library cannot be reached for five minutes. The job fails and says the title could not be added ([consistency rules](../architecture/consistency-rules.md)).
- A household member who is not an administrator opens the acquisition address or calls its API. The server refuses (ACQ-05, AS-07).

## Journey 4: looking after the household (administrator)

**Who:** the administrator. **Goal:** keep accounts, the catalogue and the library in order. **Before:** HomeFlix is in use. **Scenarios:** AS-07, AS-08, AS-09.

| # | The administrator... | HomeFlix... |
|---|---|---|
| 1 | Opens the administration area. | Brings together accounts, metadata, indexing, acquisition and the state of the services (UX-06). |
| 2 | Adds a video to the mounted folder and starts indexing. | Finds the new file and adds it. A file removed from the folder shows as unavailable and keeps its information and the members' progress (LIB-07). |
| 3 | Removes a title from the catalogue. | Removes the entry, with its favourites and progress. The video file on disk is not touched (LIB-12). |
| 4 | Disables the account of a member who has left. | The member cannot sign in any more, and loses access when the current session runs out, within about 15 minutes (ACC-10). |
| 5 | Deletes an account. | Deletes its profiles, favourites and progress in every service. The videos are not touched (SEC-07). If a service is down, nothing is deleted and the administrator is asked to try again. |
| 6 | Restarts the containers. | Everything is still there: accounts, catalogue, favourites and progress (OPS-08, PLY-10, AS-08). |

**Protections.** The last administrator cannot be disabled, deleted or demoted, so HomeFlix always has one.

## What the journeys leave out

- **Sign-up by a member.** Accounts are created by the administrator. There is no public registration or invitation (out of scope).
- **Password reset.** There is no email service and no "forgotten password" feature in the MVP.
- **Remote access.** HomeFlix is used on the home network. The requirements for Internet exposure are documented, not built (SEC-11).

## Where the journeys are checked

| Journey | Acceptance scenarios | Demonstration |
|---|---|---|
| 1. First-time setup | AS-01, AS-02 | Start of the demo video and of the presentation. |
| 2. An evening of viewing | AS-03, AS-04, AS-05, AS-09 | Main part of the demo. |
| 3. Adding a title by peer-to-peer download | AS-06, AS-07 | The real transfer on the presentation network (ACQ-14). |
| 4. Looking after the household | AS-07, AS-08, AS-09 | Short close of the demo. |

The detailed pass and fail criteria of each scenario are written in the acceptance criteria (issue #7). The journeys are reviewed with the keyboard and on a phone-sized screen before delivery (UX-07, K7).

## Assumptions to confirm

These are choices the journeys make where no requirement or issue says otherwise. They are decided in the issues named, and this page is corrected if the decision differs.

- **An account with no profile is asked to create one** right after sign-in, on the profile screen (G9, G10). This is how the first administrator and every new member start.
- **An account with exactly one profile may skip the profile screen** and have it selected automatically. The server rule does not change ([identity design](../architecture/identity-and-permissions.md#selecting-the-active-profile)).
- **Indexing is started by the administrator** from the interface (LIB-06). It does not run by itself in the MVP, except for a downloaded title, which is handed to the library when the download completes.
