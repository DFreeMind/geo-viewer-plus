package com.geoviewerplus;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GeoViewer;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.run.ui.DataAccessType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the visible result-set page without touching DataGrip's renderer. */
public final class GeoDataExtractor {
    private GeoDataExtractor() {}

    public static boolean hasGeometry(DataGrid grid) {
        return !extract(grid, 1).features().isEmpty();
    }

    public static Snapshot extract(DataGrid grid, int maxRows) {
        try {
            GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
            List<ModelIndex<GridColumn>> columns = grid.getVisibleColumns().asList();
            List<ModelIndex<GridRow>> rows = grid.getVisibleRows().asList();
            if (columns.isEmpty() || rows.isEmpty()) return new Snapshot(List.of(), "");

            ModelIndex<GridColumn> geometryColumn = findGeometryColumn(grid, model, columns, rows);
            if (geometryColumn == null) return new Snapshot(List.of(), "");

            List<Feature> features = new ArrayList<>();
            int rowCount = Math.min(maxRows, rows.size());
            for (int rowNumber = 0; rowNumber < rowCount; rowNumber++) {
                ModelIndex<GridRow> row = rows.get(rowNumber);
                String wkt = safeWkt(grid, geometryColumn, row);
                if (wkt.isBlank()) continue;
                List<Attribute> attributes = new ArrayList<>();
                for (ModelIndex<GridColumn> column : columns) {
                    GridColumn descriptor = model.getColumn(column);
                    Object value = model.getValueAt(row, column);
                    attributes.add(new Attribute(descriptor.getName(), value == null ? "" : String.valueOf(value)));
                }
                features.add(new Feature(row.asInteger(), wkt, attributes));
            }
            return new Snapshot(features, model.getColumn(geometryColumn).getName());
        } catch (Throwable ignored) {
            return new Snapshot(List.of(), "");
        }
    }

    private static ModelIndex<GridColumn> findGeometryColumn(
            DataGrid grid,
            GridModel<GridRow, GridColumn> model,
            List<ModelIndex<GridColumn>> columns,
            List<ModelIndex<GridRow>> rows) {
        for (ModelIndex<GridColumn> column : columns) {
            GridColumn descriptor = model.getColumn(column);
            String metadata = (descriptor.getName() + " " + descriptor.getTypeName()).toLowerCase(Locale.ROOT);
            boolean namedLikeGeometry = metadata.matches(".*(geom|geometry|geography|shape|wkt|geojson|latitude|longitude|lon|lat).*");
            if (namedLikeGeometry) {
                for (ModelIndex<GridRow> row : rows) {
                    if (!safeWkt(grid, column, row).isBlank()) return column;
                }
            }
        }
        for (ModelIndex<GridColumn> column : columns) {
            for (ModelIndex<GridRow> row : rows) {
                if (!safeWkt(grid, column, row).isBlank()) return column;
            }
        }
        return null;
    }

    private static String safeWkt(DataGrid grid, ModelIndex<GridColumn> column, ModelIndex<GridRow> row) {
        try {
            String value = GeoViewer.toWkt(grid, column, row);
            if (value == null) return "";
            String normalized = value.trim();
            return looksLikeWkt(normalized) ? normalized : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean looksLikeWkt(String value) {
        return value.matches("(?is)^(srid=\\d+;)?(point|multipoint|linestring|multilinestring|polygon|multipolygon|geometrycollection|circle)\\s*\\(.*")
                || value.matches("(?is)^(srid=\\d+;)?geometrycollection\\s*\\(.*");
    }

    public record Snapshot(List<Feature> features, String geometryColumn) {}
    public record Feature(int rowIndex, String wkt, List<Attribute> attributes) {}
    public record Attribute(String name, String value) {}
}
