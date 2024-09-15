package org.example;

import java.util.List;

public class Survey {
    private String title;
    private List<String> questions;
    private List<List<String>> options;
    private long creatorId;

    public Survey(String title, List<String> questions, List<List<String>> options, long creatorId) {
        this.title = title;
        this.questions = questions;
        this.options = options;
        this.creatorId = creatorId;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public List<List<String>> getOptions() {
        return options;
    }

    public long getCreatorId() {
        return creatorId;
    }
}