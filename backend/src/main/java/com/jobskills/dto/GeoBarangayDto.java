package com.jobskills.dto;

import com.jobskills.model.enums.Municipality;

/**
 * One barangay from the verified PSGC reference set.
 *
 * An entry here only says the barangay exists and belongs to the municipality.
 * It does not mean the barangay is part of the project's selected coverage or
 * that any employer hires there.
 */
public record GeoBarangayDto(
    String code,
    String name,
    String municipalityCode,
    Municipality municipality,
    boolean boundaryAvailable
) {
}
