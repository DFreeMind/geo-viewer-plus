package cn.duqimeng.geoviewerplus;

import java.util.Objects;

/** An XYZ/TMS basemap definition, either built in or user-configured. */
public final class MapSource {
    private final String name;
    private final String template;
    private final String attribution;
    private final boolean tms;
    private final String subdomains;
    private final boolean custom;
    private final MapSourceType type;
    private final String vectorLayer;

    public MapSource(String name, String template, String attribution, boolean tms) {
        this(name, template, attribution, tms, "", false, MapSourceType.RASTER_XYZ, "");
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains) {
        this(name, template, attribution, tms, subdomains, false, MapSourceType.RASTER_XYZ, "");
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains, boolean custom) {
        this(name, template, attribution, tms, subdomains, custom, MapSourceType.RASTER_XYZ, "");
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains, boolean custom, MapSourceType type, String vectorLayer) {
        this.name = Objects.requireNonNull(name);
        this.template = Objects.requireNonNull(template);
        this.attribution = attribution == null ? "" : attribution;
        this.tms = tms;
        this.subdomains = subdomains == null ? "" : subdomains;
        this.custom = custom;
        this.type = type == null ? MapSourceType.RASTER_XYZ : type;
        this.vectorLayer = vectorLayer == null ? "" : vectorLayer;
    }

    public String name() { return name; }
    public String template() { return template; }
    public String attribution() { return attribution; }
    public boolean tms() { return tms; }
    public String subdomains() { return subdomains; }
    public boolean custom() { return custom; }
    public MapSourceType type() { return type; }
    public String vectorLayer() { return vectorLayer; }

    @Override public String toString() { return name; }
}
