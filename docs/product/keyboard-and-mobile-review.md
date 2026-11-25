# Keyboard and mobile review of the main journeys

Results of the keyboard and mobile review of the main user journeys (issue K7 #90; UX-07, UX-04, UX-05, PLY-06). The acceptance plan and run (K8, #91) links to this page instead of repeating it.

| | |
|---|---|
| Journeys | Sign-in, profile selection, search, movie details, playback, administration |
| Frontend reviewed | Branch of #90, commit `e4c2463` |
| Reviewed by | Thomas Rousseau |
| Date | 2026-11-25 |

## How the review was done

1. **Code review** of `frontend/src`: focus handling, focus styles, labels and roles, page titles, loading, error and empty states, responsive rules.
2. **Fixes** of the problems found, each with a test that fails without the fix.
3. **Keyboard run in the browser**: the six journeys, with Tab, Shift+Tab, Enter, Space, Escape and the player shortcuts, without a mouse.
4. **Mobile run** at a 375 px wide viewport (browser device mode).

## Keyboard

**Result: pass.** The six journeys can be completed with the keyboard only (browser run on 2026-11-25, after the fixes below).

### Problems found and fixed

| # | Where | Problem | Fix |
|---|---|---|---|
| 1 | Player (`VideoPlayer.tsx`) | During playback, the controls hide after 3 s without activity. Only the bottom bar kept them shown while it had focus, so a focused **Back** link in the top bar became invisible. | The top bar keeps the controls shown while it has focus, like the bottom bar. |
| 2 | Movie details (`MovieDetailsPage.tsx`) | **My List** was `disabled` while saving. A disabled button loses focus, so focus fell back to the top of the page after Enter or Space. | `aria-disabled` while saving, with the same dimmed style. The button keeps focus, and a second press during the save is ignored. |
| 3 | Every page inside the main layout (`AppLayout.tsx`) | After following a link, the link was often gone and focus fell back to the top of the document. Screen reader users were not told that a new page was shown. | Focus moves to the main content after a navigation. Not on the first load, and not when the new page already moved focus into its content (Search focuses its field). |
| 4 | Every page inside the main layout (`AppLayout.tsx`), found in review of #193 | `BrowserRouter` keeps the scroll position of the previous page. A film opened from far down the home page opened already scrolled down, without its title and Play button on screen, most visible at 375 px. | A page opened from a link starts at the top. Back and forward are left to the browser, which restores the position. |

Tests: `App.test.tsx` (fixes 3 and 4), `PlayerPage.test.tsx` and `MovieDetailsPage.test.tsx`, one per fix. Each fails without its fix. The frontend suite passes (173 tests).

### Checked, no change needed

- **Focus indicator**: a 2 px outline in the accent colour (`#E8A33D`), offset 3 px, on every focusable element (`:focus-visible`). Contrast 8.9:1 on the page background. In the horizontal film rows, which clip what overflows, the ring is shown whole (browser check, 2026-11-25).
- **Skip link** "Skip to content" to the main content.
- **Dialog**: focus moves in on opening, stays inside (Tab and Shift+Tab), Escape closes it, and focus returns to where it was.
- **Player**: Play has focus on opening. Shortcuts Space or K (play and pause), Left and Right arrows (10 s back and forward), Up and Down arrows (volume), M (mute), F (full screen). Every button has a label, and the sliders announce their value in words.
- **State of toggles**: `aria-pressed` on My List, `aria-current` on the active profile and on the current page in the navigation.
- **Forms**: after an error, focus moves to the first field in error. After an action, focus moves to the message, which is announced (`role="status"` or `role="alert"`).
- **Page titles**: every page sets its own browser tab title.

### Contrast of the colour tokens

WCAG contrast ratios, computed from `frontend/src/styles/tokens.css`. AA asks for 4.5:1 for normal text.

| Text colour | On `--bg` `#0E0E10` | On `--surface` `#1A1A1E` | On `--surface-2` `#2A2A2E` |
|---|---|---|---|
| `--text` `#F4F1EA` | 17.1:1 | 15.4:1 | 12.7:1 |
| `--text-body` `#D9D5CE` | 13.2:1 | 11.9:1 | 9.8:1 |
| `--text-muted` `#A6A3A0` | 7.7:1 | 6.9:1 | 5.7:1 |
| `--text-subtle` `#8C8882` | 5.5:1 | 4.9:1 | 4.1:1 |
| `--accent` `#E8A33D` | 8.9:1 | 8.0:1 | 6.6:1 |

Text on the accent colour (`--on-accent` on `--accent`, primary buttons): 8.9:1.

The only pair under 4.5:1 is `--text-subtle` on `--surface-2`, and the interface does not use it for text. `--text-subtle` colours only the decorative logo of the placeholder poster and the "missing" note on the movie page, which sits on a dark background.

## Mobile (375 px)

**Result: pass.** The six journeys work at a 375 px wide viewport in the browser's device mode, including playback of a film (browser run on 2026-11-25). No fix was needed.

Points looked at in each journey:

- the header, where the e-mail, the Admin badge, the active profile, "Switch profile" and "Sign out" share one line;
- the administration lists (Accounts, Library, Downloads), which have no small-screen rule;
- the player controls;
- no horizontal scrolling of the page, apart from the film rows.

Device mode reproduces the width and touch input, not a real phone browser. The run on a real phone is not part of this review.

## Open items

None for K7.
