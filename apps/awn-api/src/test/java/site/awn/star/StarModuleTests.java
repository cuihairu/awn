package site.awn.star;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.star.api.BindCastCommand;
import site.awn.star.api.CreatePersonCommand;
import site.awn.star.api.CreateWorkCommand;
import site.awn.star.api.PersonDetail;
import site.awn.star.api.PersonView;
import site.awn.star.api.StarFacade;
import site.awn.star.api.WorkDetail;
import site.awn.star.api.WorkView;
import site.awn.star.domain.PersonNotFoundException;
import site.awn.star.domain.StarConflictException;
import site.awn.star.domain.WorkNotFoundException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * star 模块：人物 / 作品 / 关系行（多对多带身份语义）与公开面守卫。
 * 验收口径（docs/design/star-catalog.md）：关系行不扁平化——
 * role_type / role_name / billing_order 挂在关系上；草稿内容不跨实体泄露；
 * 照片无授权来源不透出。
 */
@SpringBootTest
class StarModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired StarFacade stars;

    private CreatePersonCommand person(String name, List<String> professions, String... aliases) {
        return new CreatePersonCommand(name, null, List.of(aliases), "中国", professions, null, null, null);
    }

    private CreatePersonCommand personWithPhoto(String photoUrl, String photoSource) {
        return new CreatePersonCommand("林晚", null, List.of(), "中国",
                List.of("singer"), null, photoUrl, photoSource);
    }

    @Test
    void createPersonValidatesRules() {
        assertThatThrownBy(() -> stars.createPerson(
                new CreatePersonCommand("  ", null, List.of(), "中国", List.of("actor"), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> stars.createPerson(
                new CreatePersonCommand("林晚", null, List.of(), " ", List.of("actor"), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nationality");
        assertThatThrownBy(() -> stars.createPerson(person("林晚", List.of("prime-minister"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profession");
        assertThatThrownBy(() -> stars.createPerson(person("林晚", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profession");

        // 素材红线：有图必须有授权来源说明
        assertThatThrownBy(() -> stars.createPerson(
                new CreatePersonCommand("林晚", null, List.of(), "中国", List.of("singer"), null,
                        "https://example.com/photo.jpg", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("photo source");

        PersonView ok = stars.createPerson(personWithPhoto("https://example.com/p.jpg", "唱片公司授权"));
        assertThat(ok.status()).isEqualTo("DRAFT");
        assertThat(ok.photoUrl()).isNotNull();
    }

    @Test
    void relationRowCarriesRoleSemantics() {
        PersonView person = stars.publishPerson(
                stars.createPerson(person("沈流舒", List.of("actor", "singer"), "流舒")).id());
        WorkView work = stars.publishWork(
                stars.createWork(new CreateWorkCommand("长风渡", "tv", 2025, null)).id());

        // 同一人同一作品两种身份各一行（主演 + 演唱主题曲），关系行不扁平化
        stars.bindCast(work.id(), new BindCastCommand(person.id(), "cast_lead", "饰 沈砚", 1));
        stars.bindCast(work.id(), new BindCastCommand(person.id(), "theme_song", "主题曲《长风》", null));

        PersonDetail detail = stars.publicPerson(person.id());
        assertThat(detail.works()).hasSize(2);
        assertThat(detail.works().get(0).roleType()).isEqualTo("cast_lead");
        assertThat(detail.works().get(0).roleName()).isEqualTo("饰 沈砚");
        assertThat(detail.works().get(0).billingOrder()).isEqualTo(1);
        assertThat(detail.works().get(0).workTitle()).isEqualTo("长风渡");
        assertThat(detail.works().stream().map(e -> e.roleType()))
                .containsExactly("cast_lead", "theme_song");

        // 反向：作品详情的人员表按 role_type 分组（设计 §2.1），同一人的两种身份各占一行
        WorkDetail workDetail = stars.publicWork(work.id());
        assertThat(workDetail.cast()).hasSize(2);
        assertThat(workDetail.cast().get(0).personName()).isEqualTo("沈流舒");
        assertThat(workDetail.cast().get(0).roleType()).isEqualTo("cast_lead");
        assertThat(workDetail.cast().get(0).roleName()).isEqualTo("饰 沈砚");
        assertThat(workDetail.cast().get(1).roleType()).isEqualTo("theme_song");
    }

    @Test
    void duplicateCastBindingConflicts() {
        PersonView person = stars.createPerson(person("程野", List.of("actor")));
        WorkView work = stars.createWork(new CreateWorkCommand("山有木兮", "movie", 2024, null));
        stars.bindCast(work.id(), new BindCastCommand(person.id(), "cast_lead", "饰 陆行舟", 1));

        // 同人同作同身份同角色名：重复 → 冲突
        assertThatThrownBy(() -> stars.bindCast(work.id(),
                new BindCastCommand(person.id(), "cast_lead", "饰 陆行舟", 1)))
                .isInstanceOf(StarConflictException.class)
                .hasMessageContaining("duplicate");

        // 同身份不同角色名：允许（一人多角）
        stars.bindCast(work.id(), new BindCastCommand(person.id(), "cast_lead", "饰 长老", null));

        // 同人同作不同身份：允许（主演 + 演唱主题曲）
        stars.bindCast(work.id(), new BindCastCommand(person.id(), "theme_song", "主题曲《木兮》", null));
    }

    @Test
    void publishGuardsKeepDraftsOutOfPublic() {
        PersonView draftPerson = stars.createPerson(person("顾清弦", List.of("musician"), "清弦"));
        WorkView draftWork = stars.createWork(new CreateWorkCommand("未发布的作品", "album", 2026, null));

        // 草稿人物 / 作品公开面 404
        assertThatThrownBy(() -> stars.publicPerson(draftPerson.id()))
                .isInstanceOf(PersonNotFoundException.class);
        assertThatThrownBy(() -> stars.publicWork(draftWork.id()))
                .isInstanceOf(WorkNotFoundException.class);

        PersonView person = stars.publishPerson(draftPerson.id());
        assertThat(person.status()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> stars.publishPerson(person.id()))
                .isInstanceOf(StarConflictException.class)
                .hasMessageContaining("already published");

        stars.bindCast(draftWork.id(), new BindCastCommand(person.id(), "composer", null, null));

        // 已发布人物的作品列表不泄露草稿作品；发布后可见
        assertThat(stars.publicPerson(person.id()).works()).isEmpty();
        stars.publishWork(draftWork.id());
        assertThat(stars.publicPerson(person.id()).works()).hasSize(1);

        // 已发布作品的 cast 不泄露草稿人物：绑定时人物必须已存在，这里验证已发布人物可见
        assertThat(stars.publicWork(draftWork.id()).cast()).hasSize(1);
    }

    @Test
    void draftEditsAreGuardedAndUnpublishReopens() {
        PersonView draft = stars.createPerson(person("旧名", List.of("actor")));

        // 草稿可编辑：字段更新后仍是 DRAFT
        PersonView edited = stars.updatePerson(draft.id(), person("新名", List.of("singer"), "别名甲"));
        assertThat(edited.name()).isEqualTo("新名");
        assertThat(edited.professionTypes()).containsExactly("singer");
        assertThat(edited.aliases()).containsExactly("别名甲");
        assertThat(edited.status()).isEqualTo("DRAFT");

        // 发布后冻结：编辑与删除都抛冲突，提示先 unpublish
        stars.publishPerson(draft.id());
        assertThatThrownBy(() -> stars.updatePerson(draft.id(), person("改名", List.of("actor"))))
                .isInstanceOf(StarConflictException.class)
                .hasMessageContaining("unpublish");
        assertThatThrownBy(() -> stars.deletePerson(draft.id()))
                .isInstanceOf(StarConflictException.class)
                .hasMessageContaining("unpublish");

        // unpublish 回到草稿：公开面 404，可再次编辑；重复 unpublish 抛冲突
        assertThat(stars.unpublishPerson(draft.id()).status()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> stars.publicPerson(draft.id()))
                .isInstanceOf(PersonNotFoundException.class);
        assertThatThrownBy(() -> stars.unpublishPerson(draft.id()))
                .isInstanceOf(StarConflictException.class);
        assertThat(stars.updatePerson(draft.id(), person("三改", List.of("host"))).name())
                .isEqualTo("三改");

        // 删除草稿后公开面与后续编辑都 404
        stars.deletePerson(draft.id());
        assertThatThrownBy(() -> stars.publicPerson(draft.id()))
                .isInstanceOf(PersonNotFoundException.class);
        assertThatThrownBy(() -> stars.updatePerson(draft.id(), person("x", List.of("actor"))))
                .isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void workEditsAndDeleteCascadeBindings() {
        PersonView person = stars.publishPerson(
                stars.createPerson(person("柯屿", List.of("actor"))).id());
        WorkView work = stars.createWork(new CreateWorkCommand("初稿片名", "movie", 2024, null));

        // 作品草稿可编辑
        WorkView edited = stars.updateWork(work.id(),
                new CreateWorkCommand("定稿片名", "movie", 2025, "简介"));
        assertThat(edited.title()).isEqualTo("定稿片名");
        assertThat(edited.year()).isEqualTo(2025);
        assertThat(edited.status()).isEqualTo("DRAFT");

        stars.bindCast(work.id(), new BindCastCommand(person.id(), "cast_lead", "饰 主角", 1));
        stars.publishWork(work.id());
        assertThatThrownBy(() -> stars.updateWork(work.id(),
                new CreateWorkCommand("再改", "movie", 2025, null)))
                .isInstanceOf(StarConflictException.class);
        assertThat(stars.unpublishWork(work.id()).status()).isEqualTo("DRAFT");
        stars.publishWork(work.id());
        assertThat(stars.publicPerson(person.id()).works()).hasSize(1);

        // 已发布冻结：删除也需先撤回
        assertThatThrownBy(() -> stars.deleteWork(work.id()))
                .isInstanceOf(StarConflictException.class)
                .hasMessageContaining("unpublish");

        // 删除作品：关系行随 FK 级联清除（残留的话 publicPerson 查作品会抛 WorkNotFound）
        stars.unpublishWork(work.id());
        stars.deleteWork(work.id());
        assertThat(stars.publicPerson(person.id()).works()).isEmpty();
        assertThatThrownBy(() -> stars.publicWork(work.id()))
                .isInstanceOf(WorkNotFoundException.class);
    }

    @Test
    void publicSearchHitsNameAliasAndFilters() {
        // 测试库从不截断：检索断言按名称等值匹配，名称追加随机段避免重复运行撞数据
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String name = "方屿-" + tag;
        String alias = "岛屿先生-" + tag;
        String latinName = "North-Shore-" + tag;
        PersonView a = stars.publishPerson(stars.createPerson(
                new CreatePersonCommand(name, null, List.of(alias), "中国",
                        List.of("actor"), null, null, null)).id());
        // 国籍用拉丁写法，验证国籍过滤大小写不敏感（等值匹配，不做拼音/多语言映射）
        stars.publishPerson(
                stars.createPerson(
                        new CreatePersonCommand("北岸-" + tag, latinName, List.of(), "China",
                                List.of("singer"), null, null, null)).id());

        // q 命中别名 / 原名；大小写不敏感
        assertThat(stars.publicPersons(null, null, alias)).hasSize(1);
        assertThat(stars.publicPersons(null, null, latinName.toLowerCase())).hasSize(1);
        assertThat(stars.publicPersons(null, null, name)).hasSize(1);

        // 职业 / 国籍过滤
        assertThat(stars.publicPersons("actor", null, null))
                .extracting(PersonView::id).contains(a.id());
        assertThat(stars.publicPersons("actor", null, "北岸-" + tag)).isEmpty();
        assertThat(stars.publicPersons("singer", "CHINA", null)).isNotEmpty();
        assertThat(stars.publicPersons("singer", "Japan", null)).isEmpty();

        // 作品检索：类型过滤 + 标题匹配
        String song = "雾里看花-" + tag;
        stars.publishWork(stars.createWork(new CreateWorkCommand(song, "song", 2025, null)).id());
        assertThat(stars.publicWorks("song", song)).hasSize(1);
        assertThat(stars.publicWorks("movie", song)).isEmpty();
    }
}
