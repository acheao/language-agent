package com.acheao.languageagent.v2.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ErrorTypeCatalog {

    public static final List<String> SUPPORTED_TYPES = List.of(
            "grammar",
            "tense",
            "article_preposition",
            "word_choice",
            "collocation",
            "spelling",
            "punctuation",
            "completeness",
            "listening_accuracy");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("grammar", "grammar"),
            Map.entry("tense", "tense"),
            Map.entry("verb_tense", "tense"),
            Map.entry("tenses", "tense"),
            Map.entry("article", "article_preposition"),
            Map.entry("articles", "article_preposition"),
            Map.entry("preposition", "article_preposition"),
            Map.entry("prepositions", "article_preposition"),
            Map.entry("article_preposition", "article_preposition"),
            Map.entry("word_choice", "word_choice"),
            Map.entry("wording", "word_choice"),
            Map.entry("vocabulary", "word_choice"),
            Map.entry("vocabulary_precision", "word_choice"),
            Map.entry("word_precision", "word_choice"),
            Map.entry("collocation", "collocation"),
            Map.entry("spelling", "spelling"),
            Map.entry("punctuation", "punctuation"),
            Map.entry("completeness", "completeness"),
            Map.entry("omission", "completeness"),
            Map.entry("missing_words", "completeness"),
            Map.entry("skip", "completeness"),
            Map.entry("listening", "listening_accuracy"),
            Map.entry("listening_accuracy", "listening_accuracy"),
            Map.entry("transcription", "listening_accuracy"),
            Map.entry("dictation", "listening_accuracy"));

    private ErrorTypeCatalog() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String key = raw.trim().toLowerCase()
                .replace('-', '_')
                .replace(' ', '_');
        if (ALIASES.containsKey(key)) {
            return ALIASES.get(key);
        }

        if (key.contains("listen") || key.contains("transcrib") || key.contains("dictation")) {
            return "listening_accuracy";
        }
        if (key.contains("spell")) {
            return "spelling";
        }
        if (key.contains("punct")) {
            return "punctuation";
        }
        if (key.contains("article") || key.contains("preposition")) {
            return "article_preposition";
        }
        if (key.contains("collocation")) {
            return "collocation";
        }
        if (key.contains("tense")) {
            return "tense";
        }
        if (key.contains("vocab") || key.contains("word")) {
            return "word_choice";
        }
        if (key.contains("omit") || key.contains("missing") || key.contains("complete") || key.contains("accuracy")) {
            return "completeness";
        }
        return "grammar";
    }

    public static List<String> normalizeAll(Collection<String> rawTypes) {
        if (rawTypes == null || rawTypes.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawType : rawTypes) {
            String item = normalize(rawType);
            if (!item.isBlank()) {
                normalized.add(item);
            }
        }
        return normalized.isEmpty() ? List.of() : new ArrayList<>(normalized);
    }
}
