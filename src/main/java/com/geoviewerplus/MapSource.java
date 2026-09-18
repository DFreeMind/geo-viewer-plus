package com.geoviewerplus;

import java.util.Objects;

/** A user-configurable XYZ/TMS basemap definition. */
public final class MapSource {
    private final String name;
    private final String template;
    private final String attribution;
    private final boolean tms;
    private final String subdomains;

    public MapSource(String name, String template, String attribution, boolean tms) {
        this(name, template, attribution, tms, "");
    }

    public MapSource(String name, String template, String attribution, boolean tms, String subdomains) {
        this.name = Objects.requireNonNull(name);
        this.template = Objects.requireNonNull(template);
        this.attribution = attribution == null ? "" : attribution;
        this.tms = tms;
        this.subdomains = subdomains == null ? "" : subdomains;
    }

    public String name() { return name; }
    public String template() { return template; }
    public String attribution() { return attribution; }
    public boolean tms() { return tms; }
    public String subdomains() { return subdomains; }

    @Override public String toString() { return name; }
}
