# Repository Guidelines

## Project Structure & Module Organization
`ruoyi-admin` is the main Spring Boot entrypoint. Shared Java modules live in `ruoyi-framework`, `ruoyi-system`, `ruoyi-common`, and `ruoyi-quartz`. The Vue 2 frontend lives in `ruoyi-ui/src`, with quant pages under `ruoyi-ui/src/views/quant`. Python quant services and CLI commands live in `python/src/quant_road`, with tests in `python/tests`. Scripts are in `scripts`, SQL bootstrap files in `sql`, and references in `docs`. Treat `admin/` and `ruoyi-generator/` as legacy/reference code, not the default development path.

## Build, Test, and Development Commands
Start the main backend from the repo root with `mvn -pl ruoyi-admin -am spring-boot:run`. Run Java tests with `mvn -pl ruoyi-admin -am test`, and package with `mvn -pl ruoyi-admin -am -DskipTests package`. For the frontend, use `cd ruoyi-ui && npm install && npm run dev` for local work and `npm run build:prod` for production output. For Python, use `cd python && pip install -r requirements.txt && pip install -e .`, then run tests with `$env:PYTHONPATH='src'; python -m unittest discover -s tests -p "test_*.py"`. Use `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1` for the quant UI smoke suite and `scripts/full-regression.ps1` before broad cross-stack changes.

## Coding Style & Naming Conventions
Follow the existing style in each layer instead of reformatting unrelated code. Java uses 4-space indentation, K&R-style spacing, and opening braces on a new line; keep classes `PascalCase`, methods/fields `camelCase`, and tests ending in `Test`. Vue/JS files use 2-space indentation; keep components in `.vue` files and prefer kebab-case helper filenames such as `dashboard-page-state.js`. Python follows PEP 8 with type hints where already present; keep modules `snake_case` and constants `UPPER_SNAKE_CASE`.

## Testing Guidelines
Add or update tests in the layer you touch. Java tests live under `ruoyi-admin/src/test/java` and use JUnit 5 with Mockito. Python tests live under `python/tests` and follow `test_*.py`. Frontend regression checks live under `ruoyi-ui/tests` as `*.test.cjs` Playwright suites. There is no published coverage threshold; the expectation is targeted test coverage for each behavior change plus the relevant smoke script for UI or cross-service work.

## Quant Page Rules
For all work under `ruoyi-ui/src/views/quant`, follow `docs/quant/page-design-regulations.md`, `docs/quant/page-delivery-regulations.md`, and `docs/quant/page-review-checklist.md`. Treat these pages as operations consoles rather than presentation pages. First screens must not use hero sections, tall empty cards, illustrative empty states, or repeated summary blocks. Do not mark a quant page issue as fixed without real logged-in screenshots of the target state.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit style with scopes, for example `test(quant): ...`, `docs(quant): ...`, and `refactor(quant): ...`. Keep commits focused and scoped to one concern. PRs should include a short summary, impacted modules, verification commands, linked issues/tasks, and screenshots for any changes under `ruoyi-ui/src/views/quant`.
