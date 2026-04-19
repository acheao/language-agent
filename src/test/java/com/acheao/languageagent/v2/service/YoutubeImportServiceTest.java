package com.acheao.languageagent.v2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YoutubeImportServiceTest {

    private final YoutubeImportService youtubeImportService = new YoutubeImportService(
            new ObjectMapper(),
            mock(MediaStorageService.class),
            WebClient.builder().build());

    @Test
    void parseVttLines_handlesYoutubeCueSettingsAndRollingCaptions() {
        List<String> lines = List.of(
                "WEBVTT",
                "Kind: captions",
                "Language: en",
                "",
                "00:00:00.160 --> 00:00:03.990 align:start position:0%",
                " ",
                "Have<00:00:00.400><c> you</c><00:00:00.640><c> ever</c><00:00:00.880><c> used</c><00:00:01.520><c> sentences</c><00:00:02.320><c> like</c><00:00:02.639><c> these?</c>",
                "",
                "00:00:03.990 --> 00:00:04.000 align:start position:0%",
                "Have you ever used sentences like these?",
                " ",
                "",
                "00:00:04.000 --> 00:00:07.430 align:start position:0%",
                "Have you ever used sentences like these?",
                "Probably<00:00:04.720><c> I'll</c><00:00:04.960><c> go</c><00:00:05.120><c> out</c><00:00:05.359><c> tonight.</c><00:00:06.319><c> It's</c><00:00:07.120><c> a</c>",
                "",
                "00:00:07.430 --> 00:00:07.440 align:start position:0%",
                "Probably I'll go out tonight. It's a",
                "",
                "00:00:07.440 --> 00:00:09.990 align:start position:0%",
                "Probably I'll go out tonight. It's a",
                "quite<00:00:07.839><c> big</c><00:00:08.400><c> house</c>",
                "",
                "00:00:09.990 --> 00:00:10.000 align:start position:0%",
                "quite big house",
                "",
                "00:00:10.000 --> 00:00:14.470 align:start position:0%",
                "quite big house",
                "or<00:00:10.960><c> I</c><00:00:11.200><c> ate</c><00:00:11.679><c> the</c><00:00:12.000><c> three</c><00:00:12.400><c> last</c><00:00:12.960><c> biscuits.</c><00:00:14.000><c> If</c><00:00:14.320><c> you</c>"
        );

        List<YoutubeImportService.TranscriptSegment> segments = youtubeImportService.parseVttLines(lines);

        assertThat(segments)
                .extracting(YoutubeImportService.TranscriptSegment::text)
                .containsExactly(
                        "Have you ever used sentences like these?",
                        "Probably I'll go out tonight. It's a",
                        "quite big house",
                        "or I ate the three last biscuits. If you");
        assertThat(segments)
                .extracting(YoutubeImportService.TranscriptSegment::startSeconds)
                .containsExactly(0.16d, 4.0d, 7.44d, 10.0d);
    }

    @Test
    void parseVttLines_keepsStandardCaptions() {
        List<String> lines = List.of(
                "WEBVTT",
                "",
                "1",
                "00:00:00.000 --> 00:00:01.000",
                "Hello there",
                "",
                "2",
                "00:00:01.200 --> 00:00:02.200",
                "General Kenobi"
        );

        List<YoutubeImportService.TranscriptSegment> segments = youtubeImportService.parseVttLines(lines);

        assertThat(segments)
                .extracting(YoutubeImportService.TranscriptSegment::text)
                .containsExactly("Hello there", "General Kenobi");
    }
}
