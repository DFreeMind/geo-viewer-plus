package cn.duqimeng.geoviewerplus;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Locale;

/** The database-adjacent Geo Viewer workspace. UI state is intentionally local so the panel is safe to open in any project. */
public final class GeoViewerPanel extends JBPanel<GeoViewerPanel> {
    private static final Color BG = new Color(15, 23, 42);
    private static final Color SURFACE = new Color(30, 41, 59);
    private static final Color SURFACE_2 = new Color(51, 65, 85);
    private static final Color BORDER = new Color(71, 85, 105);
    private static final Color TEXT = new Color(248, 250, 252);
    private static final Color MUTED = new Color(148, 163, 184);
    private static final Color GREEN = new Color(34, 197, 94);
    private static final Color CYAN = new Color(45, 212, 191);

    private final MapCanvas mapCanvas = new MapCanvas();
    private final JLabelValue selectedValue = new JLabelValue();
    private final JLabelValue zoomValue = new JLabelValue();
    private final JLabelValue zValue = new JLabelValue();
    private final JComboBox<String> basemap = new JComboBox<>(new String[]{"OSM Standard", "OSM Terrain", "OpenTopoMap"});
    private JTable dataTable;

    public GeoViewerPanel(Project project) {
        super(new BorderLayout());
        setBackground(BG);
        setBorder(JBUI.Borders.empty(8));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkspace(), BorderLayout.CENTER);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.emptyBottom(8));

        JPanel title = new JPanel(new BorderLayout(10, 0));
        title.setOpaque(false);
        JBLabel mark = new JBLabel("GEO");
        mark.setForeground(BG);
        mark.setBackground(GREEN);
        mark.setOpaque(true);
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        mark.setPreferredSize(new Dimension(38, 28));
        title.add(mark, BorderLayout.WEST);
        JPanel copy = new JPanel(new GridBagLayout());
        copy.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        JBLabel heading = label("Geo Viewer Plus", 15, TEXT);
        copy.add(heading, c);
        c.gridy = 1;
        copy.add(label("public.roads  ·  50 visible features", 11, MUTED), c);
        title.add(copy, BorderLayout.CENTER);
        header.add(title, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints cc = new GridBagConstraints();
        cc.insets = new Insets(0, 0, 0, 6);
        cc.gridy = 0;
        JButton refresh = smallButton("↻  Refresh");
        refresh.addActionListener(e -> mapCanvas.repaint());
        controls.add(refresh, cc);
        JButton fit = smallButton("⌖  Fit data");
        fit.addActionListener(e -> { mapCanvas.zoom = 1.0; mapCanvas.repaint(); zoomValue.set("100%"); });
        controls.add(fit, cc);
        cc.gridx = 2;
        JButton settings = smallButton("Settings");
        settings.addActionListener(e -> basemap.requestFocusInWindow());
        controls.add(settings, cc);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JComponent buildWorkspace() {
        JPanel body = new JPanel(new BorderLayout(8, 0));
        body.setOpaque(false);
        body.add(buildGridPanel(), BorderLayout.NORTH);

        JPanel viewer = new JPanel(new BorderLayout(8, 0));
        viewer.setOpaque(false);
        viewer.add(buildMapPanel(), BorderLayout.CENTER);
        viewer.add(buildInspector(), BorderLayout.EAST);
        body.add(viewer, BorderLayout.CENTER);
        return body;
    }

    private JComponent buildGridPanel() {
        JPanel panel = surfacePanel();
        panel.setBorder(BorderFactory.createCompoundBorder(panel.getBorder(), JBUI.Borders.emptyBottom(8)));
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(JBUI.Borders.empty(0, 10, 7, 10));
        bar.add(label("RESULT SET", 10, MUTED), BorderLayout.WEST);
        JPanel meta = new JPanel(new BorderLayout(9, 0));
        meta.setOpaque(false);
        meta.add(label("geometry", 11, MUTED), BorderLayout.WEST);
        meta.add(label("EPSG:4326", 11, CYAN), BorderLayout.CENTER);
        bar.add(meta, BorderLayout.EAST);
        panel.add(bar, BorderLayout.NORTH);

        dataTable = new JTable(new RoadsTableModel());
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setRowHeight(27);
        dataTable.setShowGrid(false);
        dataTable.setIntercellSpacing(new Dimension(0, 0));
        dataTable.setBackground(SURFACE);
        dataTable.setForeground(TEXT);
        dataTable.setSelectionBackground(new Color(30, 101, 79));
        dataTable.setSelectionForeground(Color.WHITE);
        dataTable.getTableHeader().setPreferredSize(new Dimension(0, 30));
        dataTable.getTableHeader().setBackground(SURFACE_2);
        dataTable.getTableHeader().setForeground(MUTED);
        dataTable.getTableHeader().setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        dataTable.getColumnModel().getColumn(1).setPreferredWidth(118);
        dataTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        dataTable.getColumnModel().getColumn(3).setPreferredWidth(270);
        dataTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        dataTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        dataTable.getSelectionModel().addListSelectionListener(this::rowSelected);
        dataTable.setRowSelectionInterval(0, 0);
        panel.add(new JBScrollPane(dataTable), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(900, 212));
        return panel;
    }

    private void rowSelected(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting() && dataTable.getSelectedRow() >= 0) {
            int row = dataTable.getSelectedRow();
            selectedValue.set("feature " + String.format(Locale.US, "%02d", row + 1));
            mapCanvas.selected = row;
            mapCanvas.repaint();
        }
    }

    private JComponent buildMapPanel() {
        JPanel panel = surfacePanel();
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(JBUI.Borders.empty(7, 9));
        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);
        left.add(label("MAP", 10, MUTED), BorderLayout.WEST);
        basemap.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        basemap.setBackground(SURFACE_2);
        basemap.setForeground(TEXT);
        basemap.setPreferredSize(new Dimension(145, 27));
        basemap.addActionListener(e -> { mapCanvas.style = basemap.getSelectedIndex(); mapCanvas.repaint(); });
        left.add(basemap, BorderLayout.CENTER);
        toolbar.add(left, BorderLayout.WEST);
        JPanel stats = new JPanel(new BorderLayout(10, 0));
        stats.setOpaque(false);
        stats.add(label("50 features", 11, MUTED), BorderLayout.WEST);
        stats.add(label("●  connected", 11, GREEN), BorderLayout.CENTER);
        toolbar.add(stats, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        JPanel mapWithControls = new JPanel(new BorderLayout());
        mapWithControls.setOpaque(false);
        JPanel zoom = new JPanel(new GridBagLayout());
        zoom.setOpaque(false);
        zoom.setBorder(JBUI.Borders.empty(10));
        JButton plus = mapButton("+");
        plus.addActionListener(e -> { mapCanvas.zoom = Math.min(1.4, mapCanvas.zoom + .1); mapCanvas.repaint(); zoomValue.set(Math.round(mapCanvas.zoom * 100) + "%"); });
        JButton minus = mapButton("−");
        minus.addActionListener(e -> { mapCanvas.zoom = Math.max(.7, mapCanvas.zoom - .1); mapCanvas.repaint(); zoomValue.set(Math.round(mapCanvas.zoom * 100) + "%"); });
        GridBagConstraints zc = new GridBagConstraints();
        zc.gridx = 0; zc.gridy = 0; zoom.add(plus, zc); zc.gridy = 1; zoom.add(minus, zc);
        mapWithControls.add(zoom, BorderLayout.WEST);
        mapWithControls.add(mapCanvas, BorderLayout.CENTER);
        panel.add(mapWithControls, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(JBUI.Borders.empty(6, 10));
        footer.add(label("◉  43.7942° N, 87.6270° E", 10, MUTED), BorderLayout.WEST);
        MapSources source = MapSources.fromIndex(basemap.getSelectedIndex());
        footer.add(label(source.attribution() + "  ·  " + source.tileTemplate(), 10, MUTED), BorderLayout.EAST);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildInspector() {
        JPanel panel = surfacePanel();
        panel.setPreferredSize(new Dimension(230, 0));
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        content.add(label("SELECTION", 10, MUTED), c);
        c.gridy++;
        content.add(label("Road feature", 14, TEXT), c);
        c.gridy++;
        content.add(label("click a row to locate", 11, MUTED), c);
        c.gridy++;
        content.add(divider(), c);
        c.gridy++;
        content.add(fieldLine("id", "65001149"), c);
        c.gridy++;
        content.add(fieldLine("name", "连霍高速"), c);
        c.gridy++;
        content.add(fieldLine("type", "LineString"), c);
        c.gridy++;
        content.add(fieldLine("length", "3,587.47 m"), c);
        c.gridy++;
        content.add(fieldLine("status", "active"), c);
        c.gridy++;
        content.add(divider(), c);
        c.gridy++;
        content.add(label("VIEW CONTROLS", 10, MUTED), c);
        c.gridy++;
        JPanel zoomRow = controlLine("Zoom", zoomValue, "100%");
        content.add(zoomRow, c);
        c.gridy++;
        JPanel zRow = controlLine("Z exaggeration", zValue, "1.0×");
        content.add(zRow, c);
        JSlider zSlider = new JSlider(0, 400, 100);
        zSlider.setOpaque(false);
        zSlider.setForeground(GREEN);
        zSlider.addChangeListener(e -> { double v = zSlider.getValue() / 100.0; zValue.set(String.format(Locale.US, "%.1f×", v)); mapCanvas.zScale = v; mapCanvas.repaint(); });
        c.gridy++;
        content.add(zSlider, c);
        c.gridy++;
        JButton copy = smallButton("Copy coordinates");
        copy.addActionListener(e -> copy.setText("Copied coordinates"));
        content.add(copy, c);
        c.gridy++; c.weighty = 1; content.add(new JPanel(), c);
        panel.add(content, BorderLayout.CENTER);
        selectedValue.set("feature 01");
        zoomValue.set("100%");
        zValue.set("1.0×");
        return panel;
    }

    private JPanel controlLine(String name, JLabelValue value, String initial) {
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.add(label(name, 11, TEXT), BorderLayout.WEST);
        value.set(initial);
        line.add(value, BorderLayout.EAST);
        return line;
    }

    private JPanel fieldLine(String key, String value) {
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(label(key, 11, MUTED), BorderLayout.WEST);
        line.add(label(value, 11, TEXT), BorderLayout.EAST);
        return line;
    }

    private JPanel surfacePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
        return panel;
    }

    private JPanel divider() {
        JPanel line = new JPanel();
        line.setPreferredSize(new Dimension(1, 1));
        line.setBackground(BORDER);
        return line;
    }

    private JBLabel label(String text, int size, Color color) {
        JBLabel label = new JBLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private JButton smallButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        button.setForeground(TEXT);
        button.setBackground(SURFACE_2);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), JBUI.Borders.empty(5, 9)));
        button.setFocusPainted(false);
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private JButton mapButton(String text) {
        JButton button = smallButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        button.setPreferredSize(new Dimension(30, 29));
        button.setBorder(BorderFactory.createLineBorder(BORDER));
        return button;
    }

    private static final class JLabelValue extends JBLabel {
        JLabelValue() { super(); setForeground(CYAN); setFont(new Font(Font.MONOSPACED, Font.BOLD, 11)); }
        void set(String value) { setText(value); }
    }

    private final class MapCanvas extends JComponent {
        private double zoom = 1.0;
        private double zScale = 1.0;
        private int style = 0;
        private int selected = 0;

        MapCanvas() {
            setPreferredSize(new Dimension(700, 380));
            setMinimumSize(new Dimension(350, 240));
            setToolTipText("Drag to pan · Scroll to zoom · Click a feature to inspect");
            setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        }

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color base = style == 0 ? new Color(24, 39, 55) : style == 1 ? new Color(31, 54, 52) : new Color(39, 48, 60);
            g.setPaint(new GradientPaint(0, 0, base, w, h, new Color(15, 23, 42)));
            g.fillRect(0, 0, w, h);
            drawGrid(g, w, h);
            drawTerrain(g, w, h);
            drawWater(g, w, h);
            drawRoads(g, w, h);
            drawFeatures(g, w, h);
            drawLegend(g);
            g.dispose();
        }

        private void drawGrid(java.awt.Graphics2D g, int w, int h) {
            g.setColor(new Color(148, 163, 184, 18));
            g.setStroke(new BasicStroke(1));
            for (int x = 25; x < w; x += 58) g.drawLine(x, 0, x, h);
            for (int y = 25; y < h; y += 58) g.drawLine(0, y, w, y);
        }

        private void drawTerrain(java.awt.Graphics2D g, int w, int h) {
            if (style == 0) return;
            g.setColor(new Color(163, 177, 149, 28));
            for (int i = 0; i < 5; i++) {
                int x = (int)(w * (.1 + i * .18));
                int y = (int)(h * (.08 + (i % 2) * .23));
                g.drawOval(x, y, 160 + i * 24, 76 + i * 12);
            }
            g.setColor(new Color(203, 213, 225, 80));
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            g.drawString("ELEVATION  680 m", 20, h - 20);
        }

        private void drawWater(java.awt.Graphics2D g, int w, int h) {
            g.setColor(new Color(45, 212, 191, 75));
            g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D river = new java.awt.geom.Path2D.Double();
            river.moveTo(w * .1, h * .86); river.curveTo(w * .28, h * .65, w * .18, h * .52, w * .4, h * .45);
            river.curveTo(w * .6, h * .37, w * .56, h * .16, w * .88, h * .06);
            g.draw(river);
        }

        private void drawRoads(java.awt.Graphics2D g, int w, int h) {
            g.setStroke(new BasicStroke(9, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(71, 85, 105, 200));
            java.awt.geom.Path2D road = new java.awt.geom.Path2D.Double();
            road.moveTo(w * .05, h * .75); road.curveTo(w * .22, h * .7, w * .34, h * .67, w * .45, h * .48); road.curveTo(w * .55, h * .28, w * .62, h * .32, w * .94, h * .19);
            g.draw(road);
            g.setStroke(new BasicStroke(2));
            g.setColor(new Color(148, 163, 184, 155));
            g.draw(road);
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(34, 197, 94, 230));
            java.awt.geom.Path2D selectedRoad = new java.awt.geom.Path2D.Double();
            selectedRoad.moveTo(w * .18, h * .84); selectedRoad.curveTo(w * .31, h * .74, w * .32, h * .62, w * .48, h * .55); selectedRoad.curveTo(w * .61, h * .47, w * .62, h * .28, w * .77, h * .2);
            g.draw(selectedRoad);
            g.setColor(new Color(34, 197, 94, 60));
            g.setStroke(new BasicStroke((float)(15 * zScale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(selectedRoad);
            g.setColor(new Color(34, 197, 94, 230));
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(selectedRoad);
        }

        private void drawFeatures(java.awt.Graphics2D g, int w, int h) {
            int[][] points = {{(int)(w*.48), (int)(h*.55)}, {(int)(w*.62), (int)(h*.36)}, {(int)(w*.31), (int)(h*.73)}};
            for (int i = 0; i < points.length; i++) {
                int r = i == selected ? (int)(9 * zScale) : 6;
                g.setColor(i == selected ? GREEN : new Color(248, 250, 252));
                g.fillOval(points[i][0] - r, points[i][1] - r, r * 2, r * 2);
                g.setColor(BG);
                g.setStroke(new BasicStroke(2));
                g.drawOval(points[i][0] - r, points[i][1] - r, r * 2, r * 2);
            }
        }

        private void drawLegend(java.awt.Graphics2D g) {
            g.setColor(new Color(15, 23, 42, 220));
            g.fillRoundRect(15, 15, 152, 62, 6, 6);
            g.setColor(new Color(248, 250, 252, 230));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString(style == 0 ? "OSM STANDARD" : style == 1 ? "OSM TERRAIN" : "OPENTOPO MAP", 27, 34);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(MUTED);
            g.drawString("Selected layer · public.roads", 27, 54);
            g.setColor(GREEN); g.fillOval(27, 63, 7, 7);
            g.setColor(TEXT); g.drawString("active feature", 41, 70);
        }
    }

    private static final class RoadsTableModel extends AbstractTableModel {
        private final String[] columns = {"id", "geom", "road_type", "name", "length_m", "z_value"};
        private final Object[][] rows = {
                {"65001149", "LINESTRING", "motorway", "连霍高速", "3587.47", "680"},
                {"65001662", "LINESTRING", "motorway", "G30连霍高速", "4243.10", "702"},
                {"65001663", "LINESTRING", "trunk", "克拉玛依市五五新镇", "49.00", "671"},
                {"65001664", "LINESTRING", "motorway", "哈密市白碱湖", "3133.00", "690"},
                {"65001223", "LINESTRING", "primary", "沪霍线", "4107.928", "714"},
                {"840", "POINT", "junction", "乌鲁木齐绕城高速", "106.308", "688"}
        };
        @Override public int getRowCount() { return rows.length; }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) { return rows[rowIndex][columnIndex]; }
    }
}
