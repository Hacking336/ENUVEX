package com.jobskills.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * How much of the information the matching engine uses is actually filled in.
 *
 * Only fields that change a match score are counted. A profile can be 100%
 * complete without every optional field, and an incomplete profile never blocks
 * searching or applying.
 */
@Data
public class ProfileCompletenessDto {

    private int percentage;
    private boolean complete;
    private List<Item> completed = new ArrayList<>();
    private List<Item> missing = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String key;
        private String label;
        private int weight;
        /** Profile page anchor that fixes this item. */
        private String href;
    }
}
