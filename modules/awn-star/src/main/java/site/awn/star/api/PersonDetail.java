package site.awn.star.api;

import java.util.List;

/** 人物详情：人物 + 参与作品列表（仅含已发布作品，按作品类型分组由前端处理）。 */
public record PersonDetail(PersonView person, List<PersonWorkEntry> works) {
}
