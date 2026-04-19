package com.acheao.languageagent.v2.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import com.acheao.languageagent.v2.entity.Lesson;
import com.acheao.languageagent.v2.entity.SourceItem;
import com.acheao.languageagent.v2.entity.StudyUnit;
import com.acheao.languageagent.v2.repository.LessonRepository;
import com.acheao.languageagent.v2.repository.SourceItemRepository;
import com.acheao.languageagent.v2.repository.StudyUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class LessonWorkflowService {

    private final SourceItemRepository sourceItemRepository;
    private final LessonRepository lessonRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final YoutubeImportService youtubeImportService;
    private final MediaStorageService mediaStorageService;
    private final ObjectMapper objectMapper;

    public LessonWorkflowService(
            SourceItemRepository sourceItemRepository,
            LessonRepository lessonRepository,
            StudyUnitRepository studyUnitRepository,
            YoutubeImportService youtubeImportService,
            MediaStorageService mediaStorageService,
            ObjectMapper objectMapper) {
        this.sourceItemRepository = sourceItemRepository;
        this.lessonRepository = lessonRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.youtubeImportService = youtubeImportService;
        this.mediaStorageService = mediaStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LessonSummaryView importText(User user, TextImportRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Text content cannot be blank");
        }
        String normalizedText = normalizeText(request.text());
        String title = request.title() == null || request.title().isBlank()
                ? buildTitle(normalizedText)
                : request.title().trim();
        List<SegmentSeed> segments = splitIntoSegments(normalizedText);
        return persistLesson(user, "TEXT", title, null, normalizedText, request.notes(), null, segments);
    }

    @Transactional
    public LessonSummaryView importArticle(User user, ArticleImportRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Article URL cannot be blank");
        }
        try {
            Document document = Jsoup.connect(request.url().trim())
                    .userAgent("Mozilla/5.0")
                    .timeout(20000)
                    .get();
            String title = request.title() == null || request.title().isBlank()
                    ? document.title()
                    : request.title().trim();
            String articleText = extractArticleText(document);
            List<SegmentSeed> segments = splitIntoSegments(articleText);
            return persistLesson(user, "ARTICLE", title, request.url().trim(), articleText, request.notes(), null, segments);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to import article: " + e.getMessage());
        }
    }

    @Transactional
    public LessonSummaryView startYoutubeImport(User user, YoutubeImportRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "YouTube URL cannot be blank");
        }

        SourceItem sourceItem = new SourceItem();
        sourceItem.setUser(user);
        sourceItem.setType("YOUTUBE");
        sourceItem.setTitle("Importing YouTube lesson");
        sourceItem.setSourceUrl(request.url().trim());
        sourceItem.setImportStatus("PROCESSING");
        sourceItem = sourceItemRepository.save(sourceItem);

        Lesson lesson = new Lesson();
        lesson.setUser(user);
        lesson.setSourceItem(sourceItem);
        lesson.setTitle("Importing YouTube lesson");
        lesson.setSummary("Fetching English subtitles and audio in the background.");
        lesson.setStatus("PROCESSING");
        lesson.setEstimatedMinutes(30);
        lesson = lessonRepository.save(lesson);

        return toLessonSummary(lesson, 0);
    }

    @Transactional
    public void completeYoutubeImport(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
        SourceItem sourceItem = lesson.getSourceItem();
        String sourceUrl = sourceItem.getSourceUrl();

        try {
            YoutubeImportService.ImportedYoutubeLesson imported = youtubeImportService.importVideo(sourceUrl, lesson.getId());
            List<SegmentSeed> segments = TimedSentenceSegmenter.segment(imported.segments()).stream()
                    .map(segment -> new SegmentSeed(segment.orderIndex(), segment.text(), segment.startSeconds(), segment.endSeconds()))
                    .toList();

            sourceItem.setTitle(imported.title());
            sourceItem.setImportStatus("READY");
            sourceItem.setRawContent(imported.description());
            sourceItem.setMetadataJson(writeJsonSafe(imported));
            sourceItemRepository.save(sourceItem);

            lesson.setTitle(imported.title());
            lesson.setSummary(buildSummary(imported.description(), segments));
            lesson.setMediaType(imported.mediaRelativePath() == null ? null : "audio");
            lesson.setMediaRelativePath(imported.mediaRelativePath());
            lesson.setTranscriptJson(writeJsonSafe(segments));
            lesson.setStatus("READY");
            lesson.setEstimatedMinutes(Math.max(10, Math.min(45, segments.size() * 2)));
            lessonRepository.save(lesson);

            saveStudyUnits(lesson.getUser(), lesson, segments);
        } catch (Exception e) {
            sourceItem.setImportStatus("FAILED");
            sourceItem.setMetadataJson(writeJsonSafe(new SourceMetadata("youtube-import-error: " + shrinkErrorMessage(e.getMessage()))));
            lesson.setStatus("FAILED");
            lesson.setSummary("Import failed: " + shrinkErrorMessage(e.getMessage()));
            sourceItemRepository.save(sourceItem);
            lessonRepository.save(lesson);
        }
    }

    public List<LessonSummaryView> listLessons(User user) {
        return lessonRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(lesson -> toLessonSummary(lesson, studyUnitRepository.findAllByLessonOrderByOrderIndexAsc(lesson).size()))
                .toList();
    }

    public LessonDetailView getLessonDetail(User user, UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdAndUser(lessonId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
        List<StudyUnitView> units = studyUnitRepository.findAllByLessonOrderByOrderIndexAsc(lesson).stream()
                .map(this::toStudyUnitView)
                .toList();
        return new LessonDetailView(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getSourceItem().getType(),
                lesson.getSourceItem().getSourceUrl(),
                lesson.getMediaType(),
                lesson.getMediaRelativePath() == null ? null : "/api/lessons/" + lesson.getId() + "/media",
                lesson.getEstimatedMinutes(),
                lesson.getStatus(),
                units);
    }

    public StudyUnitView updateStudyUnit(User user, UUID studyUnitId, UpdateStudyUnitRequest request) {
        StudyUnit unit = studyUnitRepository.findByIdAndUser(studyUnitId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Study unit not found"));
        if (request.favorite() != null) {
            unit.setFavorite(request.favorite());
        }
        if (request.ignored() != null) {
            unit.setIgnored(request.ignored());
        }
        if (request.inPracticePool() != null) {
            unit.setInPracticePool(request.inPracticePool());
        }
        if (request.difficulty() != null) {
            unit.setDifficulty(Math.max(1, Math.min(5, request.difficulty())));
        }
        studyUnitRepository.save(unit);
        return toStudyUnitView(unit);
    }

    public Path resolveMediaPath(User user, UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdAndUser(lessonId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
        if (lesson.getMediaRelativePath() == null || lesson.getMediaRelativePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Lesson does not have downloadable media");
        }
        Path path = mediaStorageService.resolveRelativePath(lesson.getMediaRelativePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Lesson media file not found");
        }
        return path;
    }

    private LessonSummaryView persistLesson(
            User user,
            String sourceType,
            String title,
            String sourceUrl,
            String rawContent,
            String notes,
            String mediaRelativePath,
            List<SegmentSeed> segments) {
        SourceItem sourceItem = new SourceItem();
        sourceItem.setUser(user);
        sourceItem.setType(sourceType);
        sourceItem.setTitle(title);
        sourceItem.setSourceUrl(sourceUrl);
        sourceItem.setImportStatus("READY");
        sourceItem.setRawContent(rawContent);
        sourceItem.setMetadataJson(writeJsonSafe(new SourceMetadata(notes)));
        sourceItem = sourceItemRepository.save(sourceItem);

        Lesson lesson = new Lesson();
        lesson.setUser(user);
        lesson.setSourceItem(sourceItem);
        lesson.setTitle(title);
        lesson.setSummary(buildSummary(rawContent, segments));
        lesson.setMediaType(mediaRelativePath == null ? null : "audio");
        lesson.setMediaRelativePath(mediaRelativePath);
        lesson.setTranscriptJson(writeJsonSafe(segments));
        lesson.setStatus("READY");
        lesson.setEstimatedMinutes(Math.max(10, Math.min(45, segments.size() * 2)));
        lesson = lessonRepository.save(lesson);

        saveStudyUnits(user, lesson, segments);
        return toLessonSummary(lesson, segments.size());
    }

    private void saveStudyUnits(User user, Lesson lesson, List<SegmentSeed> segments) {
        List<StudyUnit> units = new ArrayList<>();
        for (SegmentSeed segment : segments) {
            StudyUnit unit = new StudyUnit();
            unit.setUser(user);
            unit.setLesson(lesson);
            unit.setOrderIndex(segment.orderIndex());
            unit.setOriginalText(segment.text());
            unit.setContextText(lesson.getTitle());
            unit.setStartSeconds(segment.startSeconds());
            unit.setEndSeconds(segment.endSeconds());
            unit.setNextReviewAt(LocalDateTime.now());
            units.add(unit);
        }
        studyUnitRepository.saveAll(units);
    }

    private LessonSummaryView toLessonSummary(Lesson lesson, int unitCount) {
        return new LessonSummaryView(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getSourceItem().getType(),
                lesson.getSourceItem().getSourceUrl(),
                lesson.getMediaType(),
                lesson.getStatus(),
                unitCount,
                lesson.getEstimatedMinutes(),
                lesson.getCreatedAt());
    }

    private StudyUnitView toStudyUnitView(StudyUnit unit) {
        return new StudyUnitView(
                unit.getId(),
                unit.getOrderIndex(),
                unit.getOriginalText(),
                unit.getTranslationZh(),
                unit.getStartSeconds(),
                unit.getEndSeconds(),
                unit.isFavorite(),
                unit.isIgnored(),
                unit.isInPracticePool(),
                unit.getMasteryScore(),
                unit.getAttempts(),
                unit.getDifficulty());
    }

    private String extractArticleText(Document document) {
        StringBuilder builder = new StringBuilder();
        document.select("article p, main p, p").stream()
                .map(element -> element.text().trim())
                .filter(text -> text.length() > 30)
                .limit(60)
                .forEach(text -> builder.append(text).append("\n\n"));

        String candidate = builder.toString().trim();
        if (!candidate.isBlank()) {
            return normalizeText(candidate);
        }
        return normalizeText(document.body().text());
    }

    private List<SegmentSeed> splitIntoSegments(String rawText) {
        String normalized = normalizeText(rawText);
        List<SegmentSeed> segments = new ArrayList<>();
        String[] paragraphs = normalized.split("\\n{2,}");
        int index = 0;
        for (String paragraph : paragraphs) {
            String trimmedParagraph = paragraph.trim();
            if (trimmedParagraph.isBlank()) {
                continue;
            }

            String[] sentences = trimmedParagraph.split("(?<=[.!?])\\s+");
            if (sentences.length == 1) {
                segments.add(new SegmentSeed(index++, trimmedParagraph, null, null));
                continue;
            }
            for (String sentence : sentences) {
                String trimmedSentence = sentence.trim();
                if (!trimmedSentence.isBlank()) {
                    segments.add(new SegmentSeed(index++, trimmedSentence, null, null));
                }
            }
        }
        if (segments.isEmpty()) {
            segments.add(new SegmentSeed(0, normalized, null, null));
        }
        return segments;
    }

    private String buildTitle(String text) {
        String firstLine = text.split("\\n")[0].trim();
        if (firstLine.length() <= 60) {
            return firstLine;
        }
        return firstLine.substring(0, 60).trim() + "...";
    }

    private String buildSummary(String rawContent, List<SegmentSeed> segments) {
        if (rawContent == null || rawContent.isBlank()) {
            return "A learner-curated lesson ready for focused daily practice.";
        }
        String compact = rawContent.replaceAll("\\s+", " ").trim();
        if (compact.length() > 220) {
            compact = compact.substring(0, 220).trim() + "...";
        }
        return compact + " (" + segments.size() + " study units)";
    }

    private String normalizeText(String input) {
        return input == null ? "" : input.replace("\r", "\n").replaceAll("[ \t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
    }

    private String writeJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String shrinkErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180).trim() + "..." : compact;
    }

    private record SegmentSeed(int orderIndex, String text, Double startSeconds, Double endSeconds) {
    }

    private record SourceMetadata(String notes) {
    }

    public record TextImportRequest(String title, String text, String notes) {
    }

    public record ArticleImportRequest(String title, String url, String notes) {
    }

    public record YoutubeImportRequest(String url) {
    }

    public record UpdateStudyUnitRequest(Boolean favorite, Boolean ignored, Boolean inPracticePool, Integer difficulty) {
    }

    public record LessonSummaryView(
            UUID id,
            String title,
            String summary,
            String sourceType,
            String sourceUrl,
            String mediaType,
            String status,
            int unitCount,
            int estimatedMinutes,
            LocalDateTime createdAt) {
    }

    public record StudyUnitView(
            UUID id,
            int orderIndex,
            String originalText,
            String translationZh,
            Double startSeconds,
            Double endSeconds,
            boolean favorite,
            boolean ignored,
            boolean inPracticePool,
            int masteryScore,
            int attempts,
            int difficulty) {
    }

    public record LessonDetailView(
            UUID id,
            String title,
            String summary,
            String sourceType,
            String sourceUrl,
            String mediaType,
            String mediaUrl,
            int estimatedMinutes,
            String status,
            List<StudyUnitView> studyUnits) {
    }
}
