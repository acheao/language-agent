package com.acheao.languageagent.v2.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import com.acheao.languageagent.v2.entity.UserLlmConfig;
import com.acheao.languageagent.v2.repository.UserLlmConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserLlmConfigService {

    private static final Map<String, ProviderCatalogItem> PROVIDERS = Map.of(
            "minimax", new ProviderCatalogItem("minimax", "MiniMax", "https://api.minimaxi.chat/v1"),
            "deepseek", new ProviderCatalogItem("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
            "openai", new ProviderCatalogItem("openai", "OpenAI / ChatGPT", "https://api.openai.com/v1"),
            "codex", new ProviderCatalogItem("codex", "Codex", "https://api.openai.com/v1"),
            "glm", new ProviderCatalogItem("glm", "GLM", "https://open.bigmodel.cn/api/paas/v4"),
            "grok", new ProviderCatalogItem("grok", "Grok", "https://api.x.ai/v1"),
            "gemini", new ProviderCatalogItem("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
            "qwen", new ProviderCatalogItem("qwen", "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            "kimi", new ProviderCatalogItem("kimi", "Kimi", "https://api.moonshot.cn/v1"));

    private final UserLlmConfigRepository repository;
    private final SettingsEncryptionService encryptionService;
    private final GenericLlmGateway llmGateway;

    public UserLlmConfigService(
            UserLlmConfigRepository repository,
            SettingsEncryptionService encryptionService,
            GenericLlmGateway llmGateway) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.llmGateway = llmGateway;
    }

    public List<ConfigView> list(User user) {
        return repository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toView)
                .toList();
    }

    public List<ProviderCatalogItem> providers() {
        return PROVIDERS.values().stream().sorted((a, b) -> a.key().compareTo(b.key())).toList();
    }

    @Transactional
    public ConfigView create(User user, UpsertConfigRequest request) {
        validateProvider(request.provider());
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required");
        }

        UserLlmConfig entity = new UserLlmConfig();
        entity.setUser(user);
        fillConfig(entity, request, true);
        if (request.isDefault()) {
            clearDefaultFlag(user);
            entity.setDefault(true);
        } else if (repository.findFirstByUserAndIsDefaultTrueAndEnabledTrue(user).isEmpty()) {
            entity.setDefault(true);
        }
        repository.save(entity);
        return toView(entity);
    }

    @Transactional
    public ConfigView update(User user, UUID id, UpsertConfigRequest request) {
        UserLlmConfig entity = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "LLM config not found"));
        validateProvider(request.provider());
        fillConfig(entity, request, false);
        if (request.isDefault()) {
            clearDefaultFlag(user);
            entity.setDefault(true);
        }
        repository.save(entity);
        return toView(entity);
    }

    @Transactional
    public void delete(User user, UUID id) {
        UserLlmConfig entity = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "LLM config not found"));
        boolean deletingDefault = entity.isDefault();
        repository.delete(entity);
        if (deletingDefault) {
            repository.findAllByUserOrderByCreatedAtDesc(user).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                repository.save(next);
            });
        }
    }

    public TestResult test(User user, UUID id) {
        UserLlmConfig entity = repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "LLM config not found"));
        llmGateway.ping(toRuntimeConfig(entity));
        return new TestResult(true, "Connection successful");
    }

    public Optional<GenericLlmGateway.RuntimeConfig> findDefaultRuntime(User user) {
        return repository.findFirstByUserAndIsDefaultTrueAndEnabledTrue(user).map(this::toRuntimeConfig);
    }

    public boolean hasAnyConfig(User user) {
        return repository.existsByUser(user);
    }

    private void fillConfig(UserLlmConfig entity, UpsertConfigRequest request, boolean requireApiKey) {
        entity.setProvider(request.provider().trim().toLowerCase());
        entity.setModel(request.model().trim());
        entity.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.provider() + " / " + request.model()
                : request.displayName().trim());
        entity.setBaseUrl(resolveBaseUrl(request.provider(), request.baseUrl()));
        entity.setEnabled(request.enabled());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            entity.setApiKeyEncrypted(encryptionService.encrypt(request.apiKey().trim()));
        } else if (requireApiKey || entity.getApiKeyEncrypted() == null || entity.getApiKeyEncrypted().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required");
        }
    }

    private ConfigView toView(UserLlmConfig entity) {
        return new ConfigView(
                entity.getId(),
                entity.getProvider(),
                entity.getDisplayName(),
                entity.getModel(),
                entity.getBaseUrl(),
                entity.isEnabled(),
                entity.isDefault(),
                maskApiKey(encryptionService.decrypt(entity.getApiKeyEncrypted())));
    }

    private GenericLlmGateway.RuntimeConfig toRuntimeConfig(UserLlmConfig entity) {
        return new GenericLlmGateway.RuntimeConfig(
                entity.getProvider(),
                entity.getModel(),
                entity.getBaseUrl(),
                encryptionService.decrypt(entity.getApiKeyEncrypted()));
    }

    private String resolveBaseUrl(String provider, String customBaseUrl) {
        if (customBaseUrl != null && !customBaseUrl.isBlank()) {
            return customBaseUrl.trim().replaceAll("/+$", "");
        }
        return PROVIDERS.get(provider.trim().toLowerCase()).defaultBaseUrl();
    }

    private void validateProvider(String provider) {
        if (provider == null || provider.isBlank() || !PROVIDERS.containsKey(provider.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported LLM provider");
        }
    }

    private void clearDefaultFlag(User user) {
        repository.findAllByUserOrderByCreatedAtDesc(user).forEach(config -> config.setDefault(false));
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 6) {
            return "******";
        }
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 3);
    }

    public record ProviderCatalogItem(String key, String label, String defaultBaseUrl) {
    }

    public record ConfigView(
            UUID id,
            String provider,
            String displayName,
            String model,
            String baseUrl,
            boolean enabled,
            boolean isDefault,
            String apiKeyPreview) {
    }

    public record UpsertConfigRequest(
            String provider,
            String model,
            String displayName,
            String baseUrl,
            String apiKey,
            boolean enabled,
            boolean isDefault) {
    }

    public record TestResult(boolean success, String message) {
    }
}
