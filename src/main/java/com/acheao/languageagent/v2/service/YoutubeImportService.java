package com.acheao.languageagent.v2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YoutubeImportService {

    private static final Pattern TIMECODE_PATTERN = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+-->\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})(?:\\s+.*)?");

    @Value("${app.ytdlp-bin:yt-dlp}")
    private String ytdlpBin;

    @Value("${app.asr-service-url:}")
    private String asrServiceUrl;

    private final ObjectMapper objectMapper;
    private final MediaStorageService mediaStorageService;
    private final WebClient webClient;

    public YoutubeImportService(ObjectMapper objectMapper, MediaStorageService mediaStorageService, WebClient webClient) {
        this.objectMapper = objectMapper;
        this.mediaStorageService = mediaStorageService;
        this.webClient = webClient;
    }

    public ImportedYoutubeLesson importVideo(String url, UUID lessonId) {
        try {
            Path lessonDir = mediaStorageService.createLessonDirectory(lessonId);
            JsonNode metadata = objectMapper.readTree(runCommand(
                    List.of(ytdlpBin, "--dump-single-json", "--skip-download", "--no-playlist", url),
                    lessonDir));

            runCommand(List.of(
                    ytdlpBin,
                    "--no-playlist",
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "--write-sub",
                    "--write-auto-sub",
                    "--sub-langs", "en.*,en",
                    "--convert-subs", "vtt",
                    "-o", "%(id)s.%(ext)s",
                    url), lessonDir);

            Path audioFile = Files.list(lessonDir)
                    .filter(path -> path.getFileName().toString().endsWith(".mp3"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst()
                    .orElse(null);

            Path subtitleFile = Files.list(lessonDir)
                    .filter(path -> path.getFileName().toString().endsWith(".vtt"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst()
                    .orElse(null);

            List<TranscriptSegment> segments = subtitleFile != null
                    ? parseVttSegments(subtitleFile)
                    : List.of();

            if (segments.isEmpty() && audioFile != null && asrServiceUrl != null && !asrServiceUrl.isBlank()) {
                segments = requestAsrSegments(audioFile);
            }

            String title = metadata.path("title").asText("YouTube Lesson");
            String description = metadata.path("description").asText("");
            String mediaRelativePath = audioFile == null ? null : mediaStorageService.relativize(audioFile);
            return new ImportedYoutubeLesson(title, description, url, mediaRelativePath, segments);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import YouTube lesson: " + e.getMessage(), e);
        }
    }

    private String runCommand(List<String> command, Path workdir) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workdir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(output.toString().trim());
        }
        return output.toString();
    }

    private List<TranscriptSegment> parseVttSegments(Path subtitleFile) throws Exception {
        return parseVttLines(Files.readAllLines(subtitleFile, StandardCharsets.UTF_8));
    }

    List<TranscriptSegment> parseVttLines(List<String> lines) {
        List<CaptionCue> cues = new ArrayList<>();
        Double start = null;
        Double end = null;
        StringBuilder text = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            Matcher matcher = TIMECODE_PATTERN.matcher(line);
            if (matcher.matches()) {
                appendCue(cues, start, end, text);
                start = parseTimestamp(matcher.group(1));
                end = parseTimestamp(matcher.group(2));
                continue;
            }

            if (line.isBlank()) {
                if (start != null && text.length() == 0) {
                    continue;
                }
                appendCue(cues, start, end, text);
                start = null;
                end = null;
                continue;
            }

            if (shouldIgnoreVttLine(line, start != null)) {
                continue;
            }

            if (start == null) {
                continue;
            }

            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(line);
        }

        appendCue(cues, start, end, text);
        return normalizeCues(cues);
    }

    private void appendCue(List<CaptionCue> cues, Double start, Double end, StringBuilder text) {
        if (start == null) {
            text.setLength(0);
            return;
        }

        String cleaned = cleanupSubtitleText(text.toString());
        text.setLength(0);
        if (cleaned.isBlank()) {
            return;
        }
        cues.add(new CaptionCue(start, end, cleaned));
    }

    private boolean shouldIgnoreVttLine(String line, boolean insideCue) {
        if (line.startsWith("WEBVTT") || line.matches("^\\d+$")) {
            return true;
        }
        if (insideCue) {
            return false;
        }
        return line.startsWith("Kind:")
                || line.startsWith("Language:")
                || line.startsWith("NOTE")
                || line.startsWith("STYLE")
                || line.startsWith("REGION");
    }

    private List<TranscriptSegment> normalizeCues(List<CaptionCue> cues) {
        List<TranscriptSegment> segments = new ArrayList<>();
        int index = 0;

        for (CaptionCue cue : cues) {
            String text = cue.text();
            if (!segments.isEmpty()) {
                TranscriptSegment previous = segments.get(segments.size() - 1);
                if (text.equals(previous.text())) {
                    continue;
                }
                if (isRollingCaptionUpdate(previous, cue)) {
                    text = text.substring(previous.text().length()).trim();
                    if (text.isBlank()) {
                        continue;
                    }
                }
                if (isEphemeralSuffixCue(previous, cue, text)) {
                    continue;
                }
            }

            segments.add(new TranscriptSegment(index++, cue.startSeconds(), cue.endSeconds(), text));
        }
        return segments;
    }

    private boolean isRollingCaptionUpdate(TranscriptSegment previous, CaptionCue current) {
        return previous.text().length() >= 8
                && current.text().startsWith(previous.text())
                && cueGap(previous.endSeconds(), current.startSeconds()) <= 0.25;
    }

    private boolean isEphemeralSuffixCue(TranscriptSegment previous, CaptionCue current, String text) {
        return cueDuration(current.startSeconds(), current.endSeconds()) <= 0.05
                && previous.text().endsWith(text);
    }

    private double cueGap(Double previousEnd, Double currentStart) {
        if (previousEnd == null || currentStart == null) {
            return Double.MAX_VALUE;
        }
        return Math.max(0d, currentStart - previousEnd);
    }

    private double cueDuration(Double start, Double end) {
        if (start == null || end == null) {
            return Double.MAX_VALUE;
        }
        return Math.max(0d, end - start);
    }

    private List<TranscriptSegment> requestAsrSegments(Path audioFile) {
        try {
            Map<?, ?> response = webClient.post()
                    .uri(asrServiceUrl)
                    .bodyValue(Map.of("audioPath", audioFile.toAbsolutePath().toString()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

            Object rawSegments = response == null ? null : response.get("segments");
            if (!(rawSegments instanceof List<?> segmentList)) {
                return List.of();
            }

            List<TranscriptSegment> segments = new ArrayList<>();
            int index = 0;
            for (Object item : segmentList) {
                if (!(item instanceof Map<?, ?> segmentMap)) {
                    continue;
                }
                segments.add(new TranscriptSegment(
                        index++,
                        toDouble(segmentMap.get("start")),
                        toDouble(segmentMap.get("end")),
                        String.valueOf(segmentMap.containsKey("text") ? segmentMap.get("text") : "").trim()));
            }
            return segments.stream().filter(segment -> !segment.text().isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private double parseTimestamp(String value) {
        String[] parts = value.split(":");
        double hours = Double.parseDouble(parts[0]);
        double minutes = Double.parseDouble(parts[1]);
        double seconds = Double.parseDouble(parts[2]);
        return hours * 3600 + minutes * 60 + seconds;
    }

    private String cleanupSubtitleText(String raw) {
        return raw.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    public record TranscriptSegment(int orderIndex, Double startSeconds, Double endSeconds, String text) {
    }

    private record CaptionCue(Double startSeconds, Double endSeconds, String text) {
    }

    public record ImportedYoutubeLesson(
            String title,
            String description,
            String sourceUrl,
            String mediaRelativePath,
            List<TranscriptSegment> segments) {
    }
}
