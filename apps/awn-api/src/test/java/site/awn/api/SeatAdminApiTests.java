package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.support.CatalogProductFactory;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 铺座运营入口（todo 2D 座位图余项，管理面）：
 * POST /api/v1/admin/sessions/{sessionCode}/seats（X-Admin-Token 收口）经
 * InventoryFacade.createSeats（商品 catalog 校验 + 同场次同位置唯一）；
 * GET 同路径给运营视图（含占座订单号——公开座位图剥去该字段）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatAdminApiTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired MockMvc mockMvc;
    @Autowired CatalogProductFactory products;

    @Test
    void createSeatsRequiresAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sessions/sess-x/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"p\",\"positions\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin/sessions/sess-x/seats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesSeatsAndListsThemWithOrderRef() throws Exception {
        String product = products.productCode("seat-admin");
        String session = "sess-seat-admin-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/admin/sessions/" + session + "/seats")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + product + "\",\"positions\":["
                                + "{\"section\":\"A区\",\"row\":\"1\",\"seat\":\"1\"},"
                                + "{\"section\":\"A区\",\"row\":\"1\",\"seat\":\"2\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("IN_STOCK"))
                .andExpect(jsonPath("$[0].orderNo").doesNotExist())
                .andExpect(jsonPath("$[1].seat").value("2"));

        // 运营视图：新铺座位在列（铺座后可核对位置与状态）
        mockMvc.perform(get("/api/v1/admin/sessions/" + session + "/seats")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productCode").value(product));
    }

    @Test
    void duplicatePositionIsRejected() throws Exception {
        String product = products.productCode("seat-admin-dup");
        String session = "sess-seat-dup-" + UUID.randomUUID().toString().substring(0, 8);
        String position = "{\"section\":\"A区\",\"row\":\"1\",\"seat\":\"1\"}";

        mockMvc.perform(post("/api/v1/admin/sessions/" + session + "/seats")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + product + "\",\"positions\":[" + position + "]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/sessions/" + session + "/seats")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"" + product + "\",\"positions\":[" + position + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownProductIsRejectedAsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sessions/sess-unknown-prod/seats")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"prod-no-such\",\"positions\":["
                                + "{\"section\":\"A区\",\"row\":\"1\",\"seat\":\"1\"}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
