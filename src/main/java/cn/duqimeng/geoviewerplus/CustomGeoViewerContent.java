package cn.duqimeng.geoviewerplus;

import com.intellij.database.console.JdbcConsole;
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Messages;
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
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
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
    private final JBCefJSQuery reloadQuery;
    private final JPanel component;
    private final List<MapSource> sources = new ArrayList<>();
    private String payload;
    private final Timer selectionTimer;
    private boolean pageReady;
    private String lastSelectionKey = "";
    private String lastVisibleRowsKey = "";
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
        this.reloadQuery = JBCefJSQuery.create(browser);
        this.component = new JBPanel<>(new BorderLayout());
        this.selectionTimer = new Timer(180, event -> syncGridState());
        this.lastVisibleRowsKey = visibleRowsKey();

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
                        ModelIndex<GridRow> modelRow = ModelIndex.forRow(grid, row);
                        // The map must follow the currently displayed page. Do not let a stale
                        // feature select a row that makes DataGrip navigate to another page.
                        if (!isVisibleRow(row)) return;
                        grid.getSelectionModel().clearSelection();
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
        reloadQuery.addHandler(ignored -> {
            SwingUtilities.invokeLater(this::reload);
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
        JTextField name = new JTextField(initial == null ? "My map" : initial.name(), 42);
        JTextField url = new JTextField(initial == null ? "https://{s}.example.com/{z}/{x}/{y}.png" : initial.template(), 42);
        JTextField attribution = new JTextField(initial == null ? "Map data contributors" : initial.attribution(), 42);
        JTextField subdomains = new JTextField(initial == null ? "abc" : initial.subdomains(), 42);
        JCheckBox tms = new JCheckBox("TMS Y axis (invert tile row)", initial != null && initial.tms());
        JComboBox<MapSourceType> type = new JComboBox<>(MapSourceType.values());
        JTextField vectorLayer = new JTextField(initial == null ? "sliced" : initial.vectorLayer(), 42);
        if (initial != null) type.setSelectedItem(initial.type());
        JBLabel vectorLayerLabel = new JBLabel("MVT layer names");
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(JBUI.Borders.empty(8, 12, 4, 12));
        fields.setPreferredSize(new Dimension(620, 390));
        int row = 0;
        row = addFormRow(fields, row, "Name", name, "Shown in the map source menu.");
        row = addFormRow(fields, row, "Source type", type, "Raster sources use XYZ/TMS tiles; MVT sources use protobuf vector tiles.");
        row = addFormRow(fields, row, "Tile URL template", url, "Use {z}, {x}, {y}; add {s} when the server uses subdomains.");
        row = addFormRow(fields, row, "Attribution", attribution, "Keep the provider credit visible on the map.");
        row = addFormRow(fields, row, "Subdomains", subdomains, "For {s}, for example abc or 0123. Leave blank when unused.");
        row = addFormRow(fields, row, vectorLayerLabel, vectorLayer, "For MVT, enter names such as roads, water, landuse; comma-separate multiple names.");
        GridBagConstraints checkboxConstraints = new GridBagConstraints();
        checkboxConstraints.gridx = 1; checkboxConstraints.gridy = row; checkboxConstraints.gridwidth = 2;
        checkboxConstraints.anchor = GridBagConstraints.WEST; checkboxConstraints.insets = new Insets(8, 0, 4, 0);
        fields.add(tms, checkboxConstraints);
        type.addActionListener(ignored -> {
            boolean mvt = type.getSelectedItem() == MapSourceType.MVT;
            vectorLayer.setEnabled(mvt);
            vectorLayerLabel.setEnabled(mvt);
            tms.setEnabled(!mvt);
            if (mvt) tms.setSelected(false);
        });
        boolean initialMvt = type.getSelectedItem() == MapSourceType.MVT;
        vectorLayer.setEnabled(initialMvt);
        vectorLayerLabel.setEnabled(initialMvt);
        tms.setEnabled(!initialMvt);
        if (initialMvt) tms.setSelected(false);
        String title = initial == null ? "Add Custom Map Source" : "Edit Custom Map Source";
        Window owner = SwingUtilities.getWindowAncestor(grid.getMainResultViewComponent());
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(JBUI.Borders.empty(8));
        root.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton(initial == null ? "Add source" : "Save changes");
        actions.add(cancel);
        actions.add(save);
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        MapSource[] result = new MapSource[1];
        cancel.addActionListener(ignored -> dialog.dispose());
        save.addActionListener(ignored -> {
            String sourceName = name.getText().trim();
            String template = url.getText().trim();
            if (sourceName.isBlank() || template.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "Name and tile URL template are required.", "Geo Viewer Plus", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String validation = validateTileTemplate(template);
            if (validation != null) {
                JOptionPane.showMessageDialog(dialog, validation, "Geo Viewer Plus", JOptionPane.WARNING_MESSAGE);
                return;
            }
            MapSourceType sourceType = (MapSourceType) type.getSelectedItem();
            result[0] = new MapSource(sourceName, template, attribution.getText().trim(), tms.isSelected(), subdomains.getText().trim(), true, sourceType, vectorLayer.getText().trim());
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(save);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(680, 500));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    private static int addFormRow(JPanel panel, int row, String label, JComponent field, String help) {
        return addFormRow(panel, row, new JBLabel(label), field, help);
    }

    private static int addFormRow(JPanel panel, int row, JComponent label, JComponent field, String help) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0; labelConstraints.gridy = row; labelConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        labelConstraints.insets = new Insets(6, 0, 2, 14);
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1; fieldConstraints.gridy = row; fieldConstraints.gridwidth = 2;
        fieldConstraints.weightx = 1; fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(3, 0, 1, 0);
        panel.add(field, fieldConstraints);

        if (help == null || help.isBlank()) return row + 1;
        JBLabel hint = new JBLabel(help);
        hint.setForeground(JBColor.GRAY);
        GridBagConstraints helpConstraints = new GridBagConstraints();
        helpConstraints.gridx = 1; helpConstraints.gridy = row + 1; helpConstraints.gridwidth = 2;
        helpConstraints.weightx = 1; helpConstraints.fill = GridBagConstraints.HORIZONTAL;
        helpConstraints.insets = new Insets(0, 0, 3, 0);
        panel.add(hint, helpConstraints);
        return row + 2;
    }

    private void loadPage() {
        String html = readResource(HTML_RESOURCE);
        html = html.replace("__LEAFLET_CSS__", readResource("/leaflet.css"))
                .replace("__LEAFLET_JS__", readResource("/leaflet.js"))
                .replace("__LEAFLET_VECTORGRID_JS__", readResource("/leaflet-vectorgrid.js"));
        String query = selectQuery.inject("String(row)");
        String ready = readyQuery.inject("ready");
        String source = sourceQuery.inject("String(name)");
        String addSource = addSourceQuery.inject("open");
        String editSource = editSourceQuery.inject("String(name)");
        String removeSource = removeSourceQuery.inject("String(name)");
        String defaultSource = defaultSourceQuery.inject("String(name)");
        String reload = reloadQuery.inject("refresh");
        String bootstrap = "<script>window.dg=window.dg||{};window.dg.selectInTable=function(row){" + query + "};window.dg.changeSource=function(name){" + source + "};window.dg.addMapSource=function(){" + addSource + "};window.dg.editMapSource=function(name){" + editSource + "};window.dg.removeMapSource=function(name){" + removeSource + "};window.dg.setDefaultSource=function(name){" + defaultSource + "};window.dg.refreshMap=function(){" + reload + "};</script>";
        html = html.replace("__GEO_VIEWER_READY__", ready);
        browser.loadHTML(html.replace("</body>", bootstrap + "</body>"), "https://geo-viewer-plus.local/");
    }

    private void bootstrapPage() {
        if (!pageReady) return;
        String sourceJson = sources.stream().map(CustomGeoViewerContent::sourceJson).collect(java.util.stream.Collectors.joining(","));
        browser.runJavaScript("window.geoPlus && window.geoPlus.addSources([" + sourceJson + "]);" );
        if (!defaultSourceName.isBlank()) browser.runJavaScript("window.geoPlus.setDefaultSource(" + quote(defaultSourceName) + ");");
        if (!selectedSourceName.isBlank()) browser.runJavaScript("window.geoPlus.switchSource(" + quote(selectedSourceName) + ");");
        reload();
    }

    private void reload() {
        lastSelectionKey = "";
        payload = payload(GeoDataExtractor.extract(grid, 500));
        lastVisibleRowsKey = visibleRowsKey();
        browser.runJavaScript("window.geoPlus && window.geoPlus.loadFeatures(" + payload + ");");
    }

    private void syncGridState() {
        if (!pageReady || !grid.isReady() || browser.isDisposed()) return;
        String visibleRowsKey = visibleRowsKey();
        if (!visibleRowsKey.equals(lastVisibleRowsKey)) {
            lastVisibleRowsKey = visibleRowsKey;
            reload();
        }
        int[] rows = grid.getSelectionModel().getSelectedRows().asArray();
        String key = Arrays.toString(rows);
        if (key.equals(lastSelectionKey)) return;
        lastSelectionKey = key;
        if (rows.length == 0) return;
        String rowArray = Arrays.stream(rows).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        browser.runJavaScript("window.geoPlus && window.geoPlus.focusRows([" + rowArray + "], false);");
    }

    private boolean isVisibleRow(int row) {
        return grid.getVisibleRows().asList().stream().anyMatch(visible -> visible.asInteger() == row);
    }

    private String visibleRowsKey() {
        if (!grid.isReady()) return "";
        StringBuilder key = new StringBuilder();
        for (ModelIndex<GridRow> row : grid.getVisibleRows().asList()) {
            if (key.length() > 0) key.append(',');
            key.append(row.asInteger());
        }
        return key.toString();
    }

    private static List<MapSource> defaultSources() {
        return List.of(
                new MapSource("高德道路", "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}", "© 高德地图 · GCJ-02 · WGS84 data may be offset", false, "1234"),
                new MapSource("高德影像", "https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}", "© 高德地图 · GCJ-02 · WGS84 data may be offset", false, "1234"),
                new MapSource("腾讯道路", "https://rt{s}.map.gtimg.com/tile?z={z}&x={x}&y={y}&styleid=2&version=376", "© 腾讯地图 · GCJ-02 · WGS84 data may be offset", true, "0123"),
                new MapSource("OSM Standard", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors", false),
                new MapSource("OSM Humanitarian", "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png", "© OpenStreetMap contributors · HOT", false, "abc"),
                new MapSource("OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors · SRTM", false),
                new MapSource("Esri World Imagery（需可访问 ArcGIS Online）", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "© Esri", false)
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
                String template = decode(parts[1]);
                if (validateTileTemplate(template) == null) {
                    result.add(new MapSource(decode(parts[0]), template, decode(parts[2]), Boolean.parseBoolean(parts[3]), parts.length >= 5 ? decode(parts[4]) : "", true, type, vectorLayer));
                }
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
        StringBuilder out = new StringBuilder("{\"geometryColumn\":").append(quote(snapshot.geometryColumn()))
                .append(",\"visibleRows\":").append(snapshot.visibleRows())
                .append(",\"truncatedRows\":").append(snapshot.truncatedRows())
                .append(",\"skippedRows\":").append(snapshot.skippedRows())
                .append(",\"features\":[");
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
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private static String validateTileTemplate(String template) {
        if (!template.contains("{z}") || !template.contains("{x}") || !template.contains("{y}")) {
            return "Tile URL template must contain {z}, {x}, and {y}.";
        }
        try {
            String probe = template.replace("{s}", "a").replace("{z}", "0").replace("{x}", "0").replace("{y}", "0");
            String scheme = URI.create(probe).getScheme();
            if (!"https".equalsIgnoreCase(scheme)) return "Only HTTPS tile sources are allowed to protect result-set location privacy.";
        } catch (IllegalArgumentException ex) {
            return "Tile URL template is not a valid HTTPS URL.";
        }
        return null;
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
        reloadQuery.dispose();
        browser.dispose();
    }
}
