package com.acheao.languageagent.service;

import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.repository.QuestionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<Question> generateQuestions(UUID sessionId, List<Material> materials, String mode) {
        List<Question> questions = new ArrayList<>();

        for (Material material : materials) {
            Question q = new Question();
            q.setSessionId(sessionId);
            q.setMaterialId(material.getId());
            q.setType("translation_zh_en"); // MVP: Fixed type for simplicity

            // In a real app, this logic might extract sentences or use an LLM
            q.setPrompt("请将以下内容翻译为英文: " + material.getContent());
            q.setReferenceAnswer("{\"text\": \"" + material.getContent() + "\"}");
            q.setRubric("{\"accuracy\": 0.4, \"fluency\": 0.4, \"grammar\": 0.2}");
            q.setDifficulty(3);

            questions.add(q);
        }

        return questionRepository.saveAll(questions);
    }
}
