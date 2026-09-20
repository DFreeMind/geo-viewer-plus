package cn.duqimeng.geoviewerplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeometryNormalizerTest {
    @Test void keepsGeographicWktAndRemovesSridPrefix() {
        GeometryNormalizer.Result result = GeometryNormalizer.normalize("SRID=4326;POINT(120 30)");
        assertTrue(result.isSupported());
        assertEquals("POINT(120 30)", result.wkt());
    }

    @Test void convertsWebMercatorToWgs84() {
        GeometryNormalizer.Result result = GeometryNormalizer.normalize("SRID=3857;POINT(0 0)");
        assertTrue(result.isSupported());
        assertEquals("POINT(0 0)", result.wkt());
    }

    @Test void acceptsCgcs2000DegreeCoordinates() {
        assertTrue(GeometryNormalizer.normalize("SRID=4490;POINT(120 30)").isSupported());
    }

    @Test void rejectsAnUnknownProjectionInsteadOfPlottingItInTheWrongPlace() {
        GeometryNormalizer.Result result = GeometryNormalizer.normalize("SRID=32650;POINT(500000 3300000)");
        assertFalse(result.isSupported());
        assertEquals("unsupported SRID EPSG:32650", result.problem());
    }

    @Test void keepsDimensionalAndCollectionWktForTheBrowserParser() {
        assertTrue(GeometryNormalizer.normalize("POINT Z (120 30 12)").isSupported());
        assertTrue(GeometryNormalizer.normalize("GEOMETRYCOLLECTION(POINT(120 30),LINESTRING(120 30,121 31))").isSupported());
    }

    @Test void convertsFourOrdinateWebMercatorCoordinates() {
        GeometryNormalizer.Result result = GeometryNormalizer.normalize("SRID=3857;POINT ZM (0 0 12 99)");
        assertTrue(result.isSupported());
        assertEquals("POINT ZM (0 0)", result.wkt());
    }
}
