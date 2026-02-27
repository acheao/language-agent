package com.acheao.languageagent.service;

import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.entity.Session;
import com.acheao.languageagent.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SchedulerService schedulerService;
    private final QuestionService questionService;

    public SessionService(SessionRepository sessionRepository, SchedulerService schedulerService,
            QuestionService questionService) {
        this.sessionRepository = sessionRepository;
        this.schedulerService = schedulerService;
        this.questionService = questionService;
    }

    public SessionResult createSessionAndQuestions(int batchSize, String generatorMode) {
        Session session = new Session();
        session.setBatchSize(batchSize);
        session.setGeneratorMode(generatorMode);
        Session savedSession = sessionRepository.save(session);

        List<Material> materials = schedulerService.pickMaterials(batchSize);
        List<Question> questions = questionService.generateQuestions(savedSession.getId(), materials, generatorMode);

        return new SessionResult(savedSession, questions);
    }

    public static class SessionResult {
        private final Session session;
        private final List<Question> questions;

        public SessionResult(Session session, List<Question> questions) {
            this.session = session;
            this.questions = questions;
        }

        public Session getSession() {
            return session;
        }

        public List<Question> getQuestions() {
            return questions;
        }
    }
}
