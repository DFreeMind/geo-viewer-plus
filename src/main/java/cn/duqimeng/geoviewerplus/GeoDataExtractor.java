package cn.duqimeng.geoviewerplus;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GeoViewer;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the visible result-set page without touching DataGrip's renderer. */
public final class GeoDataExtractor {
    private static final Logger LOG = Logger.getInstance(GeoDataExtractor.class);
    private static final int MAX_ATTRIBUTES_PER_FEATURE = 40;
    private static final int MAX_ATTRIBUTE_LENGTH = 2_048;
    private GeoDataExtractor() {}

    public static boolean hasGeometry(DataGrid grid) {
        return !extract(grid, "").features().isEmpty();
    }

    /**
     * Extracts every row currently visible in DataGrip. Pagination and result limits belong to
     * the grid, not to this viewer.
     *
     * @param requestedColumnId the stable ID sent by the map column picker, or blank for the
     *                          first detected geometry column
     */
    public static Snapshot extract(DataGrid grid, String requestedColumnId) {
        try {
            GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
            List<ModelIndex<GridColumn>> columns = grid.getVisibleColumns().asList();
            List<ModelIndex<GridRow>> rows = grid.getVisibleRows().asList();
            if (columns.isEmpty() || rows.isEmpty()) return Snapshot.empty(rows.size());

            List<GeometryColumn> geometryColumns = findGeometryColumns(grid, model, columns, rows);
            if (geometryColumns.isEmpty()) return Snapshot.empty(rows.size());
            GeometryColumn geometryColumn = geometryColumns.stream()
                    .filter(column -> column.info().id().equals(requestedColumnId))
                    .findFirst()
                    .orElse(geometryColumns.get(0));

            List<Feature> features = new ArrayList<>();
            int skipped = 0;
            for (ModelIndex<GridRow> row : rows) {
                String wkt = geometryColumn.wktByRow().getOrDefault(row.asInteger(), "");
                if (wkt.isBlank()) {
                    skipped++;
                    continue;
                }
                List<Attribute> attributes = new ArrayList<>();
                for (int columnNumber = 0; columnNumber < Math.min(columns.size(), MAX_ATTRIBUTES_PER_FEATURE); columnNumber++) {
                    ModelIndex<GridColumn> column = columns.get(columnNumber);
                    GridColumn descriptor = model.getColumn(column);
                    Object value = model.getValueAt(row, column);
                    attributes.add(new Attribute(descriptor.getName(), boundedValue(value)));
                }
                features.add(new Feature(row.asInteger(), wkt, attributes));
            }
            return new Snapshot(features, geometryColumn.info().id(), geometryColumn.info().name(),
                    geometryColumns.stream().map(GeometryColumn::info).toList(), rows.size(), skipped);
        } catch (RuntimeException error) {
            LOG.warn("Could not extract visible geometries from the DataGrid", error);
            return Snapshot.empty(0);
        }
    }

    private static List<GeometryColumn> findGeometryColumns(
            DataGrid grid,
            GridModel<GridRow, GridColumn> model,
            List<ModelIndex<GridColumn>> columns,
            List<ModelIndex<GridRow>> rows) {
        List<ModelIndex<GridColumn>> likelyColumns = new ArrayList<>();
        for (ModelIndex<GridColumn> column : columns) {
            GridColumn descriptor = model.getColumn(column);
            String metadata = (descriptor.getName() + " " + descriptor.getTypeName()).toLowerCase(Locale.ROOT);
            boolean namedLikeGeometry = metadata.matches(".*(geom|geometry|geography|shape|wkt|geojson|latitude|longitude|lon|lat).*");
            if (namedLikeGeometry) likelyColumns.add(column);
        }
        List<GeometryColumn> detected = scanColumns(grid, model, likelyColumns, rows);
        if (!detected.isEmpty()) return detected;
        return scanColumns(grid, model, columns, rows);
    }

    private static List<GeometryColumn> scanColumns(
            DataGrid grid,
            GridModel<GridRow, GridColumn> model,
            List<ModelIndex<GridColumn>> columns,
            List<ModelIndex<GridRow>> rows) {
        List<GeometryColumn> detected = new ArrayList<>();
        for (ModelIndex<GridColumn> column : columns) {
            Map<Integer, String> wktByRow = new HashMap<>();
            for (ModelIndex<GridRow> row : rows) {
                String wkt = safeWkt(grid, column, row);
                if (!wkt.isBlank()) wktByRow.put(row.asInteger(), wkt);
            }
            if (!wktByRow.isEmpty()) {
                GridColumn descriptor = model.getColumn(column);
                detected.add(new GeometryColumn(new GeometryColumnInfo(column.asInteger() + ":" + descriptor.getName(), descriptor.getName()), wktByRow));
            }
        }
        return detected;
    }

    private static String safeWkt(DataGrid grid, ModelIndex<GridColumn> column, ModelIndex<GridRow> row) {
        try {
            String value = GeoViewer.toWkt(grid, column, row);
            if (value == null) return "";
            String normalized = value.trim();
            if (!looksLikeWkt(normalized)) return "";
            GeometryNormalizer.Result result = GeometryNormalizer.normalize(normalized);
            return result.isSupported() ? result.wkt() : "";
        } catch (RuntimeException error) {
            LOG.debug("Could not convert a result cell to WKT", error);
            return "";
        }
    }

    static boolean looksLikeWkt(String value) {
        return value.matches("(?is)^(srid=\\d+;)?(point|multipoint|linestring|multilinestring|polygon|multipolygon|geometrycollection)(?:\\s+(?:z|m|zm))?\\s*\\(.*");
    }

    private static String boundedValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return text.length() <= MAX_ATTRIBUTE_LENGTH ? text : text.substring(0, MAX_ATTRIBUTE_LENGTH) + "…";
    }

    public record Snapshot(List<Feature> features, String geometryColumnId, String geometryColumn,
                           List<GeometryColumnInfo> geometryColumns, int visibleRows, int skippedRows) {
        static Snapshot empty(int visibleRows) {
            return new Snapshot(List.of(), "", "", List.of(), visibleRows, 0);
        }
    }
    public record Feature(int rowIndex, String wkt, List<Attribute> attributes) {}
    public record Attribute(String name, String value) {}
    public record GeometryColumnInfo(String id, String name) {}

    private record GeometryColumn(GeometryColumnInfo info, Map<Integer, String> wktByRow) {}
}
