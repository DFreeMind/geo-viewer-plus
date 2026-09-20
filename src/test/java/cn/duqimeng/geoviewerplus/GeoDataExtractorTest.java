package cn.duqimeng.geoviewerplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeoDataExtractorTest {
    @Test void recognizesDimensionalAndCollectionWkt() {
        assertTrue(GeoDataExtractor.looksLikeWkt("POINT Z (120 30 12)"));
        assertTrue(GeoDataExtractor.looksLikeWkt("SRID=4326;LINESTRING M (120 30 1, 121 31 2)"));
        assertTrue(GeoDataExtractor.looksLikeWkt("GEOMETRYCOLLECTION(POINT(120 30),LINESTRING(120 30,121 31))"));
    }

    @Test void rejectsValuesThatAreNotSupportedWkt() {
        assertFalse(GeoDataExtractor.looksLikeWkt("POINT EMPTY"));
        assertFalse(GeoDataExtractor.looksLikeWkt("CIRCULARSTRING(0 0, 1 1, 2 0)"));
    }
}
