package com.jobskills.dto;

import com.jobskills.model.enums.Municipality;

import java.util.List;

/**
 * A municipality the project covers, with its official PSGC code and the
 * centre of its verified boundary.
 */
public record GeoMunicipalityDto(
    String code,
    String name,
    Municipality value,
    GeoCoordinateDto center,
    int barangayCount,
    boolean boundaryAvailable,
    boolean projectCoverage
) {
}
