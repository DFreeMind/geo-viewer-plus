# Geo Viewer Plus

Geo Viewer Plus is a DataGrip/IntelliJ plugin for inspecting spatial result sets beside a database grid.

## Features

- Uses DataGrip's live `DataGrid` context: clicking the action from a table editor or query result opens the current visible result set automatically.
- Own JCEF + Leaflet renderer and own result-set-to-WKT extraction; it does not open or reuse DataGrip's Geo Viewer content.
- Built-in basemap switcher with OSM Standard, OSM Humanitarian, OpenTopoMap, Esri imagery, AMap road/imagery variants and Tencent templates.
- `+ Add map` accepts custom XYZ/TMS tile templates, subdomains and attribution for internal, private, AMap or other compatible services.
- Bidirectional selection: selecting a grid row focuses and highlights its geometry; clicking a geometry selects and scrolls to the corresponding grid row.
- Collapsible floating map controls, persistent default basemap selection, tile-load retry feedback and real XYZ zoom level display.
- Compact feature inspector for point, line and polygon data.
- Tools > Open Geo Viewer Plus, the main toolbar, and the result-grid toolbars.
- A map icon in the main DataGrip toolbar that opens the viewer directly.

## Build

The build expects a local Gradle distribution and IntelliJ/DataGrip SDK. Keep those files outside the repository. DataGrip 2026.1's bundled JBR (Java 25) is needed to read the SDK, while Gradle itself can run on Java 17:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& '.\tools\gradle-8.10.2\bin\gradle.bat' buildPlugin --no-daemon
```

The installable ZIP is written to `build/distributions/geo-viewer-plus-0.6.0.zip`.

After installing the updated ZIP, restart DataGrip (or use **File > Invalidate Caches / Restart** if the old action is still cached). The map icon is added to the main toolbar; it can also be found under **Tools > Open Geo Viewer Plus**. The viewer is placed below the current result panel.

## Next integration seam

The live path is implemented by `OpenGeoViewerAction` and `CustomGeoViewerContent`: the action resolves the current `DataGrid`, `GeoDataExtractor` reads the visible rows and converts geometry values to WKT, and the plugin adds its own `RunnerLayoutUi` content below the current result panel. The old standalone placeholder window is no longer registered.
