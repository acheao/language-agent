package com.acheao.languageagent.v2.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimedSentenceSegmenterTest {

    @Test
    void segment_mergesYoutubeCueFragmentsIntoSentenceUnits() {
        List<YoutubeImportService.TranscriptSegment> transcript = List.of(
                new YoutubeImportService.TranscriptSegment(0, 4.0, 7.43, "Probably I'll go out tonight. It's a"),
                new YoutubeImportService.TranscriptSegment(1, 7.44, 9.99, "quite big house"),
                new YoutubeImportService.TranscriptSegment(2, 10.0, 14.47, "or I ate the three last biscuits. If you"),
                new YoutubeImportService.TranscriptSegment(3, 14.48, 17.43, "have, I'm afraid you're making some very"),
                new YoutubeImportService.TranscriptSegment(4, 17.44, 19.349, "common English mistakes. But don't")
        );

        List<TimedSentenceSegmenter.SentenceSegment> segments = TimedSentenceSegmenter.segment(transcript);

        assertThat(segments)
                .extracting(TimedSentenceSegmenter.SentenceSegment::text)
                .containsExactly(
                        "Probably I'll go out tonight.",
                        "It's a quite big house or I ate the three last biscuits.",
                        "If you have, I'm afraid you're making some very common English mistakes.",
                        "But don't");
        assertThat(segments.get(0).startSeconds()).isEqualTo(4.0);
        assertThat(segments.get(0).endSeconds()).isLessThan(7.43);
        assertThat(segments.get(1).startSeconds()).isGreaterThan(segments.get(0).endSeconds());
        assertThat(segments.get(2).endSeconds()).isLessThanOrEqualTo(19.349);
    }

    @Test
    void segment_splitsMultipleSentencesInsideSingleTimedFragment() {
        List<YoutubeImportService.TranscriptSegment> transcript = List.of(
                new YoutubeImportService.TranscriptSegment(0, 0.0, 6.0, "Hello there. General Kenobi.")
        );

        List<TimedSentenceSegmenter.SentenceSegment> segments = TimedSentenceSegmenter.segment(transcript);

        assertThat(segments)
                .extracting(TimedSentenceSegmenter.SentenceSegment::text)
                .containsExactly("Hello there.", "General Kenobi.");
        assertThat(segments.get(0).startSeconds()).isEqualTo(0.0);
        assertThat(segments.get(0).endSeconds()).isLessThan(segments.get(1).startSeconds());
        assertThat(segments.get(1).endSeconds()).isEqualTo(6.0);
    }
}
