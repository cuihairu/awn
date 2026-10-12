package site.awn.campaign.api;

import java.util.Map;

/**
 * 渲染后的页面模块视图。
 */
public record RenderedSectionView(
        String id,
        String type,
        Map<String, Object> props,
        String content
) {
}
