package site.awn.star;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.support.AbstractPostgresIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图鉴 HTTP 面（公开读 + 运营写）。真实 PostgreSQL。
 * 契约：公开面仅 PUBLISHED，未发布 / 不存在一律 404 统一信封；
 * 运营写走 X-Admin-Token；素材红线在 facade 层强制（有图必须有授权来源）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StarApiTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired MockMvc mvc;

    private String createPerson(String name) throws Exception {
        String body = """
                {"name":"%s","nationality":"中国","professionTypes":["actor"],
                 "aliases":["%s别名"]}
                """.formatted(name, name);
        return mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createWork(String title) throws Exception {
        String body = """
                {"title":"%s","type":"tv","year":2025}
                """.formatted(title);
        return mvc.perform(post("/api/v1/admin/works")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long idField(String json) {
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void adminFlowPublishesBindsAndServesPublicReads() throws Exception {
        long personId = idField(createPerson("苏折柳"));
        long workId = idField(createWork("春山夜行"));

        mvc.perform(post("/api/v1/admin/stars/{id}/publish", personId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/admin/works/{id}/publish", workId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 关系绑定：主演（带角色名与番位）+ 演唱主题曲（无名次）
        mvc.perform(post("/api/v1/admin/works/{id}/cast", workId)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":%d,"roleType":"cast_lead","roleName":"饰 柳青梧","billingOrder":1}
                                """.formatted(personId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleType").value("cast_lead"))
                .andExpect(jsonPath("$.roleName").value("饰 柳青梧"));

        mvc.perform(post("/api/v1/admin/works/{id}/cast", workId)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":%d,"roleType":"theme_song","roleName":"主题曲《夜行》"}
                                """.formatted(personId)))
                .andExpect(status().isOk());

        // 公开列表：已发布人物与作品可见，q 命中别名
        mvc.perform(get("/api/v1/stars").param("q", "苏折柳别名"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("苏折柳"));
        mvc.perform(get("/api/v1/works").param("type", "tv").param("q", "春山夜行"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("春山夜行"));

        // 详情双向互跳数据：人物详情带作品行，作品详情带人员行
        mvc.perform(get("/api/v1/stars/{id}", personId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.name").value("苏折柳"))
                .andExpect(jsonPath("$.works[0].workId").value(workId))
                .andExpect(jsonPath("$.works[0].roleType").value("cast_lead"))
                .andExpect(jsonPath("$.works[0].roleName").value("饰 柳青梧"));
        mvc.perform(get("/api/v1/works/{id}", workId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.work.title").value("春山夜行"))
                .andExpect(jsonPath("$.cast[0].personId").value(personId))
                .andExpect(jsonPath("$.cast[0].roleType").value("cast_lead"));

        // 重复绑定 → 409 统一信封
        mvc.perform(post("/api/v1/admin/works/{id}/cast", workId)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":%d,"roleType":"cast_lead","roleName":"饰 柳青梧"}
                                """.formatted(personId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void draftsAndUnknownAreNotFoundOnPublicFace() throws Exception {
        long draftPersonId = idField(createPerson("未发布的人"));
        long draftWorkId = idField(createWork("未发布的作品"));

        mvc.perform(get("/api/v1/stars/{id}", draftPersonId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        mvc.perform(get("/api/v1/works/{id}", draftWorkId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_NOT_FOUND"));
        mvc.perform(get("/api/v1/stars/{id}", 999999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));

        // 发布后公开可见
        mvc.perform(post("/api/v1/admin/stars/{id}/publish", draftPersonId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stars/{id}", draftPersonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.name").value("未发布的人"));
    }

    @Test
    void adminWritesValidateAndRequireToken() throws Exception {
        // 无 token / 错 token → 401
        mvc.perform(post("/api/v1/admin/stars").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/works/{id}/cast", 1)
                        .header(TOKEN_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        // 校验失败 → 400 信封：姓名必填、职业白名单、有图必须有来源、作品类型白名单
        mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","nationality":"中国","professionTypes":["actor"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"阿杉","nationality":"中国","professionTypes":["astronaut"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"阿杉","nationality":"中国","professionTypes":["actor"],
                                 "photoUrl":"https://example.com/x.jpg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/admin/works")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"某作品","type":"hologram","year":2025}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 未知人物 / 作品绑定 → 404
        mvc.perform(post("/api/v1/admin/works/{id}/cast", 888888888)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":1,"roleType":"cast_lead"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_NOT_FOUND"));
    }

    @Test
    void adminEditsUnpublishesAndDeletes() throws Exception {
        long personId = idField(createPerson("编辑前"));

        // PUT 编辑草稿
        mvc.perform(put("/api/v1/admin/stars/{id}", personId)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"编辑后","nationality":"中国","professionTypes":["singer"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("编辑后"))
                .andExpect(jsonPath("$.professionTypes[0]").value("singer"));

        // 发布后冻结：PUT → 409
        mvc.perform(post("/api/v1/admin/stars/{id}/publish", personId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/stars/{id}", personId)
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改名","nationality":"中国","professionTypes":["actor"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // unpublish → DRAFT，重新可编辑
        mvc.perform(post("/api/v1/admin/stars/{id}/unpublish", personId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // DELETE → 公开面 404
        mvc.perform(delete("/api/v1/admin/stars/{id}", personId).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stars/{id}", personId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));

        // 未知 id 的写操作 → 404 统一信封；无 token → 401
        mvc.perform(delete("/api/v1/admin/stars/{id}", 987654321).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        mvc.perform(delete("/api/v1/admin/stars/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void photoWithoutLicenseStaysPlaceholderInPublicView() throws Exception {
        // 有图无来源在 facade 已被拒；有图有来源则公开透出
        String withPhoto = mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"江晚吟","nationality":"中国","professionTypes":["singer"],
                                 "photoUrl":"https://example.com/licensed.jpg","photoSource":"唱片公司书面授权"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/admin/stars/{id}/publish", idField(withPhoto)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stars").param("q", "江晚吟"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].photoUrl").value("https://example.com/licensed.jpg"));

        // 无图人物：photoUrl 为 null（前端按「未收录」渲染占位）
        String noPhoto = mvc.perform(post("/api/v1/admin/stars")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"陆无声","nationality":"中国","professionTypes":["director"]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/admin/stars/{id}/publish", idField(noPhoto)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/stars").param("q", "陆无声"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].photoUrl").isEmpty());
    }
}
