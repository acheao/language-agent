# Language Agent Backend

Chinese version: [README.zh-CN.md](README.zh-CN.md)

`language-agent` is the backend service for the English Trainer product. It is not just a grading API. It is the business engine behind authentication, model configuration, material ingestion, daily planning, adaptive practice, grading, and progress analytics.

The paired frontend repository lives at `C:\Users\Lin Chao\Documents\work\english-trainer-web`.

## Overview

This backend supports the following user journey:

1. Users register and sign in.
2. Users configure one or more LLM providers and choose a default model.
3. Users import YouTube URLs, article URLs, or plain text as learning materials.
4. The backend stores lessons, extracts or downloads content, and creates study units.
5. The system generates a focused daily practice plan, usually around 30 minutes.
6. The system grades answers, records error types and behavior signals, and uses that data to improve future sessions.

## Core Capabilities

### Authentication

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `PATCH /api/auth/profile`

### LLM Settings

Users can manage per-user model configurations instead of relying on a single deployment-wide API key.

- `GET /api/settings/llm`
- `GET /api/settings/llm/providers`
- `POST /api/settings/llm`
- `PATCH /api/settings/llm/{id}`
- `DELETE /api/settings/llm/{id}`
- `POST /api/settings/llm/{id}/test`

The built-in provider catalog currently includes:

- OpenAI / ChatGPT
- Codex
- DeepSeek
- Qwen
- Gemini
- Kimi
- GLM
- Grok
- MiniMax

### Material Import

- `POST /api/import/text`
- `POST /api/import/article`
- `POST /api/import/youtube`

Behavior by source type:

- YouTube import downloads English subtitles and mp3 files when available
- Article import extracts readable body text
- Text import splits learner-provided content into practice-ready study units

### Lessons and Study Units

- `GET /api/lessons`
- `GET /api/lessons/{id}`
- `GET /api/lessons/{id}/media`
- `PATCH /api/study-units/{id}`

### Daily Plan

- `GET /api/daily-plan/today`

The daily planner ranks study units using signals such as:

- due review status
- weak mastery
- new material exposure
- previous skips
- uncertainty
- recent duration and difficulty

### Practice

- `POST /api/practice/sessions`
- `GET /api/practice/sessions/{id}`
- `POST /api/practice/answers`

The backend records:

- duration
- hint usage
- skip behavior
- uncertainty
- score
- feedback
- error types

### Stats

- `GET /api/stats/overview`
- `GET /api/stats/error-types?range=7d|30d`

## Domain Model

The current business model is centered on adaptive learning rather than a static question bank:

- `Lesson`
- `StudyUnit`
- `DailyPlan`
- `PracticeSession`
- `PracticeTask`
- `PracticeSubmission`
- `BehaviorEvent`
- `UserLlmConfig`

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- PostgreSQL
- Docker / Docker Compose
- `yt-dlp` for YouTube ingestion

## Configuration

Use `.env` for deployment-specific values. See `.env.example`.

Important environment variables:

- `APP_PORT`
- `POSTGRES_PORT`
- `CORS_ALLOWED_ORIGINS`
- `JWT_SECRET`
- `SETTINGS_ENCRYPTION_KEY`
- `MEDIA_STORAGE_ROOT`
- `YTDLP_BIN`
- `ASR_SERVICE_URL`

## Run Locally

### Docker

```bash
docker compose up -d --build
```

### Maven

```bash
mvn spring-boot:run
```

Default backend URL:

```text
http://localhost:8080
```

### API documentation

When the service is running, OpenAPI docs are available at:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## Current Refactor Focus

The current refactor aims to make the backend serve a more coherent product:

- learner-owned material library
- multi-provider model configuration
- 30-minute daily adaptive practice
- better persistence of errors, time, hesitation, and skip signals
- tighter contract with the frontend flow
