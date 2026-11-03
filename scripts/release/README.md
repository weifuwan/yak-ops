# Release Tooling

Release instructions for maintainers. Version validation and smoke scripts remain available for manual runs. Reviewed version notes live in [docs/releases](../../docs/releases/README.md). Published copies and immutable release assets are recorded in [GitHub Releases](https://github.com/weifuwan/yak-ops/releases) and Git tags.

## Prepare and Verify

Prepare a release with `bash scripts/release/prepare-release-version.sh <version>` to align Maven, the frontend, Compose, `.env.example`, and `release.env`.

```sh
bash scripts/release/check-release-metadata.sh vX.Y.Z
bash scripts/release/check-release-migration.sh X.Y.Z
bash scripts/release/check-release-notes.sh X.Y.Z
```

**v1.3.0 resets Flyway to one fresh-install `V1__baseline.sql`.** Only initialize an empty MySQL database with this schema. Databases that already ran v1.0–v1.2 or any v1.3 draft migrations are **not** upgraded by this release. Take backups and recreate the database explicitly, or separately review and execute a data-preserving upgrade plan before deployment. Never point this baseline at a populated database or edit `flyway_schema_history` / run Flyway `repair` to force acceptance.

After v1.3.0 is published, V1 is immutable. Every later release with schema changes adds one new versioned SQL file, without altering previous checksums. The release migration check verifies the baseline, release sequence, and absence of implicit data rewrites in V1.

## Manual Release Verification

The repository no longer has Release Gate or Release Publish GitHub Actions workflows. CI only checks source formatting and backend tests; it does not certify deployability or publish artifacts.

Before publishing, operators must manually build the frontend and Maven distribution, run `scripts/release/verify-distribution.sh`, build and inspect Docker images, and execute `scripts/release/smoke-compose.sh` in a disposable environment. Review the exact commit, version, migration plan, compatibility limitations and release notes. Preserve evidence of every actual test and approval. Never treat skipped or missing verification as PASS.

The local image build/push scripts remain available; tags, GitHub Releases and Docker pushes must be deliberate. Published migration and tag history must remain immutable.
