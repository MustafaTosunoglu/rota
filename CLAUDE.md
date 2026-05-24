# Rota — Claude Code Instructions

## Source of Truth
The project plan is at `docs/ROTA_PLAN.md`. Read it fully before taking any action.
Never make architectural decisions that contradict it. If a conflict arises, stop and ask.

## Before Starting Any Phase
1. Read the phase section in the plan carefully.
2. Write a short summary of what you will do (components, files, DB tables, etc.).
3. List any assumptions you are making.
4. List any questions you have — even small ones.
5. Wait for my confirmation before writing a single line of code.

## During Work
- Work in small, verifiable steps. After each meaningful step, tell me what you did and what you verified.
- Run tests after every non-trivial change. Do not proceed if tests fail.
- If you encounter something unexpected (a dependency conflict, a design gap, an ambiguity), STOP and ask. Do not guess.
- Never modify files outside the current phase's scope without telling me first.

## Git / Commits
- Do NOT commit or push anything. Stage changes if needed, but the human reviews and commits.
- When a logical unit of work is complete, tell me: "Ready to commit. Suggested message: `<message>`"
- Never touch the `main` branch directly.

## Security Rules (non-negotiable)
- Every table that contains tenant data MUST have `tenant_id` + RLS policy. No exceptions.
- Sensitive fields (API keys, secrets, tokens) MUST use `EncryptedStringConverter`. Flag if unsure.
- No secret, credential, or key may appear in: code, logs, API responses, or git history.
- SSRF protection (private IP block) must be in place before any proxy code goes live.

## Testing Rules
- RLS cross-tenant leak tests are mandatory. If a phase touches DB schema, these tests must pass.
- Do not mark a phase complete if any test is failing.
- New DB migrations must have corresponding test coverage.

## Communication Style
- Be explicit about what you're about to do before doing it.
- If a task will touch more than 5 files, list them first and get confirmation.
- When done with a phase, give me: ① what was built, ② what tests pass, ③ what to review before committing, ④ suggested commit message.

## Design Guidelines
- Style reference: Linear.app (clean, minimal, developer-focused)
- Primary: #093C5D
- Font: Inter (via Google Fonts CDN)
- Radius: rounded-lg globally
- Always support light + dark mode using shadcn/ui CSS variables
- No decorative gradients or animations in v1 — function over form
- Logo placeholder: SVG letter mark "R" in primary color
- When in doubt about visual design, choose the simpler option and note it for review
