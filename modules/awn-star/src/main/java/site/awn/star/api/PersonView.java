package site.awn.star.api;

import java.util.List;

/**
 * 人物视图。photoUrl 只在有授权来源说明时非空（素材红线：
 * 缺图 / 无授权来源一律按占位图渲染，公开面不透出来源细节）。
 */
public record PersonView(
        long id,
        String name,
        String originalName,
        List<String> aliases,
        String nationality,
        List<String> professionTypes,
        String bio,
        String photoUrl,
        String status) {
}
