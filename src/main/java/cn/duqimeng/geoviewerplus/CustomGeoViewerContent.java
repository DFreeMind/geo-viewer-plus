package cn.duqimeng.geoviewerplus;

import com.intellij.database.console.JdbcConsole;
import com.intellij.database.console.client.DatabaseSessionClientWithFile;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.DataGridSessionClient;
import com.intellij.database.datagrid.DataGridUtil;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.run.session.LogView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.JBUI;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Base64;

/** Our own bottom result content: DataGrid data in, custom JCEF/Leaflet map out. */
public final class CustomGeoViewerContent implements com.intellij.openapi.Disposable {
    private static final String CONTENT_ID = "Geo Viewer Plus";
    private static final Key<CustomGeoViewerContent> CONTENT_KEY = Key.create(CONTENT_ID);
    private static final String HTML_RESOURCE = "/geo-viewer-plus.html";
    private static final String CUSTOM_SOURCES_KEY = "geo.viewer.plus.custom.sources.v1";
    private static final String DEFAULT_SOURCE_KEY = "geo.viewer.plus.default.source.v1";
    private static final JBColor PANEL = new JBColor(new java.awt.Color(248, 250, 252), new java.awt.Color(15, 23, 42));
    private static final JBColor BORDER = new JBColor(new java.awt.Color(203, 213, 225), new java.awt.Color(51, 65, 85));

    private final DataGrid grid;
    private final JBCefBrowser browser;
    private final CefLoadHandler loadHandler;
    private final JBCefJSQuery selectQuery;
    private final JBCefJSQuery readyQuery;
    private final JBCefJSQuery sourceQuery;
    private final JBCefJSQuery addSourceQuery;
    private final JBCefJSQuery editSourceQuery;
    private final JBCefJSQuery removeSourceQuery;
    private final JBCefJSQuery defaultSourceQuery;
    private final JPanel component;
    private final List<MapSource> sources = new ArrayList<>();
    private final String payload;
    private final Timer selectionTimer;
    private boolean pageReady;
    private String lastSelectionKey = "";
    private String selectedSourceName = "";
    private String defaultSourceName = "";

    private CustomGeoViewerContent(DataGrid grid) {
        this.grid = grid;
        this.payload = payload(GeoDataExtractor.extract(grid, 500));
        this.browser = new JBCefBrowser();
        this.loadHandler = new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame == null || !frame.isMain()) return;
                pageReady = true;
                SwingUtilities.invokeLater(() -> {
                    if (!browser.isDisposed()) bootstrapPage();
                });
            }
        };
        browser.getJBCefClient().addLoadHandler(loadHandler, browser.getCefBrowser());
        this.selectQuery = JBCefJSQuery.create(browser);
        this.readyQuery = JBCefJSQuery.create(browser);
        this.sourceQuery = JBCefJSQuery.create(browser);
        this.addSourceQuery = JBCefJSQuery.create(browser);
        this.editSourceQuery = JBCefJSQuery.create(browser);
        this.removeSourceQuery = JBCefJSQuery.create(browser);
        this.defaultSourceQuery = JBCefJSQuery.create(browser);
        this.component = new JBPanel<>(new BorderLayout());
        this.selectionTimer = new Timer(180, event -> syncGridSelection());

        sources.addAll(defaultSources());
        sources.addAll(loadCustomSources());
        String savedDefault = PropertiesComponent.getInstance().getValue(DEFAULT_SOURCE_KEY, "");
        if (sources.stream().anyMatch(source -> source.name().equals(savedDefault))) {
            selectedSourceName = savedDefault;
        } else if (sources.stream().anyMatch(source -> source.name().equals("OSM Standard"))) {
            selectedSourceName = "OSM Standard";
        } else if (!sources.isEmpty()) {
            selectedSourceName = sources.get(0).name();
        }
        defaultSourceName = selectedSourceName;
        selectQuery.addHandler(rowText -> {
            try {
                int row = Integer.parseInt(rowText);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (grid.isReady()) {
                        grid.getSelectionModel().clearSelection();
                        ModelIndex<GridRow> modelRow = ModelIndex.forRow(grid, row);
                        ModelIndex<GridColumn> column = grid.getContextColumn();
                        if (column == null && !grid.getVisibleColumns().asList().isEmpty()) {
                            column = grid.getVisibleColumns().asList().get(0);
                        }
                        if (column != null) {
                            grid.getSelectionModel().setSelection(modelRow, column);
                        }
                        grid.getSelectionModel().setRowSelection(modelRow, true);
                        if (column != null) grid.showCell(row, column);
                        lastSelectionKey = "[" + row + "]";
                    }
                }, ModalityState.any());
            } catch (NumberFormatException ignored) {
            }
            return new JBCefJSQuery.Response("");
        });
        readyQuery.addHandler(ignored -> {
            pageReady = true;
            SwingUtilities.invokeLater(this::bootstrapPage);
            return new JBCefJSQuery.Response("");
        });
        sourceQuery.addHandler(sourceName -> {
            SwingUtilities.invokeLater(() -> {
                if (sources.stream().anyMatch(source -> source.name().equals(sourceName))) {
                    selectedSourceName = sourceName;
                    browser.runJavaScript("window.geoPlus && window.geoPlus.switchSource(" + quote(sourceName) + ");");
                }
            });
            return new JBCefJSQuery.Response("");
        });
        addSourceQuery.addHandler(ignored -> {
            SwingUtilities.invokeLater(() -> addMapSource(null));
            return new JBCefJSQuery.Response("");
        });
        editSourceQuery.addHandler(sourceName -> {
            SwingUtilities.invokeLater(() -> editMapSource(sourceName));
            return new JBCefJSQuery.Response("");
        });
        removeSourceQuery.addHandler(sourceName -> {
            SwingUtilities.invokeLater(() -> removeMapSource(sourceName));
            return new JBCefJSQuery.Response("");
        });
        defaultSourceQuery.addHandler(sourceName -> {
            SwingUtilities.invokeLater(() -> {
                if (sources.stream().anyMatch(source -> source.name().equals(sourceName))) {
                    defaultSourceName = sourceName;
                    PropertiesComponent.getInstance().setValue(DEFAULT_SOURCE_KEY, defaultSourceName);
                    browser.runJavaScript("window.geoPlus && window.geoPlus.setDefaultSource(" + quote(sourceName) + ");");
                }
            });
            return new JBCefJSQuery.Response("");
        });
        component.setBorder(BorderFactory.createLineBorder(BORDER));
        component.setBackground(PANEL);
        component.add(browser.getComponent(), BorderLayout.CENTER);
        component.setPreferredSize(new Dimension(950, 430));
        loadPage();
        selectionTimer.start();
    }

    public static void show(@NotNull AnActionEvent event, @NotNull DataGrid grid) {
        LogView<?> logView = findLogView(grid, event);
        if (logView == null) {
            Messages.showWarningDialog(event.getProject(), "Geo Viewer Plus could not find the current result panel.", "Geo Viewer Plus");
            return;
        }
        logView.show(true, false);
        RunnerLayoutUi ui = logView.getUi();
        com.intellij.ui.content.Content existing = ui.findContent(CONTENT_ID);
        if (existing != null) {
            CustomGeoViewerContent old = existing.getUserData(CONTENT_KEY);
            if (old != null && old.grid == grid) {
                ui.selectAndFocus(existing, false, false);
                old.reload();
                return;
            }
            ui.removeContent(existing, true);
        }
        CustomGeoViewerContent content = new CustomGeoViewerContent(grid);
        com.intellij.ui.content.Content view = ui.createContent(CONTENT_ID, content.component, "Geo Viewer Plus", IconLoader.getIcon("/icons/geoViewer.svg", CustomGeoViewerContent.class), browserFocus(content));
        view.putUserData(CONTENT_KEY, content);
        view.setCloseable(true);
        view.setDisposer(content);
        view.setDescription("Custom OSM / XYZ map for the current DataGrid result");
        ui.addContent(view, 0, PlaceInGrid.right, false);
        ui.selectAndFocus(view, false, false);
    }

    private static JComponent browserFocus(CustomGeoViewerContent content) {
        return content.browser.getComponent();
    }

    private static LogView<?> findLogView(DataGrid grid, AnActionEvent event) {
        DataGridSessionClient client = DataGridUtil.getDataGridClient(grid);
        if (client != null && client.getView() != null) return client.getView();
        JdbcConsole console = JdbcConsole.findConsole(event);
        if (console != null) return console.getView();
        return null;
    }

    private void addMapSource(ActionEvent event) {
        MapSource source = showMapSourceDialog(null);
        if (source == null) return;
        if (sources.stream().anyMatch(existing -> existing.name().equalsIgnoreCase(source.name()))) {
            Messages.showWarningDialog(grid.getMainResultViewComponent(), "A map source with this name already exists.", "Geo Viewer Plus");
            return;
        }
        sources.add(source);
        selectedSourceName = source.name();
        persistCustomSources();
        browser.runJavaScript("window.geoPlus && window.geoPlus.addSource(" + sourceJson(source) + ", true);");
    }

    private void editMapSource(String sourceName) {
        int index = findSourceIndex(sourceName);
        if (index < 0 || !sources.get(index).custom()) return;
        MapSource updated = showMapSourceDialog(sources.get(index));
        if (updated == null) return;
        if (sources.stream().anyMatch(existing -> existing != sources.get(index) && existing.name().equalsIgnoreCase(updated.name()))) {
            Messages.showWarningDialog(grid.getMainResultViewComponent(), "A map source with this name already exists.", "Geo Viewer Plus");
            return;
        }
        MapSource previous = sources.set(index, updated);
        boolean wasSelected = previous.name().equals(selectedSourceName);
        boolean wasDefault = previous.name().equals(defaultSourceName);
        if (wasSelected) selectedSourceName = updated.name();
        if (wasDefault) defaultSourceName = updated.name();
        persistCustomSources();
        if (wasDefault) PropertiesComponent.getInstance().setValue(DEFAULT_SOURCE_KEY, defaultSourceName);
        browser.runJavaScript("window.geoPlus && window.geoPlus.removeSource(" + quote(previous.name()) + ");");
        browser.runJavaScript("window.geoPlus && window.geoPlus.addSource(" + sourceJson(updated) + ", " + wasSelected + ");");
        if (wasDefault) browser.runJavaScript("window.geoPlus && window.geoPlus.setDefaultSource(" + quote(defaultSourceName) + ");");
    }

    private void removeMapSource(String sourceName) {
        int index = findSourceIndex(sourceName);
        if (index < 0 || !sources.get(index).custom()) return;
        MapSource source = sources.get(index);
        int result = JOptionPane.showConfirmDialog(
                grid.getMainResultViewComponent(),
                "Remove custom map source ‘" + source.name() + "’?\nThis does not affect built-in sources.",
                "Remove Custom Map Source",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) return;

        boolean wasSelected = source.name().equals(selectedSourceName);
        boolean wasDefault = source.name().equals(defaultSourceName);
        sources.remove(index);
        persistCustomSources();
        browser.runJavaScript("window.geoPlus && window.geoPlus.removeSource(" + quote(source.name()) + ");");
        if (wasSelected) {
            String fallback = sources.isEmpty() ? "" : sources.get(0).name();
            selectedSourceName = fallback;
            if (!fallback.isBlank()) {
                browser.runJavaScript("window.geoPlus && window.geoPlus.switchSource(" + quote(fallback) + ");");
            }
        }
        if (wasDefault) {
            String fallback = sources.isEmpty() ? "" : sources.get(0).name();
            defaultSourceName = fallback;
            PropertiesComponent.getInstance().setValue(DEFAULT_SOURCE_KEY, fallback);
            if (!fallback.isBlank()) browser.runJavaScript("window.geoPlus && window.geoPlus.setDefaultSource(" + quote(fallback) + ");");
        }
    }

    private int findSourceIndex(String sourceName) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).name().equals(sourceName)) return i;
        }
        return -1;
    }

    private MapSource showMapSourceDialog(MapSource initial) {
        JTextField name = new JTextField(initial == null ? "My map" : initial.name());
        JTextField url = new JTextField(initial == null ? "https://{s}.example.com/{z}/{x}/{y}.png" : initial.template());
        JTextField attribution = new JTextField(initial == null ? "Map data contributors" : initial.attribution());
        JTextField subdomains = new JTextField(initial == null ? "abc" : initial.subdomains());
        JCheckBox tms = new JCheckBox("TMS Y axis (invert tile row)", initial != null && initial.tms());
        JComboBox<MapSourceType> type = new JComboBox<>(MapSourceType.values());
        JTextField vectorLayer = new JTextField(initial == null ? "sliced" : initial.vectorLayer());
        if (initial != null) type.setSelectedItem(initial.type());
        type.addActionListener(ignored -> {
            boolean mvt = type.getSelectedItem() == MapSourceType.MVT;
            vectorLayer.setEnabled(mvt);
            tms.setEnabled(!mvt);
            if (mvt) tms.setSelected(false);
        });
        JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
        fields.add(new JLabel("Name")); fields.add(name);
        fields.add(new JLabel("Source type")); fields.add(type);
        fields.add(new JLabel("Tile URL template (XYZ/TMS)")); fields.add(url);
        fields.add(new JLabel("Attribution")); fields.add(attribution);
        fields.add(new JLabel("Subdomains for {s} (e.g. abc or 1234)")); fields.add(subdomains);
        fields.add(new JLabel("MVT layer name (usually sliced; comma-separated for multiple layers)")); fields.add(vectorLayer);
        fields.add(tms);
        type.setSelectedItem(initial == null ? MapSourceType.RASTER_XYZ : initial.type());
        boolean initialMvt = type.getSelectedItem() == MapSourceType.MVT;
        vectorLayer.setEnabled(initialMvt);
        tms.setEnabled(!initialMvt);
        if (initialMvt) tms.setSelected(false);
        String title = initial == null ? "Add Custom Map Source" : "Edit Custom Map Source";
        int result = JOptionPane.showConfirmDialog(grid.getMainResultViewComponent(), fields, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        String sourceName = name.getText().trim();
        String template = url.getText().trim();
        if (sourceName.isBlank() || template.isBlank()) {
            Messages.showWarningDialog(grid.getMainResultViewComponent(), "Name and tile URL template are required.", "Geo Viewer Plus");
            return null;
        }
        MapSourceType sourceType = (MapSourceType) type.getSelectedItem();
        return new MapSource(sourceName, template, attribution.getText().trim(), tms.isSelected(), subdomains.getText().trim(), true, sourceType, vectorLayer.getText().trim());
    }

    private void loadPage() {
        String html = readResource(HTML_RESOURCE);
        String query = selectQuery.inject("String(row)");
        String ready = readyQuery.inject("ready");
        String source = sourceQuery.inject("String(name)");
        String addSource = addSourceQuery.inject("open");
        String editSource = editSourceQuery.inject("String(name)");
        String removeSource = removeSourceQuery.inject("String(name)");
        String defaultSource = defaultSourceQuery.inject("String(name)");
        String bootstrap = "<script>window.dg=window.dg||{};window.dg.selectInTable=function(row){" + query + "};window.dg.changeSource=function(name){" + source + "};window.dg.addMapSource=function(){" + addSource + "};window.dg.editMapSource=function(name){" + editSource + "};window.dg.removeMapSource=function(name){" + removeSource + "};window.dg.setDefaultSource=function(name){" + defaultSource + "};</script>";
        html = html.replace("__GEO_VIEWER_READY__", ready);
        browser.loadHTML(html.replace("</body>", bootstrap + "</body>"), "https://geo-viewer-plus.local/");
    }

    private void bootstrapPage() {
        if (!pageReady) return;
        for (MapSource source : sources) {
            browser.runJavaScript("window.geoPlus.addSource(" + sourceJson(source) + ");");
        }
        if (!defaultSourceName.isBlank()) browser.runJavaScript("window.geoPlus.setDefaultSource(" + quote(defaultSourceName) + ");");
        if (!selectedSourceName.isBlank()) browser.runJavaScript("window.geoPlus.switchSource(" + quote(selectedSourceName) + ");");
        reload();
    }

    private void reload() {
        lastSelectionKey = "";
        browser.runJavaScript("window.geoPlus && window.geoPlus.loadFeatures(" + payload + ");");
    }

    private void syncGridSelection() {
        if (!pageReady || !grid.isReady() || browser.isDisposed()) return;
        int[] rows = grid.getSelectionModel().getSelectedRows().asArray();
        String key = Arrays.toString(rows);
        if (key.equals(lastSelectionKey)) return;
        lastSelectionKey = key;
        if (rows.length == 0) return;
        String rowArray = Arrays.stream(rows).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        browser.runJavaScript("window.geoPlus && window.geoPlus.focusRows([" + rowArray + "], false);");
    }

    private static List<MapSource> defaultSources() {
        return List.of(
                new MapSource("高德道路（国内）", "https://wprd0{s}.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&size=1&scl=1&style=8&ltype=11", "© 高德地图 · GCJ-02", false, "1234"),
                new MapSource("高德道路（简洁）", "https://wprd0{s}.is.autonavi.com/appmaptile?x={x}&y={y}&z={z}&size=1&scl=1&style=8", "© 高德地图 · GCJ-02", false, "1234"),
                new MapSource("高德影像（国内）", "https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}", "© 高德地图 · 影像 · GCJ-02", false, "1234"),
                new MapSource("高德影像（含路网）", "https://webst0{s}.is.autonavi.com/appmaptile?style=6&ltype=11&x={x}&y={y}&z={z}", "© 高德地图 · 影像/路网 · GCJ-02", false, "1234"),
                new MapSource("OSM Standard", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors", false),
                new MapSource("OSM Humanitarian", "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", "© OpenStreetMap contributors · HOT", false, "abc"),
                new MapSource("OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors · SRTM", false),
                new MapSource("Esri World Imagery", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "© Esri", false),
                new MapSource("腾讯道路（实验）", "https://rt{s}.map.gtimg.com/tile?z={z}&x={x}&y={y}&styleid=1&version=376", "© 腾讯地图 · 坐标系请按数据源校正", false, "0123")
        );
    }

    private static List<MapSource> loadCustomSources() {
        List<MapSource> result = new ArrayList<>();
        String stored = PropertiesComponent.getInstance().getValue(CUSTOM_SOURCES_KEY, "");
        if (stored.isBlank()) return result;
        for (String item : stored.split("\\n")) {
            String[] parts = item.split("\\|", -1);
            if (parts.length < 4 || parts.length > 7) continue;
            try {
                MapSourceType type = parts.length >= 6 ? MapSourceType.fromId(decode(parts[5])) : MapSourceType.RASTER_XYZ;
                String vectorLayer = parts.length >= 7 ? decode(parts[6]) : "";
                result.add(new MapSource(decode(parts[0]), decode(parts[1]), decode(parts[2]), Boolean.parseBoolean(parts[3]), parts.length >= 5 ? decode(parts[4]) : "", true, type, vectorLayer));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private void persistCustomSources() {
        StringBuilder value = new StringBuilder();
        for (MapSource source : sources) {
            if (!source.custom()) continue;
            if (value.length() > 0) value.append('\n');
            value.append(encode(source.name())).append('|')
                    .append(encode(source.template())).append('|')
                    .append(encode(source.attribution())).append('|')
                    .append(source.tms()).append('|')
                    .append(encode(source.subdomains())).append('|')
                    .append(encode(source.type().id())).append('|')
                    .append(encode(source.vectorLayer()));
        }
        PropertiesComponent.getInstance().setValue(CUSTOM_SOURCES_KEY, value.toString());
    }

    private static String encode(String value) { return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }

    private static String sourceJson(MapSource source) {
        return "{\"name\":\"" + json(source.name()) + "\",\"template\":\"" + json(source.template()) + "\",\"attribution\":\"" + json(source.attribution()) + "\",\"tms\":" + source.tms() + ",\"subdomains\":\"" + json(source.subdomains()) + "\",\"custom\":" + source.custom() + ",\"type\":\"" + json(source.type().id()) + "\",\"vectorLayer\":\"" + json(source.vectorLayer()) + "\"}";
    }

    private static String payload(GeoDataExtractor.Snapshot snapshot) {
        StringBuilder out = new StringBuilder("{\"geometryColumn\":").append(quote(snapshot.geometryColumn())).append(",\"features\":[");
        for (int i = 0; i < snapshot.features().size(); i++) {
            if (i > 0) out.append(',');
            GeoDataExtractor.Feature f = snapshot.features().get(i);
            out.append("{\"row\":").append(f.rowIndex()).append(",\"wkt\":").append(quote(f.wkt())).append(",\"attributes\":[");
            for (int j = 0; j < f.attributes().size(); j++) {
                if (j > 0) out.append(',');
                GeoDataExtractor.Attribute a = f.attributes().get(j);
                out.append('[').append(quote(a.name())).append(',').append(quote(a.value())).append(']');
            }
            out.append("]}");
        }
        return out.append("]}").toString();
    }

    private static String readResource(String path) {
        try (InputStream stream = CustomGeoViewerContent.class.getResourceAsStream(path)) {
            if (stream == null) return "<html><body>Geo Viewer Plus resource missing</body></html>";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<html><body>Geo Viewer Plus could not load its map page</body></html>";
        }
    }

    private static String quote(String value) { return "\"" + json(value) + "\""; }
    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    @Override public void dispose() {
        selectionTimer.stop();
        browser.getJBCefClient().removeLoadHandler(loadHandler, browser.getCefBrowser());
        selectQuery.dispose();
        readyQuery.dispose();
        sourceQuery.dispose();
        addSourceQuery.dispose();
        editSourceQuery.dispose();
        removeSourceQuery.dispose();
        defaultSourceQuery.dispose();
        browser.dispose();
    }
}
