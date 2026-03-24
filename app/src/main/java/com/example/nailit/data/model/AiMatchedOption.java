package com.example.nailit.data.model;

import java.util.ArrayList;
import java.util.List;

public class AiMatchedOption {

    private final String label;
    private final List<String> colorKeywords;
    private final List<String> seasonTags;
    private final List<String> occasionTags;
    private final List<Polish> matches;

    public AiMatchedOption(String label,
                           List<String> colorKeywords,
                           List<String> seasonTags,
                           List<String> occasionTags,
                           List<Polish> matches) {
        this.label = label;
        this.colorKeywords = colorKeywords != null ? colorKeywords : new ArrayList<>();
        this.seasonTags = seasonTags != null ? seasonTags : new ArrayList<>();
        this.occasionTags = occasionTags != null ? occasionTags : new ArrayList<>();
        this.matches = matches != null ? matches : new ArrayList<>();
    }

    public String getLabel() {
        return label;
    }

    public List<String> getColorKeywords() {
        return colorKeywords;
    }

    public List<String> getSeasonTags() {
        return seasonTags;
    }

    public List<String> getOccasionTags() {
        return occasionTags;
    }

    public List<Polish> getMatches() {
        return matches;
    }
}

