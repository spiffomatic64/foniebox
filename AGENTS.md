# Repository guidance

## Repository knowledge

Load only the repository map relevant to the current task, then verify behavior
against the current source:

- `docs/agent/architecture.md`: components and runtime boundaries.
- `docs/agent/codebase_map.md`: where to find source, resources, and build files.
- `docs/agent/workflows.md`: folder scanning, playback, and build workflows.

Update materially affected maps when changing the architecture or workflows.

## Working conventions

Use JDK 21 and the checked-in Gradle wrapper. Validate changes with
`assembleDebug lintDebug` when they affect source or build configuration.
Keep personal paths, credentials, signing material, and device identifiers out
of source, documentation, and version control. Local SDK and IDE settings are
ignored. Preserve bundled third-party license notices.
