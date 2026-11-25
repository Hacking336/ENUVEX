package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Hypothetical skill acquisition request. Nothing is written to the profile. */
@Data
public class SimulationRequest {
    private List<String> skills = new ArrayList<>();

    /** Match band cut-off used for "current matching" and "potential matching". */
    private Integer threshold = 60;
}
