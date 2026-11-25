package com.jobskills.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobskills.dto.*;
import com.jobskills.model.enums.Municipality;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Geographic reference data for the JSM coverage area.
 *
 * Boundaries and barangay names come from the PSA PSGC-aligned GeoJSON published
 * by faeldon/philippines-json-maps (PSGC 3Q 2026). They are read from bundled
 * resources, never generated, and are used both to draw the maps and to validate
 * an employer's workplace pin against the municipality (and the barangay) the
 * employer selected.
 *
 * The service also owns the tile-source configuration so no page hardcodes a
 * map provider.
 */
@Service
@RequiredArgsConstructor
public class GeoService {

    /** The four municipalities JSM covers, keyed by official 10-digit PSGC code. */
    private static final Map<String, Municipality> COVERAGE = new LinkedHashMap<>();

    static {
        COVERAGE.put("0501601000", Municipality.BASUD);
        COVERAGE.put("0501603000", Municipality.DAET);
        COVERAGE.put("0501606000", Municipality.LABO);
        COVERAGE.put("0501612000", Municipality.VINZONS);
    }

    /**
     * Boundary polygons are simplified (1% of vertices), so a pin a few metres
     * outside a coastline can still be a real workplace. 250 m accepts those
     * without ever accepting a pin in a neighbouring municipality.
     */
    private static final double BOUNDARY_TOLERANCE_METERS = 250;

    private static final String PROVINCE = "Camarines Norte";
    private static final String PROVINCE_CODE = "0501600000";

    private static final String COVERAGE_NOTE =
        "JSM covers Basud, Daet, Labo and Vinzons. Municipal boundaries are drawn for the whole "
        + "municipality; a barangay appears with jobs only when an employer has posted there.";
    private static final String BARANGAY_NOTE =
        "All 125 barangays of the four municipalities come from the PSA PSGC reference list with "
        + "PSGC codes and PSGC-aligned boundaries. Being listed does not mean a barangay is part of "
        + "the study's selected coverage, and a barangay with no jobs has no listings.";

    private final ObjectMapper objectMapper;

    @Value("${jsm.map.tile-url:https://tile.openstreetmap.org/{z}/{x}/{y}.png}")
    private String tileUrl;

    @Value("${jsm.map.attribution:&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors}")
    private String attribution;

    @Value("${jsm.map.min-zoom:8}")
    private int minZoom;

    @Value("${jsm.map.max-zoom:18}")
    private int maxZoom;

    @Value("${jsm.map.center-lat:14.1290}")
    private double centerLat;

    @Value("${jsm.map.center-lng:122.9200}")
    private double centerLng;

    @Value("${jsm.map.zoom:10}")
    private int zoom;

    private final Map<String, MunicipalityFeature> municipalitiesByCode = new LinkedHashMap<>();
    private final Map<Municipality, MunicipalityFeature> municipalitiesByEnum = new EnumMap<>(Municipality.class);
    private final Map<String, BarangayFeature> barangaysByCode = new LinkedHashMap<>();
    private final Map<String, List<BarangayFeature>> barangaysByMunicipality = new LinkedHashMap<>();

    /* ---------- loaded data ------------------------------------------------ */

    private static final class MunicipalityFeature {
        String code;
        String name;
        Municipality value;
        List<List<double[][]>> polygons;
        double minLat, minLng, maxLat, maxLng;
        JsonNode geometry;
    }

    private static final class BarangayFeature {
        String code;
        String name;
        String municipalityCode;
        Municipality municipality;
        List<List<double[][]>> polygons;
        JsonNode geometry;
    }

    /** A validated workplace location, with official codes resolved from the data. */
    public record ResolvedLocation(String municipalityCode, String barangayCode, String barangayName) {
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("geo/municipalities.geojson").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode feature : root.path("features")) {
                JsonNode props = feature.path("properties");
                String code = props.path("code").asText();
                Municipality value = COVERAGE.get(code);
                if (value == null) {
                    continue;
                }
                MunicipalityFeature muni = new MunicipalityFeature();
                muni.code = code;
                muni.name = props.path("name").asText();
                muni.value = value;
                muni.geometry = feature.path("geometry");
                muni.polygons = parseGeometry(muni.geometry);
                computeBounds(muni);
                if (muni.polygons.isEmpty()) {
                    throw new IllegalStateException("No boundary polygon for " + muni.name);
                }
                municipalitiesByCode.put(code, muni);
                municipalitiesByEnum.put(value, muni);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the municipality boundary data", e);
        }

        if (!municipalitiesByEnum.keySet().containsAll(COVERAGE.values())) {
            throw new IllegalStateException(
                "Boundary data is missing a project municipality: " + municipalitiesByEnum.keySet());
        }

        try (InputStream in = new ClassPathResource("geo/barangays.geojson").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode feature : root.path("features")) {
                JsonNode props = feature.path("properties");
                String municipalityCode = props.path("municipalityCode").asText();
                if (!municipalitiesByCode.containsKey(municipalityCode)) {
                    continue;
                }
                BarangayFeature barangay = new BarangayFeature();
                barangay.code = props.path("code").asText();
                barangay.name = props.path("name").asText();
                barangay.municipalityCode = municipalityCode;
                barangay.municipality = municipalitiesByCode.get(municipalityCode).value;
                barangay.geometry = feature.path("geometry");
                barangay.polygons = parseGeometry(barangay.geometry);
                barangaysByCode.put(barangay.code, barangay);
                barangaysByMunicipality
                    .computeIfAbsent(municipalityCode, key -> new ArrayList<>())
                    .add(barangay);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the barangay boundary data", e);
        }

        if (barangaysByCode.isEmpty()) {
            throw new IllegalStateException("No barangay reference data was loaded");
        }
    }

    /* ---------- read API --------------------------------------------------- */

    public GeoCoverageDto coverage() {
        List<GeoMunicipalityDto> items = municipalitiesByCode.values().stream()
            .map(muni -> new GeoMunicipalityDto(
                muni.code,
                muni.name,
                muni.value,
                new GeoCoordinateDto(
                    round((muni.minLat + muni.maxLat) / 2),
                    round((muni.minLng + muni.maxLng) / 2)),
                barangaysByMunicipality.getOrDefault(muni.code, List.of()).size(),
                !muni.polygons.isEmpty(),
                true))
            .toList();

        return new GeoCoverageDto(
            PROVINCE,
            PROVINCE_CODE,
            items,
            barangaysByCode.size(),
            new GeoCoverageDto.GeoSourceDto(
                "Philippine administrative boundaries, PSGC-aligned GeoJSON",
                "Philippine Statistics Authority (PSGC names and codes); NAMRIA-derived geometry "
                    + "published by the faeldon/philippines-json-maps project",
                "MIT for the dataset repository; PSGC names/codes are PSA reference data",
                "PSGC 3Q 2026 (repository latest, boundaries derived from NAMRIA v2023-11-06)",
                "https://github.com/faeldon/philippines-json-maps"),
            COVERAGE_NOTE,
            BARANGAY_NOTE);
    }

    public GeoMapConfigDto mapConfig() {
        return new GeoMapConfigDto(
            tileUrl,
            attribution,
            minZoom,
            maxZoom,
            new GeoCoordinateDto(centerLat, centerLng),
            zoom,
            coverage());
    }

    public List<GeoBarangayDto> barangays(String municipalityKey) {
        MunicipalityFeature muni = findMunicipality(municipalityKey);
        if (muni == null) {
            throw new IllegalArgumentException("Unknown municipality: " + municipalityKey);
        }
        return barangaysByMunicipality.getOrDefault(muni.code, List.of()).stream()
            .sorted(Comparator.comparing(barangay -> barangay.name))
            .map(barangay -> new GeoBarangayDto(
                barangay.code,
                barangay.name,
                barangay.municipalityCode,
                barangay.municipality,
                !barangay.polygons.isEmpty()))
            .toList();
    }

    /**
     * Boundary layer as GeoJSON.
     *
     * @param level            "municipality" (the four covered municipalities) or "barangay"
     * @param municipalityKey  a PSGC code or enum name; required for barangays, ignored for municipalities
     */
    public ObjectNode boundaries(String level, String municipalityKey) {
        ObjectNode collection = objectMapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        ArrayNode features = collection.putArray("features");

        if ("barangay".equalsIgnoreCase(level)) {
            MunicipalityFeature muni = findMunicipality(municipalityKey);
            if (muni == null) {
                throw new IllegalArgumentException("Select a municipality to load its barangay boundaries");
            }
            for (BarangayFeature barangay : barangaysByMunicipality.getOrDefault(muni.code, List.of())) {
                ObjectNode feature = features.addObject();
                feature.put("type", "Feature");
                ObjectNode props = feature.putObject("properties");
                props.put("code", barangay.code);
                props.put("name", barangay.name);
                props.put("municipalityCode", barangay.municipalityCode);
                props.put("municipality", barangay.municipality.name());
                props.put("level", "barangay");
                feature.set("geometry", barangay.geometry);
            }
        } else if ("municipality".equalsIgnoreCase(level) || level == null || level.isBlank()) {
            for (MunicipalityFeature muni : municipalitiesByCode.values()) {
                ObjectNode feature = features.addObject();
                feature.put("type", "Feature");
                ObjectNode props = feature.putObject("properties");
                props.put("code", muni.code);
                props.put("name", muni.name);
                props.put("municipality", muni.value.name());
                props.put("level", "municipality");
                feature.set("geometry", muni.geometry);
            }
        } else {
            throw new IllegalArgumentException("Unknown boundary level: " + level);
        }

        return collection;
    }

    /* ---------- validation ------------------------------------------------ */

    /**
     * Validates a workplace selection against the reference data.
     *
     * A municipality and barangay always have to agree, and a pin has to fall
     * inside the municipality it claims (with a small tolerance for simplified
     * coastlines). Nothing is silently corrected: a mismatched selection is an
     * error the employer has to fix.
     */
    public ResolvedLocation validate(Municipality municipality,
                                     String municipalityCode,
                                     String barangayCode,
                                     String barangayName,
                                     Double latitude,
                                     Double longitude) {
        if (municipality == null) {
            throw new IllegalArgumentException("Municipality is required");
        }
        MunicipalityFeature muni = municipalitiesByEnum.get(municipality);
        if (muni == null) {
            throw new IllegalArgumentException("That municipality is outside the JSM coverage area");
        }
        if (municipalityCode != null && !municipalityCode.isBlank()
            && !muni.code.equals(municipalityCode.trim())) {
            throw new IllegalArgumentException(
                "The municipality code does not match " + muni.name + ". Reload the form and try again.");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Set both the latitude and the longitude, or neither.");
        }

        BarangayFeature barangay = null;
        if (barangayCode != null && !barangayCode.isBlank()) {
            barangay = barangaysByCode.get(barangayCode.trim());
            if (barangay == null) {
                throw new IllegalArgumentException("That barangay is not in the PSA reference data.");
            }
            if (!barangay.municipalityCode.equals(muni.code)) {
                throw new IllegalArgumentException(
                    barangay.name + " belongs to " + nameOf(barangay.municipalityCode)
                        + ", not " + muni.name + ".");
            }
        } else if (barangayName != null && !barangayName.isBlank()) {
            barangay = findBarangayByName(muni.code, barangayName.trim());
            if (barangay == null) {
                throw new IllegalArgumentException("\"" + barangayName.trim() + "\" is not an official barangay of "
                    + muni.name + ". Choose one from the barangay list.");
            }
        }

        if (latitude != null) {
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("Those coordinates are not valid.");
            }
            if (!insideOrNear(muni.polygons, longitude, latitude, BOUNDARY_TOLERANCE_METERS)) {
                throw new IllegalArgumentException(
                    "The pinned location is outside " + muni.name + ". Move the pin inside the municipality.");
            }
            BarangayFeature containing = containingBarangay(muni.code, longitude, latitude);
            if (containing != null && barangay != null
                && !containing.code.equals(barangay.code)
                && !insideOrNear(barangay.polygons, longitude, latitude, 100)) {
                throw new IllegalArgumentException(
                    "The pinned location is inside " + containing.name + ", not " + barangay.name
                        + ". Move the pin or choose " + containing.name + ".");
            }
        }

        return new ResolvedLocation(
            muni.code,
            barangay != null ? barangay.code : null,
            barangay != null ? barangay.name : null);
    }

    /** True when the point falls inside (or within the tolerance of) the municipality. */
    public boolean insideMunicipality(Municipality municipality, double latitude, double longitude) {
        MunicipalityFeature muni = municipalitiesByEnum.get(municipality);
        return muni != null && insideOrNear(muni.polygons, longitude, latitude, BOUNDARY_TOLERANCE_METERS);
    }

    public String municipalityCode(Municipality municipality) {
        MunicipalityFeature muni = municipalitiesByEnum.get(municipality);
        return muni == null ? null : muni.code;
    }

    public String municipalityName(String municipalityCode) {
        MunicipalityFeature muni = municipalitiesByCode.get(municipalityCode);
        return muni == null ? null : muni.name;
    }

    public String barangayName(String barangayCode) {
        BarangayFeature barangay = barangayCode == null ? null : barangaysByCode.get(barangayCode);
        return barangay == null ? null : barangay.name;
    }

    /* ---------- geometry helpers ----------------------------------------- */

    private MunicipalityFeature findMunicipality(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String value = key.trim();
        MunicipalityFeature muni = municipalitiesByCode.get(value);
        if (muni != null) {
            return muni;
        }
        try {
            return municipalitiesByEnum.get(Municipality.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String nameOf(String municipalityCode) {
        MunicipalityFeature muni = municipalitiesByCode.get(municipalityCode);
        return muni == null ? "another municipality" : muni.name;
    }

    private BarangayFeature findBarangayByName(String municipalityCode, String name) {
        for (BarangayFeature barangay : barangaysByMunicipality.getOrDefault(municipalityCode, List.of())) {
            if (barangay.name.equalsIgnoreCase(name)) {
                return barangay;
            }
        }
        return null;
    }

    private BarangayFeature containingBarangay(String municipalityCode, double lng, double lat) {
        for (BarangayFeature barangay : barangaysByMunicipality.getOrDefault(municipalityCode, List.of())) {
            if (contains(barangay.polygons, lng, lat)) {
                return barangay;
            }
        }
        return null;
    }

    private static List<List<double[][]>> parseGeometry(JsonNode geometry) {
        List<List<double[][]>> polygons = new ArrayList<>();
        if (geometry == null || geometry.isMissingNode()) {
            return polygons;
        }
        String type = geometry.path("type").asText();
        if ("Polygon".equals(type)) {
            polygons.add(parsePolygon(geometry.get("coordinates")));
        } else if ("MultiPolygon".equals(type)) {
            for (JsonNode polygon : geometry.get("coordinates")) {
                polygons.add(parsePolygon(polygon));
            }
        }
        return polygons;
    }

    private static List<double[][]> parsePolygon(JsonNode rings) {
        List<double[][]> parsed = new ArrayList<>();
        if (rings == null || !rings.isArray()) {
            return parsed;
        }
        for (JsonNode ring : rings) {
            double[][] points = new double[ring.size()][2];
            for (int i = 0; i < ring.size(); i++) {
                points[i][0] = ring.get(i).get(0).asDouble();
                points[i][1] = ring.get(i).get(1).asDouble();
            }
            parsed.add(points);
        }
        return parsed;
    }

    private void computeBounds(MunicipalityFeature muni) {
        double minLat = Double.MAX_VALUE, minLng = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (List<double[][]> polygon : muni.polygons) {
            for (double[][] ring : polygon) {
                for (double[] point : ring) {
                    minLng = Math.min(minLng, point[0]);
                    maxLng = Math.max(maxLng, point[0]);
                    minLat = Math.min(minLat, point[1]);
                    maxLat = Math.max(maxLat, point[1]);
                }
            }
        }
        muni.minLat = minLat;
        muni.maxLat = maxLat;
        muni.minLng = minLng;
        muni.maxLng = maxLng;
    }

    private static boolean contains(List<List<double[][]>> polygons, double lng, double lat) {
        for (List<double[][]> polygon : polygons) {
            if (polygon.isEmpty()) {
                continue;
            }
            if (!ringContains(polygon.get(0), lng, lat)) {
                continue;
            }
            boolean inHole = false;
            for (int i = 1; i < polygon.size(); i++) {
                if (ringContains(polygon.get(i), lng, lat)) {
                    inHole = true;
                    break;
                }
            }
            if (!inHole) {
                return true;
            }
        }
        return false;
    }

    private static boolean ringContains(double[][] ring, double lng, double lat) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double xi = ring[i][0], yi = ring[i][1];
            double xj = ring[j][0], yj = ring[j][1];
            boolean intersect = ((yi > lat) != (yj > lat))
                && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Inside the polygons, or within {@code toleranceMeters} of their outline. */
    private static boolean insideOrNear(List<List<double[][]>> polygons, double lng, double lat, double toleranceMeters) {
        if (contains(polygons, lng, lat)) {
            return true;
        }
        for (List<double[][]> polygon : polygons) {
            for (double[][] ring : polygon) {
                if (distanceToRingMeters(ring, lng, lat) <= toleranceMeters) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceToRingMeters(double[][] ring, double lng, double lat) {
        double metersPerDegreeLat = 111_320d;
        double metersPerDegreeLng = 111_320d * Math.cos(Math.toRadians(lat));
        double best = Double.MAX_VALUE;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double ax = (ring[j][0] - lng) * metersPerDegreeLng;
            double ay = (ring[j][1] - lat) * metersPerDegreeLat;
            double bx = (ring[i][0] - lng) * metersPerDegreeLng;
            double by = (ring[i][1] - lat) * metersPerDegreeLat;
            best = Math.min(best, distanceToSegment(ax, ay, bx, by));
        }
        return best;
    }

    private static double distanceToSegment(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0 && dy == 0) {
            return Math.hypot(ax, ay);
        }
        double t = -((ax * dx) + (ay * dy)) / ((dx * dx) + (dy * dy));
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    /**
     * Great-circle distance in kilometres. Used for the Near Me filter; the
     * seeker's coordinates are never stored or returned to an employer.
     */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
