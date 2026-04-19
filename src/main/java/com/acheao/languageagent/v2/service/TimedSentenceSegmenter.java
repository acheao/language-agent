package com.acheao.languageagent.v2.service;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TimedSentenceSegmenter {

    private TimedSentenceSegmenter() {
    }

    static List<SentenceSegment> segment(List<YoutubeImportService.TranscriptSegment> transcriptSegments) {
        List<TimedFragment> fragments = new ArrayList<>();
        StringBuilder transcript = new StringBuilder();

        for (YoutubeImportService.TranscriptSegment segment : transcriptSegments) {
            String text = normalizeText(segment.text());
            if (text.isBlank()) {
                continue;
            }
            if (transcript.length() > 0) {
                transcript.append(' ');
            }
            int textStart = transcript.length();
            transcript.append(text);
            int textEnd = transcript.length();
            fragments.add(new TimedFragment(textStart, textEnd, segment.startSeconds(), segment.endSeconds()));
        }

        if (fragments.isEmpty()) {
            return List.of();
        }

        List<TextRange> sentenceRanges = sentenceRanges(transcript.toString());
        if (sentenceRanges.isEmpty()) {
            sentenceRanges = List.of(new TextRange(0, transcript.length()));
        }

        List<SentenceSegment> sentences = new ArrayList<>();
        int index = 0;
        for (TextRange range : sentenceRanges) {
            String text = transcript.substring(range.start(), range.end()).trim();
            if (text.isBlank()) {
                continue;
            }

            TimedFragment firstFragment = firstOverlappingFragment(fragments, range);
            TimedFragment lastFragment = lastOverlappingFragment(fragments, range);
            if (firstFragment == null || lastFragment == null) {
                continue;
            }

            sentences.add(new SentenceSegment(
                    index++,
                    text,
                    estimateSeconds(firstFragment, range.start()),
                    estimateSeconds(lastFragment, range.end())));
        }

        return sentences;
    }

    private static List<TextRange> sentenceRanges(String transcript) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(transcript);

        List<TextRange> ranges = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            int trimmedStart = trimLeadingWhitespace(transcript, start, end);
            int trimmedEnd = trimTrailingWhitespace(transcript, trimmedStart, end);
            if (trimmedStart < trimmedEnd) {
                ranges.add(new TextRange(trimmedStart, trimmedEnd));
            }
        }
        return ranges;
    }

    private static TimedFragment firstOverlappingFragment(List<TimedFragment> fragments, TextRange range) {
        for (TimedFragment fragment : fragments) {
            if (fragment.textEnd() > range.start() && fragment.textStart() < range.end()) {
                return fragment;
            }
        }
        return null;
    }

    private static TimedFragment lastOverlappingFragment(List<TimedFragment> fragments, TextRange range) {
        for (int index = fragments.size() - 1; index >= 0; index--) {
            TimedFragment fragment = fragments.get(index);
            if (fragment.textEnd() > range.start() && fragment.textStart() < range.end()) {
                return fragment;
            }
        }
        return null;
    }

    private static Double estimateSeconds(TimedFragment fragment, int textOffset) {
        if (fragment.startSeconds() == null) {
            return null;
        }
        if (fragment.endSeconds() == null) {
            return fragment.startSeconds();
        }
        int span = fragment.textEnd() - fragment.textStart();
        if (span <= 0) {
            return fragment.endSeconds();
        }

        int clampedOffset = Math.max(fragment.textStart(), Math.min(textOffset, fragment.textEnd()));
        double progress = (double) (clampedOffset - fragment.textStart()) / span;
        return fragment.startSeconds() + ((fragment.endSeconds() - fragment.startSeconds()) * progress);
    }

    private static int trimLeadingWhitespace(String text, int start, int end) {
        int index = start;
        while (index < end && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int trimTrailingWhitespace(String text, int start, int end) {
        int index = end;
        while (index > start && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    record SentenceSegment(int orderIndex, String text, Double startSeconds, Double endSeconds) {
    }

    private record TimedFragment(int textStart, int textEnd, Double startSeconds, Double endSeconds) {
    }

    private record TextRange(int start, int end) {
    }
}
