# ADR-0019: CSS Modules with design tokens in CSS custom properties

- **Status:** Proposed
- **Date:** 2026-11-17
- **Deciders:** Thomas Rousseau (frontend lead). Awaiting review by another learner, as required by the ADR process.
- **Related:** issue #35, ADR-0005, ADR-0016, requirements ARC-09, UX-02, UX-03 and UX-04

## Context

ADR-0005 chose React with Vite and ADR-0016 chose TypeScript, but the styling approach was left open. As frontend lead, Thomas Rousseau has reviewed ADR-0016 and confirms TypeScript. The interface must be responsive and readable (UX-03), keyboard accessible with visible labels and clear focus states (UX-04), and have its own visual identity that does not copy any existing streaming product (UX-02). Every frontend screen issue (G6, H11, H12, I5, J9, K1) waits on this choice. The team has a short schedule and no prior shared experience with a CSS framework.

## Decision

We will style the frontend with **CSS Modules** and **design tokens declared as CSS custom properties**.

- Each component has its own `*.module.css` file. Vite supports CSS Modules natively, so no dependency is added.
- One global stylesheet declares the tokens on `:root`, plus a minimal reset. Components use tokens only and never hard-code colours, fonts or radii.
- We will not use a component library. Screens are built from native HTML elements (`button`, `label`, `input`, `dialog`) first, because they are accessible by default.
- The MVP has a single dark theme.

The initial tokens are:

| Token | Value | Role |
|---|---|---|
| `--bg` | `#0E0E10` | Page background |
| `--bg-hero` | `#18140F` | Hero and featured areas |
| `--surface` | `#1A1A1E` | Cards, panels |
| `--surface-2` | `#2A2A2E` | Raised elements, hover |
| `--border` | `#45423C` | Decorative separators |
| `--text` | `#F4F1EA` | Headings, primary text |
| `--text-body` | `#D9D5CE` | Body text |
| `--text-muted` | `#A6A3A0` | Secondary text |
| `--text-subtle` | `#8C8882` | Hints, metadata |
| `--accent` | `#E8A33D` | Primary actions, focus ring, active state |
| `--on-accent` | `#0E0E10` | Text on accent |
| `--font-display` | `'Bebas Neue', sans-serif` | Titles, logo |
| `--font-body` | `'Inter', system-ui, sans-serif` | Everything else |
| `--radius-sm` / `--radius-md` / `--radius-lg` | `6px` / `8px` / `12px` | Corners |
| `--gutter` | `clamp(20px, 4vw, 56px)` | Responsive page side spacing |

Accessibility rules that every component follows:

- Interactive elements show a visible `:focus-visible` outline in `--accent`, at least 2px wide. An outline is never removed without a visible replacement.
- Text meets WCAG AA contrast (4.5:1). `--text-subtle` is not used on `--surface-2` (4.06:1).
- `--border` (1.93:1 on `--bg`) is decorative only. Form fields and other controls need a boundary of at least 3:1 (WCAG 1.4.11), for example `--text-subtle`.
- Font sizes use `rem`, and layouts are mobile first.
- Animations are disabled under `prefers-reduced-motion`.

## Alternatives considered

- **Tailwind CSS:** fast once learned, but it adds a dependency and a learning curve for the whole team, and long class lists make components harder to review.
- **A component library (MUI, Mantine, Chakra):** accessible components out of the box, but a generic look that works against UX-02, a heavier bundle, and theming work to make it our own.
- **CSS-in-JS (styled-components, Emotion):** styles live next to the logic, but it has a runtime cost, and it is less recommended with React 19.
- **Global CSS only:** no tooling at all, but class names collide as the number of screens grows.

## Consequences

- No new dependency, and nothing more to configure or pin: CSS Modules work with the existing Vite and Vitest setup.
- The visual identity lives in one place. Changing a colour or a font is a change to the tokens only.
- Styles are scoped per component, so screens can be built in parallel without conflicts.
- Accessible widgets that HTML does not provide (menus, tabs) must be written and tested by us. We keep them to a minimum.
- Bebas Neue and Inter are both under the SIL Open Font License. They are self-hosted with the frontend, never loaded from an external CDN, because HomeFlix runs on a home network that may have no Internet access.
- Applying the tokens and the logo to the screens is done in issue #61 (H12).
