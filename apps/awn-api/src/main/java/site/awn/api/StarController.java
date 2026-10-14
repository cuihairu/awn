package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.awn.star.api.PersonDetail;
import site.awn.star.api.PersonView;
import site.awn.star.api.StarFacade;
import site.awn.star.api.WorkDetail;
import site.awn.star.api.WorkView;

import java.util.List;

/**
 * 公开图鉴接口（docs/design/star-catalog.md §2.3）：只读，仅暴露 PUBLISHED。
 * 人物 / 作品未发布一律 404（PERSON_NOT_FOUND / WORK_NOT_FOUND），不泄露草稿。
 * 检索为基础匹配（姓名 / 别名 / 国籍 / 类型），不做全文与拼音模糊。
 */
@RestController
public class StarController {

    private final StarFacade stars;

    public StarController(StarFacade stars) {
        this.stars = stars;
    }

    /** 公开人物列表：可按职业类型、国籍过滤，q 命中姓名 / 原名 / 别名 */
    @GetMapping("/api/v1/stars")
    public List<PersonView> persons(@RequestParam(required = false) String profession,
                                    @RequestParam(required = false) String nationality,
                                    @RequestParam(required = false) String q) {
        return stars.publicPersons(profession, nationality, q);
    }

    /** 公开人物详情（含参与作品与身份）；未发布或不存在 → 404 */
    @GetMapping("/api/v1/stars/{personId}")
    public PersonDetail person(@PathVariable Long personId) {
        return stars.publicPerson(personId);
    }

    /** 公开作品列表：可按类型过滤，q 命中标题 */
    @GetMapping("/api/v1/works")
    public List<WorkView> works(@RequestParam(required = false) String type,
                                @RequestParam(required = false) String q) {
        return stars.publicWorks(type, q);
    }

    /** 公开作品详情（含人员表与身份）；未发布或不存在 → 404 */
    @GetMapping("/api/v1/works/{workId}")
    public WorkDetail work(@PathVariable Long workId) {
        return stars.publicWork(workId);
    }
}
