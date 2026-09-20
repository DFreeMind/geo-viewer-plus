package cn.duqimeng.geoviewerplus;

/** Tile protocol used by a map source. Keep this small and explicit for future source types. */
public enum MapSourceType {
    RASTER_XYZ("raster", "Raster XYZ / TMS"),
    MVT("mvt", "MVT vector tiles");

    private final String id;
    private final String label;

    MapSourceType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    @Override public String toString() { return label; }

    public static MapSourceType fromId(String id) {
        for (MapSourceType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return RASTER_XYZ;
    }
}
