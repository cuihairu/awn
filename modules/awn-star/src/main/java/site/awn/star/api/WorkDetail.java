package site.awn.star.api;

import java.util.List;

/** 作品详情：作品 + 参与人员列表（仅含已发布人物）。 */
public record WorkDetail(WorkView work, List<PersonWorkEntry> cast) {
}
