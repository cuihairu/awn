package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开座位图接口集成测试（真实 PostgreSQL）：
 * 场次可见性 = 父活动可见性（与 /sessions/{id} 同口径，未知场次或
 * 草稿活动一律 404 SESSION_NOT_FOUND，草稿座位配置不泄露到公开面）；
 * 载荷只含编号与状态，不含 orderNo（占座订单号不进公开面）。
 *
 * <p>测试库跨用例持久：断言只做成员判断，不做全局计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatmapApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EventFacade events;
    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 造活动 + 场次；publish=false 时留在草稿态，验证公开面不可见 */
    private SessionView eventWithSession(String suffix, boolean publish) {
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", null, start, start.plusSeconds(7200), venue.id()));
        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, start.plusSeconds(7200)));
        if (publish) {
            events.publishEvent(event.eventCode());
        }
        return session;
    }

    @Test
    void seatmapListsSeatsForPublicSession() throws Exception {
        SessionView session = eventWithSession(uid(), true);
        String productCode = products.productCode("seat-" + uid());
        inventory.createSeats(new CreateSeatsCommand(productCode, session.sessionCode(), List.of(
                new CreateSeatsCommand.SeatPosition("A区", "1", "1"),
                new CreateSeatsCommand.SeatPosition("A区", "1", "2"))));

        mvc.perform(get("/api/v1/sessions/{id}/seatmap", session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].seatNo").isNotEmpty())
                .andExpect(jsonPath("$[*].productCode", hasItem(productCode)))
                .andExpect(jsonPath("$[*].section", hasItem("A区")))
                .andExpect(jsonPath("$[*].status", hasItem("IN_STOCK")))
                .andExpect(jsonPath("$[*].sessionCode").doesNotExist())
                .andExpect(jsonPath("$[*].orderNo").doesNotExist());
    }

    /** S2 契约（seatmap-3d-plan.md §3）：公开面透出可选 x/y；坐标缺失显式 null，前端回退网格 */
    @Test
    void seatmapExposesExplicitNullCoordinatesUntilDataLands() throws Exception {
        SessionView session = eventWithSession(uid(), true);
        String productCode = products.productCode("seat-" + uid());
        inventory.createSeats(new CreateSeatsCommand(productCode, session.sessionCode(), List.of(
                new CreateSeatsCommand.SeatPosition("A区", "1", "1"))));

        mvc.perform(get("/api/v1/sessions/{id}/seatmap", session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].x").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].y").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void seatmapOfPublicSessionWithoutSeatsIsEmpty() throws Exception {
        SessionView session = eventWithSession(uid(), true);

        mvc.perform(get("/api/v1/sessions/{id}/seatmap", session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownSessionReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/sessions/999999999/seatmap"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void seatmapOfDraftEventSessionIsInvisible() throws Exception {
        SessionView session = eventWithSession(uid(), false);

        mvc.perform(get("/api/v1/sessions/{id}/seatmap", session.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
