# Geographic coverage and job mapping

This document records where JSM's map data comes from, what it covers, and what
the feature deliberately does not claim. It is the reference for the geographic
parts of the app (Job Map, Map View in Search Jobs, the employer workplace pin
and the admin Geographic Map).

## Map stack

| Part | Choice |
| --- | --- |
| Map library | [Leaflet](https://leafletjs.com/) 1.9.4, loaded by `assets/js/jsm-map.js` |
| Marker clustering | `leaflet.markercluster` 1.5.3 (optional enhancement; markers still render if it fails) |
| Base tiles | OpenStreetMap-compatible XYZ tiles, configured on the server |
| Boundary layers | GeoJSON served from `/api/geo/boundaries` |

### Tile provider configuration

The tile URL and attribution are **not** written into any page. They come from
`GET /api/geo/map-config`, which reads:

| Variable | Default |
| --- | --- |
| `JSM_MAP_TILE_URL` | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` |
| `JSM_MAP_ATTRIBUTION` | `&copy; OpenStreetMap contributors` |
| `JSM_MAP_MIN_ZOOM` | `8` |
| `JSM_MAP_MAX_ZOOM` | `18` |
| `JSM_MAP_CENTER_LAT` / `JSM_MAP_CENTER_LNG` | `14.1290` / `122.9200` |
| `JSM_MAP_ZOOM` | `10` |

Attribution is always rendered by Leaflet's attribution control.

The default is OpenStreetMap's public tile service. It is usage-limited and
offered **without an uptime guarantee**, and JSM does not bulk-download or cache
tiles. For production traffic, point `JSM_MAP_TILE_URL` at a provider whose
usage policy covers the traffic (or a self-hosted tile server) before relying on
the base map.

## Geographic dataset

Bundled in `backend/src/main/resources/geo/`:

| File | Contents |
| --- | --- |
| `municipalities.geojson` | Boundaries for Basud (0501601000), Daet (0501603000), Labo (0501606000), Vinzons (0501612000) |
| `barangays.geojson` | 125 barangay boundaries with PSGC codes and their municipality |

Source: [faeldon/philippines-json-maps](https://github.com/faeldon/philippines-json-maps),
the PSGC-aligned GeoJSON build of PSA Philippine Standard Geographic Code
releases and NAMRIA-derived administrative boundaries.

- Reference release: PSGC 3Q 2026 (`latest/`, generated November 2026).
- Boundary geometry: derived from NAMRIA administrative boundaries v2023-11-06,
  simplified to 1% of vertices (`medres`).
- Municipality files: `provdists/medres/municities-provdist-501600000`, filtered
  to the four project municipalities.
- Barangay files: `municities/medres/bgysubmuns-municity-5016xxxxx`, which carry
  `adm4_psgc`, `adm4_en` and the parent `adm3_psgc`.
- Barangay counts were checked against PSA: Basud 29, Daet 25, Labo 52,
  Vinzons 19 (125 total).
- License: MIT for the dataset repository; PSGC names and codes are PSA
  reference data. Attribution is shown on the admin Geographic Map and quoted in
  `GET /api/geo/coverage`.
- Compatibility: plain WGS84 GeoJSON (`Polygon`/`MultiPolygon`), read by the
  backend with Jackson and returned to Leaflet as-is.

Nothing is hand-drawn, interpolated or generated: if a boundary or barangay is
missing from the dataset, it is missing from the app.

## What the map does and does not claim

1. **Geographic reference data** — every barangay listed exists in the PSA PSGC
   release and belongs to the stated municipality.
2. **Project coverage** — Basud, Daet, Labo and Vinzons are the municipalities
   JSM targets. Coverage is a project decision, not a PSA boundary attribute.
3. **Job listings** — a barangay or municipality only shows a job count when an
   employer has an active post there. A barangay with 0 jobs has no listings;
   it is not excluded from the reference data.

Only jobs whose employer confirmed a workplace pin have coordinates. Jobs with a
text-only location never get an invented marker: they stay in list search, and
the map states how many matching jobs are not plotted.

## Database

`V4__add_job_coordinates.sql` adds four nullable columns to `job_posts`:
`latitude`, `longitude`, `municipality_code`, `barangay_code`, plus a check
constraint that a pin is either complete or absent. Existing rows keep working
unchanged; nothing is backfilled.

## Validation rules

On create/update the backend checks, against the bundled data:

- the municipality code matches the selected municipality;
- the barangay code exists and belongs to that municipality;
- latitude/longitude are set together and are valid;
- the pin lies inside the municipality (250 m tolerance for simplified
  coastlines);
- if the pin falls inside a different barangay of the same municipality, the
  request is rejected instead of saving a mismatched location.

A rejected edit leaves the previously saved location untouched.

## Limitations

- Boundaries are simplified, so a pin within roughly 250 m of a municipality
  edge may validate as inside. This tolerance exists so coastal workplaces are
  not rejected.
- Barangay polygons do not cover every point (coastline and simplification
  gaps). A pin inside the municipality but in no barangay polygon is accepted
  when the selected barangay is the same municipality.
- The PSA reference data changes between quarters; updating requires replacing
  the two GeoJSON files and re-checking the four municipality codes.
- The public OSM tile service is not a guaranteed production tile source (see
  above).
- No employer address or seeker coordinate is sent to an external geocoding
  service. Near Me sends the temporary position only to JSM's own search
  endpoint, which uses it for that request and does not store it.
