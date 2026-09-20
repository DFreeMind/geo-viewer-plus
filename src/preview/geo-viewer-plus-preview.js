(() => {
  window.dg = {
    selectInTable:() => {},
    changeSource:name => window.geoPlus.switchSource(name),
    addMapSource:() => {},
    editMapSource:() => {},
    removeMapSource:() => {},
    setDefaultSource:name => window.geoPlus.setDefaultSource(name),
    refreshMap:() => {}
  };

  window.geoPlus.addSources([
    { name:'高德道路', template:'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', attribution:'© 高德地图 · GCJ-02 · WGS84 data may be offset', tms:false, subdomains:'1234', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'高德影像', template:'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}', attribution:'© 高德地图 · GCJ-02 · WGS84 data may be offset', tms:false, subdomains:'1234', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'腾讯道路（兼容）', template:'https://rt{s}.map.gtimg.com/realtimerender?z={z}&x={x}&y={y}&type=vector&style=0&scene=0', attribution:'© 腾讯地图 · GCJ-02 · WGS84 data may be offset', tms:true, subdomains:'0123', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'OSM Standard', template:'https://tile.openstreetmap.org/{z}/{x}/{y}.png', attribution:'© OpenStreetMap contributors', tms:false, subdomains:'', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'OSM Humanitarian', template:'https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', attribution:'© OpenStreetMap contributors · HOT', tms:false, subdomains:'abc', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'OpenTopoMap', template:'https://tile.opentopomap.org/{z}/{x}/{y}.png', attribution:'© OpenStreetMap contributors · SRTM', tms:false, subdomains:'', custom:false, type:'raster_xyz', vectorLayer:'' },
    { name:'OpenFreeMap 开源矢量（每周更新）', template:'https://tiles.openfreemap.org/planet/latest/{z}/{x}/{y}.pbf', attribution:'© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors', tms:false, subdomains:'', custom:false, type:'mvt', vectorLayer:'water,waterway,landcover,landuse,park,building,transportation,boundary,place,poi' },
    { name:'Esri World Imagery（需可访问 ArcGIS Online）', template:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', attribution:'© Esri', tms:false, subdomains:'', custom:false, type:'raster_xyz', vectorLayer:'' }
  ]);
  window.geoPlus.switchSource('OSM Standard');
  window.geoPlus.loadFeatures({
    geometryColumn:'geom',
    visibleRows:3,
    truncatedRows:0,
    skippedRows:0,
    features:[
      { row:0, wkt:'LINESTRING(116.315 39.895,116.345 39.91,116.382 39.925,116.418 39.91)', attributes:[['name','示例路线'],['mode','独立网页预览']] },
      { row:1, wkt:'POINT(116.365 39.915)', attributes:[['name','示例点位']] },
      { row:2, wkt:'POLYGON((116.39 39.895,116.42 39.895,116.42 39.92,116.39 39.92,116.39 39.895))', attributes:[['name','示例区域']] }
    ]
  });
})()
