package site.awn.shared.url;

import java.util.Set;

/**
 * 受控凭证存储域白名单。
 * 供货凭证、交付凭证等用户不可任意指 URL 的场景统一使用：
 * 仅接受 https + 自有存储域（或由平台解析的相对路径），防止凭证外域注入。
 */
public final class StorageUrlValidator {

    /** 自有资源域；环境差异（CDN 域名）由配置覆盖，此处为代码内兜底白名单 */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "assets.awn.site",
            "cdn.awn.site",
            "img.awn.site",
            "video.awn.site"
    );

    private StorageUrlValidator() {
    }

    /** 是否指向白名单存储域（或平台解析的相对路径） */
    public static boolean isAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.startsWith("/")) {
            return true;
        }
        if (!url.startsWith("https://")) {
            return false;
        }
        String rest = url.substring("https://".length());
        int pathStart = rest.indexOf('/');
        String host = pathStart >= 0 ? rest.substring(0, pathStart) : rest;
        int portStart = host.indexOf(':');
        if (portStart >= 0) {
            host = host.substring(0, portStart);
        }
        return ALLOWED_DOMAINS.contains(host.toLowerCase());
    }

    /** 校验失败直接拒绝：IllegalArgumentException（映射 VALIDATION_FAILED 错误码） */
    public static void validate(String url) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException("evidence url must use whitelisted storage domain: " + url);
        }
    }

    public static Set<String> allowedDomains() {
        return ALLOWED_DOMAINS;
    }
}
