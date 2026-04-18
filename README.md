# English Trainer Backend

This is the backend service for **English Trainer**, an AI-powered system designed to help users improve English writing skills through smart correction and personalized practice.

---

## Project Overview

The goal of this project is to build an intelligent learning platform that:

* Corrects English sentences using Large Language Models (LLMs)
* Identifies and classifies writing errors
* Tracks user progress over time
* Generates personalized exercises based on past mistakes
* Provides REST APIs for frontend applications

---

## Key Features

* Automatic grammar and writing correction
* Error type classification (grammar, tense, spelling, collocation, etc.)
* Learning history storage
* Adaptive exercise generation
* Support for multiple LLM providers (OpenAI, DeepSeek, Qwen, etc.)

---

## Tech Stack

* **Language:** Java
* **Framework:** Spring Boot
* **Database:** MySQL / PostgreSQL
* **HTTP Client:** RestTemplate / WebClient
* **Architecture:** RESTful API
* **Deployment:** Docker (optional)

---

## System Architecture

```
Frontend (React / Mobile App)
        ↓
Backend API (Spring Boot)
        ↓
LLM Provider (OpenAI / DeepSeek / Qwen)
        ↓
Database
```

---

## Core Modules

### 1. Correction Module

* Receives user-written English text
* Sends it to an LLM for evaluation
* Returns structured correction results

Example response:

```json
{
  "original": "She go to school yesterday.",
  "corrected": "She went to school yesterday.",
  "errorTypes": ["tense"]
}
```

---

### 2. Error Tracking

The system records:

* User writing history
* Frequent error types
* Repeated vocabulary issues
* Accuracy statistics
* Practice timestamps

This data is used to improve future learning efficiency.

---

### 3. Personalized Exercise Generation

New practice tasks are generated based on:

* Recent error patterns
* Vocabulary difficulty
* Spaced repetition principles
* User performance history

---

## API Examples

### Sentence Correction

**POST** `/api/correct`

Request:

```json
{
  "text": "He don't like apples."
}
```

Response:

```json
{
  "corrected": "He doesn't like apples.",
  "errors": [
    {
      "type": "grammar",
      "message": "Subject-verb agreement error"
    }
  ]
}
```

---

## Configuration

LLM provider configuration can be set in `application.yml`:

```yaml
llm:
  provider: openai
  api-key: your_api_key_here
  base-url: https://api.openai.com
```

For open-source use, do not commit a real key. Set `LLM_API_KEY` through environment variables or a local `.env` file during deployment.

You can switch between different providers without changing business logic.

---

## Getting Started

### 1. Clone the repository

```
git clone <repository-url>
cd english-trainer-backend
```

### 2. Configure the database

Update `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/english_trainer
    username: root
    password: password
```

### 3. Run the application

```
./mvnw spring-boot:run
```

The service will be available at:

```
http://localhost:8080
```

---

## Roadmap

### Completed

* Basic backend framework
* LLM integration module
* Simple correction API

### In Progress

* Error statistics module
* Personalized exercise logic
* Frontend integration

### Future Plans

* Multi-model comparison
* Offline/local model support
* Mobile app integration
* Advanced spaced repetition algorithm

---

## Contribution

Contributions, suggestions, and bug reports are welcome.

Feel free to open an issue or submit a pull request.

---
