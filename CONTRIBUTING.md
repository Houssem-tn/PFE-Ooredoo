# Contributing

Backend service for the Ooredoo Field Service Management platform (Spring Boot / Java 17 / MongoDB).

## Branching model

- `main` — stable, production-ready code only. Never commit directly.
- `develop` — integration branch. All feature/fix branches merge here first.
- `feature/<feature-name>` — new functionality, branched from `develop`.
- `fix/<bug-name>` — non-urgent bug fixes, branched from `develop`.
- `hotfix/<issue-name>` — urgent production fixes, branched from `main`.

Examples: `feature/stock-monitoring`, `fix/websocket-reconnection`, `hotfix/jwt-expiry-crash`.

Use lowercase, hyphen-separated names that describe the change, not a ticket number alone.

## Commit message conventions

Format: `<type>: <short description>`

| Type       | Use for                                      |
|------------|-----------------------------------------------|
| `feat`     | new feature                                   |
| `fix`      | bug fix                                       |
| `test`     | adding or updating tests                      |
| `refactor` | code change that neither fixes nor adds a feature |
| `docs`     | documentation only                            |
| `ci`       | CI/CD pipeline changes                        |
| `chore`    | tooling, dependencies, no production code change |

Examples:
```
feat: add stock monitoring
fix: correct websocket reconnection
test: add case lifecycle tests
refactor: simplify material return service
docs: update deployment guide
ci: add backend test workflow
```

Keep the subject line under ~72 characters; add a body if the change needs explanation.

## Pull request workflow

1. Branch from `develop` (or `main` for a `hotfix/*`).
2. Commit using the conventions above.
3. Push your branch and open a PR **into `develop`** (hotfixes target `main`, then get back-merged into `develop`).
4. Fill in what changed and why; link the related issue/task if any.
5. Request review from at least one other contributor before merging.
6. Squash or rebase merge to keep history readable — avoid noisy merge commits for small branches.
7. Delete the branch after merge.

## Tests before merging

- Run `./mvnw test` locally and ensure it passes before opening a PR.
- Do not merge a PR with failing or skipped tests without an explicit justification in the PR description.

## Secrets — do not commit them

- Never commit real credentials, API keys, JWT secrets, or `.env` files. See `.gitignore` and `.env.example`.
- Application configuration (`application*.properties`) must only contain environment-variable placeholders (`${VAR_NAME:safe-default}`), never real secrets.
- If you accidentally commit a secret, rotate it immediately and notify the team — removing it from a later commit does not remove it from git history.

## Release flow

```
feature/* or fix/*  --PR-->  develop  --PR-->  main
hotfix/*             -------------PR----------->  main  (then back-merge main into develop)
```

- `develop` is merged into `main` for releases, after tests pass and the integration branch is verified stable.
- `hotfix/*` branches merge directly into `main` for urgent fixes, then `main` is merged back into `develop` so the fix isn't lost.
