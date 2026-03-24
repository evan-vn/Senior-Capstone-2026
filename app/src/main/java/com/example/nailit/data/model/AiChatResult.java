package com.example.nailit.data.model;

import java.util.ArrayList;
import java.util.List;

public class AiChatResult {

    private final String assistantMessage;
    private final List<AiMatchedOption> options;

    public AiChatResult(String assistantMessage,
                        List<AiMatchedOption> options) {
        this.assistantMessage = assistantMessage;
        this.options = options != null ? options : new ArrayList<>();
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public List<AiMatchedOption> getOptions() {
        return options;
    }
}

