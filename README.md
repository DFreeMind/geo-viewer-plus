# Geo Viewer Plus

Geo Viewer Plus is a DataGrip/IntelliJ plugin for inspecting spatial result sets beside a database grid.

Plugin ID: `cn.duqimeng.geo-viewer-plus`  
Java package: `cn.duqimeng.geoviewerplus`

## Features

- Uses DataGrip's live `DataGrid` context: clicking the action from a table editor or query result opens the current visible result set automatically.
- Own JCEF + Leaflet renderer and own result-set-to-WKT extraction; it does not open or reuse DataGrip's Geo Viewer content.
- Built-in basemap switcher with OSM Standard, OSM Humanitarian, OpenTopoMap, OpenFreeMap's keyless open-source vector tiles (updated weekly), Esri World Imagery (when ArcGIS Online is reachable), plus AMap road/imagery and a Tencent compatibility source. Domestic sources are explicitly marked GCJ-02.
- A display-only coordinate selector converts WGS84 result geometries to GCJ-02 for AMap/Tencent or BD-09 for Baidu. Conversion happens locally in the browser, is limited to mainland-China coordinates, and never writes to or changes the database value.
- `+ Add map` accepts HTTPS XYZ/TMS and MVT tile templates, subdomains and attribution. HTTPS protects database-result location privacy when tiles are requested.
- Bidirectional selection: selecting a grid row focuses and highlights its geometry; clicking a geometry selects and scrolls to the corresponding grid row.
- **Refresh result data** re-reads the active grid after a query rerun, edit, filter, or sort.
- Collapsible floating map controls, persistent default basemap selection, tile-load retry feedback, actual XYZ zoom level display, and clear feedback when rows are skipped or the 500-row map limit applies.
- EPSG:4326, EPSG:4979, EPSG:4490/CGCS2000, and EPSG:3857 geometry values are rendered in WGS84; unknown coordinate reference systems are skipped rather than plotted incorrectly.
- Compact feature inspector for point, line and polygon data.
- Tools > Open Geo Viewer Plus, the main toolbar, and the result-grid toolbars.
- A map icon in the main DataGrip toolbar that opens the viewer directly.

## Build

The build expects a local Gradle distribution and IntelliJ/DataGrip SDK. Keep those files outside the repository. DataGrip 2026.1's bundled JBR (Java 25) is needed to read the SDK, while Gradle itself can run on Java 17:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& '.\tools\gradle-8.10.2\bin\gradle.bat' buildPlugin --no-daemon
```

The installable ZIP is written to `build/distributions/geo-viewer-plus-0.1.0.zip`.

## Versioning and releases

The version is defined once in `gradle.properties` as `pluginVersion`. Local rebuilds and normal fixes reuse the same version. Only a Marketplace release changes it:

- `0.1.0` — first public preview release.
- Patch fixes increment the patch number, for example `0.1.1`.
- New backward-compatible feature groups increment the minor number, for example `0.2.0`.
- Breaking changes increment the major number after the initial preview phase.

The first Marketplace upload must be done manually from `build/distributions/geo-viewer-plus-0.1.0.zip`. After the plugin page exists, later releases can use `publishPlugin` with the `intellijPlatformPublishingToken` Gradle property supplied through an environment variable or CI secret.

To publish a later patch release from the maintained Windows build environment, create a JetBrains Marketplace token and set it only in the local user environment:

```powershell
[Environment]::SetEnvironmentVariable("ORG_GRADLE_PROJECT_intellijPlatformPublishingToken", "YOUR_MARKETPLACE_TOKEN", "User")
```

Then create release notes in `release-notes/<new-version>.html`, open a new PowerShell session, and run:

```powershell
.\scripts\publish-plugin.ps1 -Bump patch
```

The release script injects that HTML into the Marketplace change notes and the IDE's plugin details. You can pass a different file with `-NotesFile path\to\notes.html`.

Keep Marketplace screenshots in `docs/media/<version>/`. The Marketplace Media section currently requires uploading those images in the plugin admin page; the official Gradle publish task does not manage page media.

Use `-Bump minor` for a backward-compatible feature group or `-Bump major` for a breaking release. The script checks for a clean worktree, increments `pluginVersion`, runs `publishPlugin`, commits the release, and pushes the Git tag. The token is never stored in the repository.

Install the ZIP from **Settings | Plugins | ⚙ | Install Plugin from Disk…**. Geo Viewer Plus declares dynamic-plugin support, so DataGrip can normally enable or update it immediately without restarting. If an older viewer is open or the IDE reports that unloading failed, close its Geo Viewer Plus tab and retry; restart only when DataGrip explicitly requests it. The map icon is added to the main toolbar and **Tools | Open Geo Viewer Plus**.

## Next integration seam

The live path is implemented by `OpenGeoViewerAction` and `CustomGeoViewerContent`: the action resolves the current `DataGrid`, `GeoDataExtractor` reads the visible rows and converts geometry values to WKT, and the plugin adds its own `RunnerLayoutUi` content below the current result panel. The old standalone placeholder window is no longer registered.
