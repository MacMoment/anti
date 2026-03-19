package com.anticheat.cloud;

import java.util.List;

public class CloudResponse {

    private double cheatProbability;
    private List<String> detectedCheats;
    private String confidence;
    private String recommendedAction;
    private String reasoning;

    public CloudResponse() {}

    public double getCheatProbability() { return cheatProbability; }
    public void setCheatProbability(double cheatProbability) { this.cheatProbability = cheatProbability; }

    public List<String> getDetectedCheats() { return detectedCheats; }
    public void setDetectedCheats(List<String> detectedCheats) { this.detectedCheats = detectedCheats; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
}
