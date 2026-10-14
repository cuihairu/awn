package site.awn.star.api;

import java.util.List;

/**
 * 创建人物。photoUrl 与 photoSource 必须成对出现：
 * 有图必须有授权来源说明（书面授权 / 自有拍摄），否则 facade 拒绝。
 */
public record CreatePersonCommand(
        String name,
        String originalName,
        List<String> aliases,
        String nationality,
        List<String> professionTypes,
        String bio,
        String photoUrl,
        String photoSource) {
}
