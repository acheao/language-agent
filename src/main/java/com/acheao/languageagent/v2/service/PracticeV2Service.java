package com.acheao.languageagent.v2.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import com.acheao.languageagent.v2.entity.BehaviorEvent;
import com.acheao.languageagent.v2.entity.DailyPlan;
import com.acheao.languageagent.v2.entity.PracticeSession;
import com.acheao.languageagent.v2.entity.PracticeSubmission;
import com.acheao.languageagent.v2.entity.PracticeTask;
import com.acheao.languageagent.v2.entity.StudyUnit;
import com.acheao.languageagent.v2.repository.BehaviorEventRepository;
import com.acheao.languageagent.v2.repository.DailyPlanRepository;
import com.acheao.languageagent.v2.repository.PracticeSessionV2Repository;
import com.acheao.languageagent.v2.repository.PracticeSubmissionRepository;
import com.acheao.languageagent.v2.repository.PracticeTaskRepository;
import com.acheao.languageagent.v2.repository.StudyUnitRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PracticeV2Service {

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z']+");

    private final DailyPlanService dailyPlanService;
    private final DailyPlanRepository dailyPlanRepository;
    private final PracticeSessionV2Repository sessionRepository;
    private final PracticeTaskRepository taskRepository;
    private final PracticeSubmissionRepository submissionRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final UserLlmConfigService userLlmConfigService;
    private final GenericLlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public PracticeV2Service(
            DailyPlanService dailyPlanService,
            DailyPlanRepository dailyPlanRepository,
            PracticeSessionV2Repository sessionRepository,
            PracticeTaskRepository taskRepository,
            PracticeSubmissionRepository submissionRepository,
            StudyUnitRepository studyUnitRepository,
            BehaviorEventRepository behaviorEventRepository,
            UserLlmConfigService userLlmConfigService,
            GenericLlmGateway llmGateway,
            ObjectMapper objectMapper) {
        this.dailyPlanService = dailyPlanService;
        this.dailyPlanRepository = dailyPlanRepository;
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.behaviorEventRepository = behaviorEventRepository;
        this.userLlmConfigService = userLlmConfigService;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionView startSession(User user, StartSessionRequest request) {
        String mode = request.mode() == null || request.mode().isBlank() ? "DAILY" : request.mode().trim().toUpperCase(Locale.ROOT);
        int desiredTaskCount = request.desiredTaskCount() == null ? Math.max(6, defaultMinutes(user) / 3) : Math.max(4, request.desiredTaskCount());

        DailyPlan dailyPlan = null;
        List<DailyPlanService.SelectedStudyUnit> selectedUnits;
        String focusSummary;
        if ("EXTRA".equals(mode)) {
            selectedUnits = dailyPlanService.selectExtraUnits(user, desiredTaskCount);
            focusSummary = "Extra challenge flow built from your current weak spots and untapped materials.";
        } else {
            dailyPlan = dailyPlanService.getOrCreateEntity(user);
            selectedUnits = dailyPlanService.selectDailyUnits(user, desiredTaskCount);
            focusSummary = dailyPlan.getFocusSummary();
        }

        if (selectedUnits.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No practice-ready study units found. Import materials first.");
        }

        PracticeSession session = new PracticeSession();
        session.setUser(user);
        session.setDailyPlan(dailyPlan);
        session.setMode(mode);
        session.setStatus("ACTIVE");
        session.setFocusSummary(focusSummary);
        session.setEstimatedMinutes(mode.equals("EXTRA") ? Math.max(10, desiredTaskCount * 3) : defaultMinutes(user));
        session.setStartedAt(LocalDateTime.now());
        session.setCurrentIndex(0);
        session.setCompletedTasks(0);
        session.setAverageScore(0.0);
        session = sessionRepository.save(session);

        List<PracticeTask> tasks = buildTasks(session, selectedUnits);
        session.setTotalTasks(tasks.size());
        sessionRepository.save(session);
        taskRepository.saveAll(tasks);

        createBehaviorEvent(user, session, null, "session_started", 0, Map.of("mode", mode, "taskCount", tasks.size()));
        return buildSessionView(session, tasks);
    }

    @Transactional(readOnly = true)
    public SessionView getSession(User user, UUID sessionId) {
        PracticeSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Practice session not found"));
        return buildSessionView(session, taskRepository.findAllByPracticeSessionOrderByOrderIndexAsc(session));
    }

    @Transactional
    public AnswerResult submitAnswer(User user, SubmitAnswerRequest request) {
        PracticeTask task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Practice task not found"));
        if (!task.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Practice task not found");
        }
        if ("ANSWERED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "This task has already been answered");
        }

        Evaluation evaluation = evaluateTask(user, task, request);
        PracticeSubmission submission = new PracticeSubmission();
        submission.setUser(user);
        submission.setPracticeTask(task);
        submission.setAnswerText(request.answerText());
        submission.setDurationSeconds(request.durationSeconds() == null ? 0 : Math.max(0, request.durationSeconds()));
        submission.setUsedHint(request.usedHint());
        submission.setSkipped(request.skipped());
        submission.setUncertain(request.uncertain());
        submission.setScore(evaluation.score());
        submission.setCorrect(evaluation.correct());
        submission.setShortFeedback(evaluation.shortFeedback());
        submission.setDetailedFeedback(evaluation.detailedFeedback());
        submission.setSuggestion(evaluation.suggestion());
        submission.setErrorTypesJson(writeJson(evaluation.errorTypes()));
        submission = submissionRepository.save(submission);

        task.setStatus("ANSWERED");
        taskRepository.save(task);

        updateStudyUnit(task.getStudyUnit(), submission, evaluation);
        updateSession(task.getPracticeSession(), evaluation.score());

        createBehaviorEvent(user, task.getPracticeSession(), task, "answer_submitted",
                submission.getDurationSeconds(),
                Map.of(
                        "score", evaluation.score(),
                        "usedHint", request.usedHint(),
                        "skipped", request.skipped(),
                        "uncertain", request.uncertain(),
                        "errorTypes", evaluation.errorTypes()));

        PracticeSession session = task.getPracticeSession();
        List<PracticeTask> tasks = taskRepository.findAllByPracticeSessionOrderByOrderIndexAsc(session);
        PracticeTask nextTask = tasks.stream()
                .filter(item -> "READY".equals(item.getStatus()))
                .min(Comparator.comparing(PracticeTask::getOrderIndex))
                .orElse(null);

        if (nextTask == null) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            if (session.getDailyPlan() != null) {
                DailyPlan plan = session.getDailyPlan();
                plan.setStatus("COMPLETED");
                plan.setCompletedAt(LocalDateTime.now());
                dailyPlanRepository.save(plan);
            }
        }
        sessionRepository.save(session);

        return new AnswerResult(
                new SubmissionView(
                        submission.getId(),
                        submission.getScore(),
                        submission.isCorrect(),
                        submission.getShortFeedback(),
                        submission.getDetailedFeedback(),
                        submission.getSuggestion(),
                        parseErrorTypes(submission.getErrorTypesJson()),
                        nextTask == null,
                        nextTask == null ? null : toTaskView(nextTask)),
                buildSessionView(session, tasks));
    }

    private List<PracticeTask> buildTasks(PracticeSession session, List<DailyPlanService.SelectedStudyUnit> selectedUnits) {
        List<PracticeTask> tasks = new ArrayList<>();
        int orderIndex = 0;
        for (DailyPlanService.SelectedStudyUnit item : selectedUnits) {
            StudyUnit unit = item.studyUnit();
            GeneratedTask generatedTask = generateTask(unit, orderIndex);
            PracticeTask task = new PracticeTask();
            task.setUser(session.getUser());
            task.setPracticeSession(session);
            task.setStudyUnit(unit);
            task.setOrderIndex(orderIndex++);
            task.setTaskType(generatedTask.taskType());
            task.setPrompt(generatedTask.prompt());
            task.setReferenceAnswer(generatedTask.referenceAnswer());
            task.setHintText(generatedTask.hintText());
            task.setShortContext(generatedTask.shortContext());
            task.setDifficulty(Math.max(1, Math.min(5, unit.getDifficulty())));
            task.setPlanBucket(item.bucket());
            task.setStatus("READY");
            tasks.add(task);
        }
        return tasks;
    }

    private GeneratedTask generateTask(StudyUnit unit, int orderIndex) {
        String original = unit.getOriginalText();
        String taskType = chooseTaskType(unit, orderIndex);

        return switch (taskType) {
            case "listen_transcribe" -> new GeneratedTask(
                    "listen_transcribe",
                    "Listen to the highlighted clip and type the original sentence as accurately as you can.",
                    original,
                    firstNonBlank(unit.getTranslationZh(), "Use the clip timing and the lesson title as your cue."),
                    unit.getLesson().getTitle());
            case "fill_blank" -> {
                String blankWord = chooseBlankWord(original);
                String prompt = original.replaceFirst("(?i)\\b" + Pattern.quote(blankWord) + "\\b", "_____");
                yield new GeneratedTask(
                        "fill_blank",
                        "Fill in the missing word: " + prompt,
                        blankWord,
                        "Focus on the collocation, word choice, and sentence rhythm.",
                        unit.getLesson().getTitle());
            }
            case "rebuild_sentence" -> new GeneratedTask(
                    "rebuild_sentence",
                    "Rebuild the sentence from memory. Keywords: " + keywordCue(original),
                    original,
                    firstNonBlank(unit.getTranslationZh(), "Try to preserve the grammar, tense, and exact wording."),
                    unit.getLesson().getTitle());
            default -> new GeneratedTask(
                    "meaning_recall",
                    "Use the context cue to recall and write the original English sentence.",
                    original,
                    firstNonBlank(unit.getTranslationZh(),
                            "Lean on the lesson title and the core meaning, then recall the source sentence."),
                    unit.getLesson().getTitle());
        };
    }

    private String chooseTaskType(StudyUnit unit, int orderIndex) {
        boolean youtubeWithClip = "YOUTUBE".equalsIgnoreCase(unit.getLesson().getSourceItem().getType())
                && unit.getStartSeconds() != null
                && unit.getEndSeconds() != null;
        String lastErrorType = ErrorTypeCatalog.normalize(unit.getLastErrorType());

        if (youtubeWithClip
                && ("listening_accuracy".equals(lastErrorType)
                        || unit.getAverageDurationSeconds() > 40
                        || orderIndex % 4 == 0)) {
            return "listen_transcribe";
        }

        if ("word_choice".equals(lastErrorType)
                || "collocation".equals(lastErrorType)
                || "spelling".equals(lastErrorType)
                || orderIndex % 4 == 0) {
            return "fill_blank";
        }

        if ("grammar".equals(lastErrorType)
                || "tense".equals(lastErrorType)
                || "article_preposition".equals(lastErrorType)
                || "punctuation".equals(lastErrorType)
                || orderIndex % 4 == 1) {
            return "rebuild_sentence";
        }

        if ("completeness".equals(lastErrorType) || orderIndex % 4 == 2) {
            return "meaning_recall";
        }

        return youtubeWithClip ? "listen_transcribe" : "meaning_recall";
    }

    private Evaluation evaluateTask(User user, PracticeTask task, SubmitAnswerRequest request) {
        if (request.skipped()) {
            return new Evaluation(
                    0,
                    false,
                    "Skipped for now. We will recycle this sentence soon.",
                    "Skipping is recorded as a low-confidence signal, so this unit will come back earlier.",
                    "Use the hint or replay the clip next time before skipping.",
                    List.of(defaultErrorTypeForTask(task.getTaskType())));
        }

        Optional<GenericLlmGateway.RuntimeConfig> runtime = userLlmConfigService.findDefaultRuntime(user);
        if (runtime.isPresent()) {
            try {
                return evaluateWithLlm(runtime.get(), task, request.answerText());
            } catch (Exception ignored) {
                // Fall back to deterministic local scoring below.
            }
        }
        return evaluateLocally(task, request.answerText(), request.usedHint(), request.uncertain());
    }

    private Evaluation evaluateWithLlm(GenericLlmGateway.RuntimeConfig runtime, PracticeTask task, String answerText) {
        JsonNode response = llmGateway.chatJson(
                runtime,
                """
                        You are grading a focused English practice task.
                        Return JSON only with this schema:
                        {
                          "score": 0,
                          "correct": true,
                          "shortFeedback": "short feedback",
                          "detailedFeedback": "one concise paragraph",
                          "suggestion": "one next-step suggestion",
                          "errorTypes": ["grammar"]
                        }
                        Score must be 0-100.
                        Keep feedback short and practical.
                        """,
                "Task type: " + task.getTaskType()
                        + "\nPrompt: " + task.getPrompt()
                        + "\nReference answer: " + task.getReferenceAnswer()
                        + "\nUser answer: " + answerText);

        int score = response.path("score").asInt(0);
        boolean correct = response.path("correct").asBoolean(score >= 85);
        List<String> errorTypes = new ArrayList<>();
        if (response.path("errorTypes").isArray()) {
            for (JsonNode item : response.path("errorTypes")) {
                errorTypes.add(item.asText());
            }
        }
        errorTypes = ErrorTypeCatalog.normalizeAll(errorTypes);
        if (errorTypes.isEmpty() && !correct) {
            errorTypes = List.of(defaultErrorTypeForTask(task.getTaskType()));
        }
        return new Evaluation(
                Math.max(0, Math.min(100, score)),
                correct,
                response.path("shortFeedback").asText("Good effort. Keep going."),
                response.path("detailedFeedback").asText("The response was reviewed against the target sentence."),
                response.path("suggestion").asText("Try one more repetition with full attention to wording."),
                errorTypes);
    }

    private Evaluation evaluateLocally(PracticeTask task, String answerText, boolean usedHint, boolean uncertain) {
        String reference = task.getReferenceAnswer() == null ? "" : task.getReferenceAnswer();
        String normalizedReference = normalize(reference);
        String normalizedAnswer = normalize(answerText == null ? "" : answerText);

        if (normalizedAnswer.isBlank()) {
            return new Evaluation(
                    0,
                    false,
                    "No answer was submitted.",
                    "Empty answers are treated as misses so the sentence can come back earlier in your next plan.",
                    "Try to produce even a partial answer next time so the system can see where the gap is.",
                    List.of(defaultErrorTypeForTask(task.getTaskType())));
        }

        double similarity = similarity(normalizedReference, normalizedAnswer);
        int score = (int) Math.round(similarity * 100);
        if (usedHint) {
            score = Math.max(0, score - 5);
        }
        if (uncertain) {
            score = Math.max(0, score - 3);
        }

        boolean correct = score >= 85;
        List<String> errorTypes = guessErrorTypes(reference, answerText == null ? "" : answerText, task.getTaskType());
        String shortFeedback = correct
                ? "Clean answer. This one is close to mastered."
                : score >= 60
                        ? "Close, but a few wording details still slipped."
                        : "This one needs another round soon.";
        String detailedFeedback = correct
                ? "Your response stayed very close to the target form and should strengthen retrieval speed."
                : "The answer was compared directly with the target text. Focus on missing words, exact word order, and grammar endings.";
        String suggestion = switch (task.getTaskType()) {
            case "fill_blank" -> "Repeat the full sentence aloud once after filling the blank.";
            case "listen_transcribe" -> "Replay the clip and shadow it one more time before moving on.";
            case "meaning_recall" -> "Summarize the meaning once in Chinese, then rewrite the source sentence in English.";
            default -> "Rewrite the target sentence once more with full punctuation.";
        };

        return new Evaluation(score, correct, shortFeedback, detailedFeedback, suggestion, errorTypes);
    }

    private void updateStudyUnit(StudyUnit unit, PracticeSubmission submission, Evaluation evaluation) {
        int attempts = unit.getAttempts() + 1;
        unit.setAttempts(attempts);
        if (submission.isSkipped()) {
            unit.setSkipCount(unit.getSkipCount() + 1);
        }
        if (submission.isUncertain()) {
            unit.setUncertainCount(unit.getUncertainCount() + 1);
        }
        unit.setAverageScore(weightedAverage(unit.getAverageScore(), attempts - 1, evaluation.score()));
        unit.setAverageDurationSeconds(weightedAverage(unit.getAverageDurationSeconds(), attempts - 1, submission.getDurationSeconds()));
        unit.setMasteryScore((int) Math.round(weightedAverage(unit.getMasteryScore(), attempts - 1, evaluation.score())));
        unit.setLastPracticedAt(LocalDateTime.now());
        unit.setLastErrorType(evaluation.errorTypes().isEmpty() ? null : ErrorTypeCatalog.normalize(evaluation.errorTypes().getFirst()));
        unit.setNextReviewAt(nextReviewTime(evaluation.score(), submission.isSkipped(), submission.isUncertain()));
        studyUnitRepository.save(unit);
    }

    private void updateSession(PracticeSession session, int score) {
        int completed = session.getCompletedTasks() + 1;
        session.setCompletedTasks(completed);
        session.setCurrentIndex(completed);
        session.setAverageScore(weightedAverage(session.getAverageScore(), completed - 1, score));
        sessionRepository.save(session);
    }

    private SessionView buildSessionView(PracticeSession session, List<PracticeTask> tasks) {
        PracticeTask currentTask = tasks.stream()
                .filter(task -> "READY".equals(task.getStatus()))
                .min(Comparator.comparing(PracticeTask::getOrderIndex))
                .orElse(null);
        return new SessionView(
                session.getId(),
                session.getMode(),
                session.getStatus(),
                session.getFocusSummary(),
                session.getEstimatedMinutes(),
                new ProgressView(session.getCompletedTasks(), session.getTotalTasks(), session.getAverageScore()),
                currentTask == null ? null : toTaskView(currentTask),
                currentTask == null);
    }

    private TaskView toTaskView(PracticeTask task) {
        StudyUnit unit = task.getStudyUnit();
        return new TaskView(
                task.getId(),
                unit.getId(),
                unit.getLesson().getId(),
                task.getTaskType(),
                task.getPrompt(),
                task.getHintText(),
                task.getShortContext(),
                unit.getStartSeconds(),
                unit.getEndSeconds(),
                unit.getLesson().getMediaRelativePath() == null ? null : "/api/lessons/" + unit.getLesson().getId() + "/media");
    }

    private void createBehaviorEvent(
            User user,
            PracticeSession session,
            PracticeTask task,
            String eventType,
            int secondsSpent,
            Object payload) {
        BehaviorEvent event = new BehaviorEvent();
        event.setUser(user);
        event.setPracticeSession(session);
        event.setPracticeTask(task);
        event.setEventType(eventType);
        event.setSecondsSpent(secondsSpent);
        event.setPayloadJson(writeJson(payload));
        behaviorEventRepository.save(event);
    }

    private double weightedAverage(double currentAverage, int currentCount, double newValue) {
        if (currentCount <= 0) {
            return newValue;
        }
        return ((currentAverage * currentCount) + newValue) / (currentCount + 1);
    }

    private LocalDateTime nextReviewTime(int score, boolean skipped, boolean uncertain) {
        if (skipped || score < 40) {
            return LocalDateTime.now().plusHours(6);
        }
        if (uncertain) {
            return LocalDateTime.now().plusHours(score >= 80 ? 20 : 12);
        }
        if (score < 70) {
            return LocalDateTime.now().plusDays(1);
        }
        if (score < 85) {
            return LocalDateTime.now().plusDays(2);
        }
        return LocalDateTime.now().plusDays(5);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s']", " ").replaceAll("\\s+", " ").trim();
    }

    private double similarity(String reference, String answer) {
        if (reference.equals(answer)) {
            return 1.0;
        }
        if (reference.isBlank() || answer.isBlank()) {
            return 0.0;
        }
        int distance = levenshtein(reference, answer);
        int maxLength = Math.max(reference.length(), answer.length());
        return Math.max(0.0, 1.0 - ((double) distance / maxLength));
    }

    private int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[left.length()][right.length()];
    }

    private List<String> guessErrorTypes(String reference, String answer, String taskType) {
        String normalizedReference = normalize(reference);
        String normalizedAnswer = normalize(answer);

        if ("fill_blank".equals(taskType)) {
            return normalizedReference.equals(normalizedAnswer) ? List.of() : List.of("word_choice");
        }

        if ("listen_transcribe".equals(taskType)) {
            Set<String> errors = new LinkedHashSet<>();
            if (normalizedAnswer.split(" ").length < Math.max(1, normalizedReference.split(" ").length - 1)) {
                errors.add("completeness");
            }
            if (!normalizedReference.equals(normalizedAnswer)) {
                errors.add("listening_accuracy");
            }
            if (hasPunctuationDifference(reference, answer)) {
                errors.add("punctuation");
            }
            return ErrorTypeCatalog.normalizeAll(errors);
        }

        Set<String> errors = new LinkedHashSet<>();
        if (normalizedAnswer.split(" ").length < Math.max(1, normalizedReference.split(" ").length - 1)) {
            errors.add("completeness");
        }
        if (hasPunctuationDifference(reference, answer)) {
            errors.add("punctuation");
        }
        if (!normalizedReference.equals(normalizedAnswer)) {
            errors.add("word_choice");
        }
        if (levenshtein(normalizedReference, normalizedAnswer) > Math.max(3, normalizedReference.length() / 4)) {
            errors.add("grammar");
        }
        if ("meaning_recall".equals(taskType) && !normalizedReference.equals(normalizedAnswer)) {
            errors.add("completeness");
        }
        return ErrorTypeCatalog.normalizeAll(errors);
    }

    private String chooseBlankWord(String sentence) {
        Matcher matcher = WORD_PATTERN.matcher(sentence);
        String best = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            if (best == null || candidate.length() > best.length()) {
                best = candidate;
            }
        }
        return best == null ? sentence.split("\\s+")[0] : best;
    }

    private String keywordCue(String sentence) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(sentence);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        if (words.isEmpty()) {
            return sentence;
        }
        return String.join(" / ", words.stream().limit(5).toList());
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private List<String> parseErrorTypes(String json) {
        try {
            return ErrorTypeCatalog.normalizeAll(objectMapper.readValue(json, List.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean hasPunctuationDifference(String reference, String answer) {
        return punctuationSignature(reference).equals(punctuationSignature(answer)) ? false : !normalize(reference).equals(normalize(answer));
    }

    private String punctuationSignature(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("[A-Za-z0-9\\s]", "");
    }

    private String defaultErrorTypeForTask(String taskType) {
        return "listen_transcribe".equals(taskType) ? "listening_accuracy" : "completeness";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private int defaultMinutes(User user) {
        return user.getDailyGoalMinutes() == null ? 30 : Math.max(15, user.getDailyGoalMinutes());
    }

    public record StartSessionRequest(String mode, Integer desiredTaskCount) {
    }

    public record SubmitAnswerRequest(
            UUID taskId,
            String answerText,
            Integer durationSeconds,
            boolean usedHint,
            boolean skipped,
            boolean uncertain) {
    }

    public record ProgressView(int completedTasks, int totalTasks, double averageScore) {
    }

    public record TaskView(
            UUID id,
            UUID studyUnitId,
            UUID lessonId,
            String taskType,
            String prompt,
            String hintText,
            String shortContext,
            Double startSeconds,
            Double endSeconds,
            String mediaUrl) {
    }

    public record SessionView(
            UUID id,
            String mode,
            String status,
            String focusSummary,
            int estimatedMinutes,
            ProgressView progress,
            TaskView currentTask,
            boolean completed) {
    }

    public record SubmissionView(
            UUID id,
            int score,
            boolean correct,
            String shortFeedback,
            String detailedFeedback,
            String suggestion,
            List<String> errorTypes,
            boolean sessionCompleted,
            TaskView nextTask) {
    }

    public record AnswerResult(SubmissionView submission, SessionView session) {
    }

    private record GeneratedTask(String taskType, String prompt, String referenceAnswer, String hintText, String shortContext) {
    }

    private record Evaluation(
            int score,
            boolean correct,
            String shortFeedback,
            String detailedFeedback,
            String suggestion,
            List<String> errorTypes) {
    }
}
