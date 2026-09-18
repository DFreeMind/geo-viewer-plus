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

    public MapSource(String name, String template, String attribution, boolean tms) {
        this(name, template, attribution, tms, "", false);
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains) {
        this(name, template, attribution, tms, subdomains, false);
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains, boolean custom) {
        this.name = Objects.requireNonNull(name);
        this.template = Objects.requireNonNull(template);
        this.attribution = attribution == null ? "" : attribution;
        this.tms = tms;
        this.subdomains = subdomains == null ? "" : subdomains;
        this.custom = custom;
    }

    public String name() { return name; }
    public String template() { return template; }
    public String attribution() { return attribution; }
    public boolean tms() { return tms; }
    public String subdomains() { return subdomains; }
    public boolean custom() { return custom; }

    @Override public String toString() { return name; }
}
