package site.awn.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.star.api.BindCastCommand;
import site.awn.star.api.CreatePersonCommand;
import site.awn.star.api.CreateWorkCommand;
import site.awn.star.api.PersonView;
import site.awn.star.api.PersonWorkEntry;
import site.awn.star.api.StarFacade;
import site.awn.star.api.WorkView;

/**
 * 图鉴运营接口：创建 / 发布 / 绑定人物与作品身份。
 * 鉴权由 AdminAuthFilter 统一执行（X-Admin-Token + 资源组限流）。
 * 素材红线落在 facade：图片必须带授权来源说明，否则拒绝创建。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class StarAdminController {

    private final StarFacade stars;

    public StarAdminController(StarFacade stars) {
        this.stars = stars;
    }

    /** 创建人物（草稿） */
    @PostMapping("/stars")
    public PersonView createPerson(@RequestBody CreatePersonCommand command) {
        return stars.createPerson(command);
    }

    /** 编辑人物草稿（仅 DRAFT；已发布冻结，先 unpublish） */
    @PutMapping("/stars/{personId}")
    public PersonView updatePerson(@PathVariable Long personId, @RequestBody CreatePersonCommand command) {
        return stars.updatePerson(personId, command);
    }

    /** 发布人物：DRAFT → PUBLISHED 守卫 */
    @PostMapping("/stars/{personId}/publish")
    public PersonView publishPerson(@PathVariable Long personId) {
        return stars.publishPerson(personId);
    }

    /** 撤回人物：PUBLISHED → DRAFT 守卫 */
    @PostMapping("/stars/{personId}/unpublish")
    public PersonView unpublishPerson(@PathVariable Long personId) {
        return stars.unpublishPerson(personId);
    }

    /** 删除人物草稿（仅 DRAFT；关系行随外键级联清除） */
    @DeleteMapping("/stars/{personId}")
    public void deletePerson(@PathVariable Long personId) {
        stars.deletePerson(personId);
    }

    /** 创建作品（草稿） */
    @PostMapping("/works")
    public WorkView createWork(@RequestBody CreateWorkCommand command) {
        return stars.createWork(command);
    }

    /** 编辑作品草稿（仅 DRAFT；已发布冻结，先 unpublish） */
    @PutMapping("/works/{workId}")
    public WorkView updateWork(@PathVariable Long workId, @RequestBody CreateWorkCommand command) {
        return stars.updateWork(workId, command);
    }

    /** 发布作品：DRAFT → PUBLISHED 守卫 */
    @PostMapping("/works/{workId}/publish")
    public WorkView publishWork(@PathVariable Long workId) {
        return stars.publishWork(workId);
    }

    /** 撤回作品：PUBLISHED → DRAFT 守卫 */
    @PostMapping("/works/{workId}/unpublish")
    public WorkView unpublishWork(@PathVariable Long workId) {
        return stars.unpublishWork(workId);
    }

    /** 删除作品草稿（仅 DRAFT；关系行随外键级联清除） */
    @DeleteMapping("/works/{workId}")
    public void deleteWork(@PathVariable Long workId) {
        stars.deleteWork(workId);
    }

    /** 给作品绑定人物身份（关系行：role_type / role_name / billing_order） */
    @PostMapping("/works/{workId}/cast")
    public PersonWorkEntry bindCast(@PathVariable Long workId, @RequestBody BindCastCommand command) {
        return stars.bindCast(workId, command);
    }
}
