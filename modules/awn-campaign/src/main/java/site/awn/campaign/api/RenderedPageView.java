package site.awn.campaign.api;

import java.util.List;

/**
 * 渲染后的专场页面视图。
 */
public record RenderedPageView(
        Integer schemaVersion,
        String template,
        String seoTitle,
        String seoDescription,
        List<RenderedSectionView> sections
) {
}
