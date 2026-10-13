package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.event.domain.EventConflictException;
import site.awn.event.domain.EventNotFoundException;
import site.awn.event.domain.EventStatus;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.intelligence.domain.DataSource;
import site.awn.intelligence.domain.SourceKind;
import site.awn.intelligence.infrastructure.DataSourceRepository;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公共活动 / 场次 / 情报接口集成测试（真实 PostgreSQL）：
 * 端点 200 返回视图，未知资源 404 + 统一错误信封，
 * 草稿内容不出现在公开面（列表、详情、场次、活动维度价格）。
 *
 * <p>测试库跨用例持久：列表类断言只做成员判断，不做全局计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicEventApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EventFacade events;
    @Autowired IntelligenceFacade intelligence;
    @Autowired DataSourceRepository sources;
    @Autowired JdbcTemplate jdbc;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 造活动 + N 个场次；publish=false 时留在草稿态，验证公开面不可见 */
    private EventView eventWithSessions(String suffix, int sessionCount, boolean publish) {
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", null, start, start.plusSeconds(7200), venue.id()));
        for (int i = 0; i < sessionCount; i++) {
            events.createSession(new CreateSessionCommand(
                    "s-" + suffix + "-" + i, event.id(), "第" + i + "场",
                    start, start.plusSeconds(7200)));
        }
        return publish ? events.publishEvent(event.eventCode()) : event;
    }

    /** 价格点入库：采集链路不填 session_code，公开端点按场次归集 */
    private Long registerSource(String suffix) {
        return sources.save(new DataSource(
                "SRC-" + suffix, "测试渠道", SourceKind.OFFICIAL_PUBLISHED, 1)).getId();
    }

    private void insertPrice(Long sourceId, String sessionCode, String productCode,
                             long price, String availability, Instant observedAt) {
        // timestamptz 列必须显式给类型，否则驱动无法推断 Instant
        jdbc.update("""
                INSERT INTO intelligence.price_history
                  (source_id, product_code, session_code, price, currency, availability, observed_at, trust_tier)
                VALUES (?, ?, ?, ?, 'CNY', ?, ?, 1)
                """, new Object[]{sourceId, productCode, sessionCode, price, availability,
                        observedAt.atOffset(ZoneOffset.UTC)},
                new int[]{Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.BIGINT,
                        Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE});
    }

    @Test
    void listExposesOnlyPublishedEvents() throws Exception {
        String uid = uid();
        EventView published = eventWithSessions(uid + "-a", 1, true);
        EventView draft = eventWithSessions(uid + "-b", 1, false);

        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                // 测试库不截断：按唯一编码做成员判断，不数总数
                .andExpect(jsonPath("$[*].eventCode", hasItem(published.eventCode())))
                .andExpect(jsonPath("$[*].eventCode", not(hasItem(draft.eventCode()))))
                .andExpect(jsonPath("$[*].status", hasItem("PUBLISHED")));
    }

    @Test
    void publishedEventDetailReturnsView() throws Exception {
        EventView event = eventWithSessions(uid(), 2, true);

        mvc.perform(get("/api/v1/events/" + event.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.id()))
                .andExpect(jsonPath("$.eventCode").value(event.eventCode()))
                .andExpect(jsonPath("$.name").value("演唱会"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.venueId").value(event.venueId()))
                .andExpect(jsonPath("$.startTime").isNotEmpty())
                .andExpect(jsonPath("$.endTime").isNotEmpty());
    }

    @Test
    void unknownEventReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/events/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void draftEventIsInvisibleToPublicRead() throws Exception {
        EventView draft = eventWithSessions(uid(), 1, false);

        mvc.perform(get("/api/v1/events/" + draft.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void sessionDetailReturnsViewForPublishedEvent() throws Exception {
        EventView event = eventWithSessions(uid(), 2, true);
        SessionView session = events.sessions(event.id()).get(0);

        mvc.perform(get("/api/v1/sessions/" + session.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.id()))
                .andExpect(jsonPath("$.sessionCode").value(session.sessionCode()))
                .andExpect(jsonPath("$.eventId").value(event.id()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startTime").isNotEmpty());
    }

    @Test
    void unknownSessionReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/sessions/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void sessionOfDraftEventIsInvisible() throws Exception {
        EventView draft = eventWithSessions(uid(), 1, false);
        SessionView session = events.sessions(draft.id()).get(0);

        mvc.perform(get("/api/v1/sessions/" + session.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void eventPricesReturnTraceableHistoryOfItsSessions() throws Exception {
        String uid = uid();
        EventView event = eventWithSessions(uid, 2, true);
        var sessions = events.sessions(event.id());
        Long sourceId = registerSource(uid);
        Instant now = Instant.now();

        insertPrice(sourceId, sessions.get(0).sessionCode(), "p-1", 199900, "AVAILABLE", now);
        insertPrice(sourceId, sessions.get(1).sessionCode(), "p-1", 128000, "SOLD_OUT",
                now.minusSeconds(60));
        // 其他活动的价格点不得泄漏进来
        insertPrice(sourceId, "s-other-" + uid, "p-other", 1, "AVAILABLE", now);

        mvc.perform(get("/api/v1/intelligence/events/" + event.id() + "/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 按观测时间倒序
                .andExpect(jsonPath("$[0].price").value(199900))
                .andExpect(jsonPath("$[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].price").value(128000))
                .andExpect(jsonPath("$[1].availability").value("SOLD_OUT"))
                // 每条价格可溯源（来源、可信度、币种、观测时间）
                .andExpect(jsonPath("$[0].sourceNo").value("SRC-" + uid))
                .andExpect(jsonPath("$[0].kind").value("OFFICIAL_PUBLISHED"))
                .andExpect(jsonPath("$[0].trustTier").value(1))
                .andExpect(jsonPath("$[0].currency").value("CNY"))
                .andExpect(jsonPath("$[0].observedAt").isNotEmpty());
    }

    @Test
    void eventPricesIsEmptyWhenEventHasNoHistory() throws Exception {
        EventView event = eventWithSessions(uid(), 2, true);

        mvc.perform(get("/api/v1/intelligence/events/" + event.id() + "/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownEventPricesReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/intelligence/events/999999999/prices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void draftEventPricesReturnsNotFound() throws Exception {
        EventView draft = eventWithSessions(uid(), 1, false);

        mvc.perform(get("/api/v1/intelligence/events/" + draft.id() + "/prices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void publishEventGuardsStateMachine() {
        EventView draft = eventWithSessions(uid(), 1, false);

        assertThat(events.publishEvent(draft.eventCode()).status()).isEqualTo(EventStatus.PUBLISHED);
        // 重复发布是状态机冲突；未知活动直接 404
        assertThatThrownBy(() -> events.publishEvent(draft.eventCode()))
                .isInstanceOf(EventConflictException.class);
        assertThatThrownBy(() -> events.publishEvent("e-missing-" + uid()))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void publishedEventAppearsImmediatelyAfterPublishing() throws Exception {
        EventView draft = eventWithSessions(uid(), 1, false);
        assertThat(events.publishEvent(draft.eventCode()).status()).isEqualTo(EventStatus.PUBLISHED);

        mvc.perform(get("/api/v1/events/" + draft.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/intelligence/events/" + draft.id() + "/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
