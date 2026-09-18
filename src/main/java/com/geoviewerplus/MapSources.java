package com.geoviewerplus;

/** Public, no-key basemap endpoints used by the viewer's source switcher. */
public enum MapSources {
    OSM_STANDARD("OSM Standard", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
    OSM_TERRAIN("OSM Terrain", "https://tile.openstreetmap.de/{z}/{x}/{y}.png", "© OpenStreetMap contributors"),
    OPEN_TOPO("OpenTopoMap", "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors · SRTM");

    private final String title;
    private final String tileTemplate;
    private final String attribution;

    MapSources(String title, String tileTemplate, String attribution) {
        this.title = title;
        this.tileTemplate = tileTemplate;
        this.attribution = attribution;
    }

    public String title() { return title; }
    public String tileTemplate() { return tileTemplate; }
    public String attribution() { return attribution; }

    public static MapSources fromIndex(int index) {
        return values()[Math.max(0, Math.min(values().length - 1, index))];
    }
}

