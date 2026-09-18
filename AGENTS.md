# Geo Viewer Plus Agent Guide

This file is the working contract for AI agents contributing to this repository. Read it before changing code.

## Project identity

- Plugin ID: `cn.duqimeng.geo-viewer-plus`
- Gradle group: `cn.duqimeng.geo-viewer-plus`
- Java package: `cn.duqimeng.geoviewerplus`
- Product: a DataGrip Geo Viewer Plus plugin for spatial result sets.
- Main implementation: `src/main/java/cn/duqimeng/geoviewerplus/CustomGeoViewerContent.java`.
- Map UI: `src/main/resources/geo-viewer-plus.html`.

The plugin owns its JCEF/Leaflet map view and must not bridge to or modify DataGrip's built-in Geo Viewer.

## Required workflow

1. Inspect the current working tree and relevant files before editing. Preserve unrelated user changes.
2. Keep each change focused. Do not change plugin IDs, Java namespaces, compatibility ranges, or release behavior casually.
3. Use `apply_patch` for source and documentation edits. Do not use destructive reset or checkout commands.
4. After implementation, run the relevant checks. For plugin changes, run:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
   & '.\tools\gradle-8.10.2\bin\gradle.bat' verifyPlugin buildPlugin --no-daemon
   ```

5. Inspect the generated ZIP when metadata or packaging changes. Confirm the plugin ID, version, namespace, and change notes in the packaged `META-INF/plugin.xml`.
6. Do not claim that DataGrip UI behavior was live-tested unless the UI was actually opened and exercised. Report build/static verification separately from live verification.
7. When the requested change is implemented and checks pass, create an atomic Git commit with a descriptive message. Push to `origin/main` when the task includes publishing/synchronizing the repository. Otherwise report the commit and wait for push authorization.
8. Finish with `git status --short --branch` and report the commit, tests, package path, and any known warnings.

## Versioning and releases

- The single source of truth is `pluginVersion` in `gradle.properties`.
- Rebuilding or fixing code does not change the version.
- Use SemVer: patch for fixes (`0.1.1`), minor for backward-compatible features (`0.2.0`), major for breaking changes.
- Every Marketplace release must add `release-notes/<version>.html` with concise English-first notes.
- Use `.\scripts\publish-plugin.ps1 -Bump patch|minor|major` for a release after the Marketplace token is configured locally.
- Never commit or print the Marketplace token. It must be supplied through `ORG_GRADLE_PROJECT_intellijPlatformPublishingToken`.
- Store release screenshots under `docs/media/<version>/`. Marketplace Media uploads are managed in the Marketplace admin page.
- Do not reuse a Marketplace version. Do not force-move a published tag or replace a release asset unless the user explicitly requests a release correction.

## Build and compatibility

- The repository intentionally ignores local DataGrip SDK, JDK, Gradle distribution, `.gradle`, and `build` output.
- The current local build targets DataGrip build range `261` through `262.*`.
- The Gradle IntelliJ Plugin 1.x warning for 2024.2+ and deprecated `JBCefJSQuery.create` warnings are known; do not hide them. New failures must be investigated.
- Marketplace publishing is only allowed after `verifyPlugin` and `buildPlugin` succeed.

## Geo Viewer behavior contract

- Opening the action from a DataGrip table/result grid loads the current visible spatial rows.
- Supported geometry data includes point, line, polygon, and their multi-geometries where extraction can produce WKT.
- Selecting a result-grid row focuses and highlights its map geometry.
- Clicking a map geometry selects, scrolls to, and highlights the corresponding result-grid row.
- The map toolbar is collapsible and the selected default basemap is persistent.
- The lower-left status must show the actual XYZ tile zoom level (`Z=`), not a Z-coordinate exaggeration control.
- Built-in basemaps must include reliable OSM options and domestic-accessible options where possible. Custom XYZ/TMS sources must support attribution and subdomains.
- Map tile errors should be retried and surfaced without breaking spatial feature interaction.

## UI and data safety

- Do not include database hostnames, credentials, personal data, or screenshots containing sensitive data in commits or Marketplace media.
- Keep map attribution visible for every source.
- Avoid blocking map interactions with unnecessary auto-pan or modal dialogs.
- Prefer smooth, bounded map animations and clear selection states over abrupt jumps.
- Keep user-facing Marketplace descriptions and release notes concise, accurate, and free of unsupported claims.

## Multi-agent collaboration

- Before starting, state the file scope and objective. Avoid editing the same files as another agent.
- Split work by concern where possible: UI/HTML, Java/DataGrid integration, build/release, and documentation/media.
- Each agent must leave a focused commit or clearly report uncommitted changes for the coordinating agent to integrate.
- The coordinating agent owns final integration, full verification, version changes, release notes, and publishing.
- Never use force-push, history rewriting, or destructive cleanup as a coordination shortcut.
