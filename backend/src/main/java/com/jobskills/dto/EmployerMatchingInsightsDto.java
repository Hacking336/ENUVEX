package com.jobskills.dto;

import java.util.Map;

/** Matching insights for employer analytics. */
public class EmployerMatchingInsightsDto {

    private double averageMatchScore;
    private double highestMatch;
    private long excellentMatches;   // 90%+
    private long strongMatches;      // 75-89%
    private long goodMatches;        // 60-74%
    private long partialMatches;     // 40-59%
    private long lowMatches;         // < 40%
    private Map<Integer, Long> scoreDistribution;

    // Getters and setters
    public double getAverageMatchScore() { return averageMatchScore; }
    public void setAverageMatchScore(double averageMatchScore) { this.averageMatchScore = averageMatchScore; }

    public double getHighestMatch() { return highestMatch; }
    public void setHighestMatch(double highestMatch) { this.highestMatch = highestMatch; }

    public long getExcellentMatches() { return excellentMatches; }
    public void setExcellentMatches(long excellentMatches) { this.excellentMatches = excellentMatches; }

    public long getStrongMatches() { return strongMatches; }
    public void setStrongMatches(long strongMatches) { this.strongMatches = strongMatches; }

    public long getGoodMatches() { return goodMatches; }
    public void setGoodMatches(long goodMatches) { this.goodMatches = goodMatches; }

    public long getPartialMatches() { return partialMatches; }
    public void setPartialMatches(long partialMatches) { this.partialMatches = partialMatches; }

    public long getLowMatches() { return lowMatches; }
    public void setLowMatches(long lowMatches) { this.lowMatches = lowMatches; }

    public Map<Integer, Long> getScoreDistribution() { return scoreDistribution; }
    public void setScoreDistribution(Map<Integer, Long> scoreDistribution) { this.scoreDistribution = scoreDistribution; }
}