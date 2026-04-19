# Language Agent Backend

English version: [README.md](README.md)

`language-agent` 是 English Trainer 产品的后端服务。它不只是一个评分 API，而是承载身份认证、模型配置、材料导入、每日学习计划、自适应练习、评分与进度分析的业务引擎。

配套前端仓库位于 `C:\Users\Lin Chao\Documents\work\english-trainer-web`。

## 项目定位

这个后端支持如下用户学习路径：

1. 用户注册并登录。
2. 用户配置一个或多个 LLM 提供商，并选择默认模型。
3. 用户通过 YouTube 链接、文章链接或纯文本导入学习材料。
4. 后端保存 lesson，提取或下载内容，并生成 study units。
5. 系统生成以 30 分钟左右为目标的每日练习计划。
6. 系统为用户答案打分，记录错误类型与行为信号，并将这些数据用于后续学习优化。

## 核心能力

### 认证

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `PATCH /api/auth/profile`

### LLM 设置

用户可以维护按账号隔离的模型配置，而不是依赖单一部署级 API Key。

- `GET /api/settings/llm`
- `GET /api/settings/llm/providers`
- `POST /api/settings/llm`
- `PATCH /api/settings/llm/{id}`
- `DELETE /api/settings/llm/{id}`
- `POST /api/settings/llm/{id}/test`

当前内置提供商目录包括：

- OpenAI / ChatGPT
- Codex
- DeepSeek
- Qwen
- Gemini
- Kimi
- GLM
- Grok
- MiniMax

### 材料导入

- `POST /api/import/text`
- `POST /api/import/article`
- `POST /api/import/youtube`

不同来源的处理逻辑：

- YouTube 导入会在可用时下载英文字幕和 mp3
- 文章导入会提取可阅读正文
- 文本导入会把用户提供的内容拆成可练习的 study units

### Lessons 与 Study Units

- `GET /api/lessons`
- `GET /api/lessons/{id}`
- `GET /api/lessons/{id}/media`
- `PATCH /api/study-units/{id}`

### 每日计划

- `GET /api/daily-plan/today`

每日规划器会综合以下信号给 study units 排序：

- 到期复习状态
- 掌握薄弱程度
- 新材料曝光
- 历史跳过行为
- 不确定性
- 最近耗时与难度

### 练习

- `POST /api/practice/sessions`
- `GET /api/practice/sessions/{id}`
- `POST /api/practice/answers`

后端会记录：

- 耗时
- 提示使用情况
- 跳过行为
- 不确定性
- 分数
- 反馈
- 错误类型

### 统计

- `GET /api/stats/overview`
- `GET /api/stats/error-types?range=7d|30d`

## 领域模型

当前业务模型以“自适应学习”而不是静态题库为中心：

- `Lesson`
- `StudyUnit`
- `DailyPlan`
- `PracticeSession`
- `PracticeTask`
- `PracticeSubmission`
- `BehaviorEvent`
- `UserLlmConfig`

## 技术栈

- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- PostgreSQL
- Docker / Docker Compose
- `yt-dlp` 用于 YouTube 导入

## 配置说明

部署时使用 `.env` 存放环境差异配置，示例见 `.env.example`。

关键环境变量包括：

- `APP_PORT`
- `POSTGRES_PORT`
- `CORS_ALLOWED_ORIGINS`
- `JWT_SECRET`
- `SETTINGS_ENCRYPTION_KEY`
- `MEDIA_STORAGE_ROOT`
- `YTDLP_BIN`
- `ASR_SERVICE_URL`

## 本地运行

### Docker

```bash
docker compose up -d --build
```

### Maven

```bash
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8080
```

### API 文档

服务启动后，可以通过以下地址查看 OpenAPI 文档：

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## 当前重构重点

当前这轮重构主要目标是让后端更好地支撑完整产品链路：

- 用户自有材料库
- 多提供商模型配置
- 每日 30 分钟自适应练习
- 更完整地持久化错误、耗时、犹豫和跳过信号
- 与前端流程更紧密的契约
