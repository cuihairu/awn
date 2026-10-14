package site.awn.star.api;

import java.util.List;

/**
 * star 模块对外接口。其他模块只允许通过本接口使用图鉴能力。
 * 边界：图鉴是编辑型内容数据——本模块不触碰交易数据（活动 / 商品 / 订单 / 库存 / 支付）。
 */
public interface StarFacade {

    /** 创建人物（草稿）。校验：姓名 / 国籍非空、职业白名单；有图必须有授权来源说明。 */
    PersonView createPerson(CreatePersonCommand command);

    /** 发布人物：DRAFT → PUBLISHED 守卫，重复发布抛冲突。 */
    PersonView publishPerson(long personId);

    /** 创建作品（草稿）。校验：标题非空、类型白名单、年份区间。 */
    WorkView createWork(CreateWorkCommand command);

    /** 发布作品：DRAFT → PUBLISHED 守卫，重复发布抛冲突。 */
    WorkView publishWork(long workId);

    /**
     * 给作品绑定人物身份（多对多关系行带 role_type / role_name / billing_order）。
     * 同一人物同一作品同一身份 + 同一角色名重复绑定抛冲突。
     */
    PersonWorkEntry bindCast(long workId, BindCastCommand command);

    /**
     * 公开人物列表（仅 PUBLISHED）。检索范围（拍板口径）：职业类型、国籍、
     * 姓名 / 别名基本匹配；不做全文与拼音模糊。
     */
    List<PersonView> publicPersons(String profession, String nationality, String q);

    /** 公开人物详情：404 当不存在或未发布；作品列表仅含已发布作品。 */
    PersonDetail publicPerson(long personId);

    /** 公开作品列表（仅 PUBLISHED）。检索范围：类型、标题基本匹配。 */
    List<WorkView> publicWorks(String type, String q);

    /** 公开作品详情：404 当不存在或未发布；人员列表仅含已发布人物。 */
    WorkDetail publicWork(long workId);
}
