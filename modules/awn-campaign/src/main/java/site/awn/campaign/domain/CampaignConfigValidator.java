package site.awn.campaign.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import site.awn.campaign.api.CampaignConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 专场配置校验器。
 * 校验规则：
 * 1. schemaVersion 必须为 1
 * 2. template 不能为空
 * 3. sections 中的 type 必须在白名单中
 * 4. sections 中的 id 必须唯一
 * 5. 不允许出现 script 或 html 标签
 */
@Component
public class CampaignConfigValidator {

    private final ObjectMapper objectMapper;

    public CampaignConfigValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(CampaignConfig config) {
        List<String> errors = new ArrayList<>();

        if (config == null) {
            throw new CampaignConfigValidationException("config must not be null");
        }

        if (config.schemaVersion() == null || config.schemaVersion() != 1) {
            errors.add("schemaVersion must be 1");
        }

        if (config.template() == null || config.template().isBlank()) {
            errors.add("template must not be blank");
        }

        if (config.seo() != null) {
            if (config.seo().title() != null && config.seo().title().length() > 128) {
                errors.add("seo.title must not exceed 128 characters");
            }
            if (config.seo().description() != null && config.seo().description().length() > 512) {
                errors.add("seo.description must not exceed 512 characters");
            }
        }

        if (config.sections() != null) {
            var seenIds = new java.util.HashSet<String>();
            for (int i = 0; i < config.sections().size(); i++) {
                CampaignConfig.SectionConfig section = config.sections().get(i);
                String prefix = "sections[" + i + "]";

                if (section.id() == null || section.id().isBlank()) {
                    errors.add(prefix + ".id must not be blank");
                } else if (!seenIds.add(section.id())) {
                    errors.add(prefix + ".id must be unique: " + section.id());
                }

                if (section.type() == null || section.type().isBlank()) {
                    errors.add(prefix + ".type must not be blank");
                } else if (!SectionWhitelist.isAllowed(section.type())) {
                    errors.add(prefix + ".type not in whitelist: " + section.type());
                }

                if (section.props() != null) {
                    validateProps(prefix + ".props", section.props(), errors);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new CampaignConfigValidationException(String.join("; ", errors));
        }
    }

    private void validateProps(String prefix, Map<String, Object> props, List<String> errors) {
        for (var entry : props.entrySet()) {
            validateValue(prefix + "." + entry.getKey(), entry.getKey(), entry.getValue(), errors);
        }
    }

    /**
     * 递归校验任意深度的值：内联展示形态的 props 是嵌套结构
     * （faq 的 entries 问答对、imageGallery 的 images 条目、rulePanel 的 rules 文本数组），
     * 只查顶层会漏掉嵌套在对象/数组里的脚本标签与非白名单资源引用。
     */
    private void validateValue(String prefix, String key, Object value, List<String> errors) {
        if (value instanceof String str) {
            String lower = str.toLowerCase();
            if (lower.contains("<script") || lower.contains("javascript:")) {
                errors.add(prefix + " must not contain script");
            }
            if (lower.contains("<iframe") || lower.contains("<object") || lower.contains("<embed")) {
                errors.add(prefix + " must not contain embedded HTML");
            }
            // 受控资源引用：URL 键仅允许白名单域（嵌套条目如 images[].src 同口径）
            if (isResourceUrlKey(key) && !ResourceDomainWhitelist.isAllowed(str)) {
                errors.add(prefix + " references non-whitelisted domain: " + str);
            }
        } else if (value instanceof java.util.List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                validateValue(prefix + "[" + i + "]", key, list.get(i), errors);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                validateValue(prefix + "." + childKey, childKey, entry.getValue(), errors);
            }
        }
    }

    /** 资源引用键：值必须是白名单域上的资源 URL（assetId 指向 campaign_assets 记录，不属于 URL 引用） */
    private boolean isResourceUrlKey(String key) {
        return key.equals("url") || key.equals("src") || key.equals("poster");
    }

    public void validateJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                throw new CampaignConfigValidationException("config must be a JSON object");
            }
            CampaignConfig config = objectMapper.readValue(json, CampaignConfig.class);
            validate(config);
        } catch (CampaignConfigValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CampaignConfigValidationException("invalid config JSON: " + e.getMessage());
        }
    }
}
