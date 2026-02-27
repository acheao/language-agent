package com.acheao.languageagent.service;

import com.acheao.languageagent.dto.AnswerSubmitRequest;
import com.acheao.languageagent.entity.Answer;
import com.acheao.languageagent.entity.Grading;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.exception.ResourceNotFoundException;
import com.acheao.languageagent.repository.AnswerRepository;
import com.acheao.languageagent.repository.QuestionRepository;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final GradingService gradingService;

    public AnswerService(AnswerRepository answerRepository, QuestionRepository questionRepository,
            GradingService gradingService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.gradingService = gradingService;
    }

    public Grading submitAnswer(AnswerSubmitRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        Answer answer = new Answer();
        answer.setQuestionId(question.getId());
        answer.setUserAnswer(request.getUserAnswer());
        answer.setTimeSpentMs(request.getTimeSpentMs());

        Answer savedAnswer = answerRepository.save(answer);

        return gradingService.grade(question, savedAnswer);
    }
}
