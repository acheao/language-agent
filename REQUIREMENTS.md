# 后端需求文档（Spring Boot 服务）

**技术栈**：Spring Boot 3.5.6 / Java 21 / spring-web + webflux / Spring Data JPA / PostgreSQL / springdoc-openapi  
**项目目标**：提供素材管理、出题、批改、调度、统计 API；封装硅基流动 LLM 调用；持久化所有练习数据。

## 1. 模块划分（推荐包结构）

- `controller`：REST API
    
- `service`：
    
    - `MaterialService`
        
    - `SessionService`
        
    - `QuestionService`
        
    - `AnswerService`
        
    - `GradingService`
        
    - `SchedulerService`
        
    - `StatsService`
        
    - `LlmClient`（硅基流动封装）
        
- `domain/entity`：JPA Entity
    
- `repository`：Spring Data JPA
    
- `dto`：请求/响应对象
    
- `config`：OpenAPI、WebClient、CORS、Jackson
    
- `exception`：统一异常与错误码
    

---

## 2. 数据库设计（MVP 必要）

### 2.1 表（建议）

**materials**

- id (uuid)
    
- type (varchar)
    
- content (text)
    
- tags (jsonb 或 text)
    
- enabled (bool)
    
- created_at, updated_at
    

**sessions**

- id (uuid)
    
- batch_size (int)
    
- generator_mode (varchar)
    
- created_at
    

**questions**

- id (uuid)
    
- session_id (uuid)
    
- material_id (uuid nullable)
    
- type (varchar)
    
- prompt (text)
    
- reference_answer (jsonb/text)
    
- rubric (jsonb/text)
    
- difficulty (int)
    
- target_error_types (jsonb/text)
    
- llm_model (varchar)
    
- created_at
    

**answers**

- id (uuid)
    
- question_id (uuid)
    
- user_answer (text)
    
- time_spent_ms (bigint)
    
- submitted_at
    

**gradings**

- id (uuid)
    
- answer_id (uuid)
    
- score (int)
    
- is_correct (bool)
    
- corrected_answer (text)
    
- error_types (jsonb/text)
    
- explanation_zh (text)
    
- suggestions (jsonb/text)
    
- raw_llm_response (text)
    
- created_at
    

**material_stats**（调度用，MVP 推荐做）

- material_id (uuid pk)
    
- practice_count (int)
    
- correct_count (int)
    
- last_practiced_at (timestamp)
    
- next_review_at (timestamp)
    
- interval_days (int)
    
- ease (numeric)
    
- cooldown_until (timestamp)
    

**error_type_stats**（统计用，可 V2）

- error_type (varchar)
    
- count_7d, count_30d, count_total (int)
    
- last_seen_at (timestamp)
    

> MVP：可以先不建 `error_type_stats`，统计从 `gradings` 聚合查询。

---

## 3. 核心业务流程

## 3.1 导入素材

- 接口接收 `lines[]`
    
- 清洗：
    
    - trim
        
    - 去空行
        
    - 去重（可选：content 唯一索引或逻辑判断）
        
- 入库 `materials`
    
- 返回 success/fail 统计与失败原因
    

## 3.2 创建 session 并生成题目

`POST /sessions`

1. 新建 session
    
2. 调用 `SchedulerService.pickMaterials(batchSize)` 选出素材集合
    
3. 调用 `QuestionService.generateQuestions(materials, mode)` 生成 questions
    
    - `mode=hybrid`：部分题型走 DB 规则生成，部分走 LLM
        
4. questions 入库并返回给前端
    

## 3.3 提交答案与批改

`POST /answers`

1. 保存 answer
    
2. 调用 `GradingService.grade(question, userAnswer)`
    
    - 调用 LLM 输出 JSON
        
    - JSON 解析与校验（失败重试 1-2 次）
        
    - 仍失败：记录 rawText + 返回 fallback
        
3. 保存 grading
    
4. 更新 `material_stats`：
    
    - practice_count++
        
    - correct_count++（若 is_correct）
        
    - 更新 last_practiced_at
        
    - 计算 interval/next_review_at（简化 SM-2/Leitner）
        
    - 设置 cooldown（比如 30min 内不重复同素材）
        
5. 返回 grading
    

## 3.4 下一批题

`POST /sessions/{id}/next`

- 复用 scheduler + question generator 逻辑
    
- 返回新的 questions（并写入 session_id）
    

---

## 4. 调度算法（MVP 可落地）

### 4.1 目标

- 到期复习优先 + 弱项错误类型优先 + 少量新素材探索
    

### 4.2 必要输入

- `material_stats`：next_review_at / interval / ease / cooldown
    
- `gradings`：近 N 天 error_types（用于弱项频率）
    
- `materials.enabled = true`
    

### 4.3 优先级评分（建议实现）

priority = a*S_due + b*S_weak + c*S_new - d*cooldownPenalty

- S_due：到期=1；未到期按接近程度衰减
    
- S_weak：近 7 天错误类型频率加权（越近越大）
    
- S_new：从未练过=1
    
- cooldownPenalty：未过 cooldown_until 则大惩罚或直接排除
    

输出：按 priority 排序取前 batchSize 个 material

---

## 5. LLM 接入（硅基流动）

### 5.1 统一封装

- 使用 `WebClient`（你已经引了 webflux）
    
- 支持：
    
    - 超时（例如 20s）
        
    - 重试（解析失败重试；网络失败有限重试）
        
    - 日志（耗时、模型、请求 id）
        
- 配置项：
    
    - baseUrl
        
    - apiKey
        
    - model
        
    - temperature（默认 0.2）
        
    - maxTokens（可选）
        
    - enableMock（本地开发用）
        

### 5.2 批改输出必须结构化

- 强制 JSON schema（用 Jackson 反序列化）
    
- DTO：`LlmGradingResponse`
    

### 5.3 成本控制（MVP）

- 题目生成尽量 DB 规则生成（cloze/correct）
    
- 批改必走 LLM
    
- grading 缓存：同 answer_id 不重复调用
    

---

## 6. API 设计（后端视角）

### 6.1 Materials Controller

- `POST /api/materials/import`
    
- `GET /api/materials`
    
- `PATCH /api/materials/{id}`
    

### 6.2 Practice Controller

- `POST /api/sessions`
    
- `POST /api/answers`
    
- `POST /api/sessions/{sessionId}/next`
    

### 6.3 Stats Controller

- `GET /api/stats/overview`
    
- `GET /api/stats/error-types`
    

> 需要 springdoc：为所有接口写注解与 DTO schema，访问 `/swagger-ui.html` 或 springdoc 默认地址。

---

## 7. 验收标准（后端）

1. materials 导入后，DB 中有记录、enabled=true
    
2. 创建 session 返回 questions，并且 questions 已入库
    
3. 提交 answer 返回 grading，且 gradings 表存在对应记录，error_types 非空（或允许空）
    
4. material_stats 会更新 next_review_at / interval_days
    
5. next batch 能体现调度：到期/弱项优先的素材更常出现
    
6. OpenAPI 文档可访问且能试调接口
    

---

## 8. 配置与运行

### 8.1 application.yml（建议）

- `spring.datasource.url/username/password`
    
- `spring.jpa.hibernate.ddl-auto=update`（MVP 可用，后续改 Flyway）
    
- `llm.base-url`
    
- `llm.api-key`
    
- `llm.model`
    
- `llm.timeout-ms`
    
- `cors.allowed-origins`