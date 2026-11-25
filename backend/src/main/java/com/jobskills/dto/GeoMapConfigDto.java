package com.jobskills.dto;

/**
 * Tile source and viewport for every JSM map.
 *
 * The frontend never names a tile provider itself: it reads this response, so
 * the provider can be changed with JSM_MAP_TILE_URL / JSM_MAP_ATTRIBUTION
 * without touching a page. The default points at OpenStreetMap's public tile
 * servers, which are usage-limited and not guaranteed; a deployment that needs
 * guaranteed uptime should set a commercial or self-hosted tile URL.
 */
public record GeoMapConfigDto(
    String tileUrl,
    String attribution,
    int minZoom,
    int maxZoom,
    GeoCoordinateDto center,
    int zoom,
    GeoCoverageDto coverage
) {
}
