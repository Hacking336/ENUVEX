package com.jobskills.dto;

import java.util.List;

/**
 * Everything the map screens need to describe what they are showing, including
 * where the boundaries came from and what the reference data does not claim.
 */
public record GeoCoverageDto(
    String province,
    String provinceCode,
    List<GeoMunicipalityDto> municipalities,
    int barangayCount,
    GeoSourceDto source,
    String coverageNote,
    String barangayNote
) {
    public record GeoSourceDto(
        String name,
        String publisher,
        String license,
        String referenceDate,
        String url
    ) {
    }
}
