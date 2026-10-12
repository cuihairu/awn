package site.awn.campaign.domain;

import java.util.Set;

/**
 * 受控资源引用白名单。
 * 专场所引用的图片 / 视频等资源 URL 仅允许白名单域，防止运营配置外域注入。
 */
public final class ResourceDomainWhitelist {

    /** 自有资源域；环境差异（CDN 域名）由配置覆盖，此处为代码内兜底白名单 */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "assets.awn.site",
            "cdn.awn.site",
            "img.awn.site",
            "video.awn.site"
    );

    private ResourceDomainWhitelist() {
    }

    /**
     * 校验资源 URL 是否指向白名单域。
     * 仅接受 https；无 host 的相对路径按白名单处理（由渲染端基于自有域解析）。
     */
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

    public static Set<String> allowedDomains() {
        return ALLOWED_DOMAINS;
    }
}
