package cn.duqimeng.geoviewerplus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the small, supported WKT subset to geographic WGS84 before it reaches Leaflet. */
public final class GeometryNormalizer {
    private static final Pattern SRID = Pattern.compile("(?is)^\\s*srid=(\\d+)\\s*;\\s*(.*)$");
    private static final Pattern COORDINATE = Pattern.compile("(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)(?:\\s+-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)?(?=\\s*(?:,|\\)))");

    private GeometryNormalizer() {}

    public static Result normalize(String wkt) {
        if (wkt == null || wkt.isBlank()) return Result.unsupported("empty geometry");
        Matcher sridMatcher = SRID.matcher(wkt.trim());
        int srid = 4326;
        String geometry = wkt.trim();
        if (sridMatcher.matches()) {
            srid = Integer.parseInt(sridMatcher.group(1));
            geometry = sridMatcher.group(2).trim();
        }
        // EPSG:4490 / CGCS2000 and EPSG:4258 use geographic degree coordinates;
        // Leaflet can render them alongside WGS84 without a projection conversion.
        if (srid == 4326 || srid == 4979 || srid == 4490 || srid == 4258) return Result.supported(geometry, srid);
        if (srid != 3857 && srid != 900913 && srid != 102100) return Result.unsupported("unsupported SRID EPSG:" + srid);

        Matcher coordinates = COORDINATE.matcher(geometry);
        StringBuffer converted = new StringBuffer();
        while (coordinates.find()) {
            double x = Double.parseDouble(coordinates.group(1));
            double y = Double.parseDouble(coordinates.group(2));
            if (Math.abs(x) > 20_037_508.35 || Math.abs(y) > 20_037_508.35) return Result.unsupported("invalid EPSG:3857 coordinate");
            double longitude = Math.toDegrees(x / 6_378_137d);
            double latitude = Math.toDegrees(2d * Math.atan(Math.exp(y / 6_378_137d)) - Math.PI / 2d);
            coordinates.appendReplacement(converted, Matcher.quoteReplacement(format(longitude) + " " + format(latitude)));
        }
        coordinates.appendTail(converted);
        return Result.supported(converted.toString(), srid);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.8f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record Result(String wkt, int sourceSrid, String problem) {
        static Result supported(String wkt, int sourceSrid) { return new Result(wkt, sourceSrid, ""); }
        static Result unsupported(String problem) { return new Result("", -1, problem); }
        public boolean isSupported() { return problem.isEmpty(); }
    }
}
