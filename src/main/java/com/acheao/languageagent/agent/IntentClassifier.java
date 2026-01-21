package com.acheao.languageagent.agent;

import com.acheao.languageagent.agent.model.IntentResult;

public interface IntentClassifier {

    IntentResult classify(String userInput);

}
