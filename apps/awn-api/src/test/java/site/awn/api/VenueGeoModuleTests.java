package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.SessionVenueView;
import site.awn.event.api.VenueView;
import site.awn.event.domain.SessionNotFoundException;
import site.awn.event.domain.VenueNotFoundException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 场次地理位置（docs/design/ui-system.md §6.1）模块契约：
 *
 * <p>地理归属场馆，场馆带城市/地址/坐标；场次可单独指定场馆（如巡回赛每场换城市），
 * 未指定时继承父活动场馆——继承与覆盖两条路径都要能读出同一份 geo 视图。
 * 坐标缺失是正常态（场馆信息不全），不是错误。
 */
@SpringBootTest
class VenueGeoModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired EventFacade events;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private EventView eventWithVenue(String suffix, VenueView venue) {
        Instant start = Instant.now().plusSeconds(3600);
        return events.createEvent(new CreateEventCommand(
                "e-" + suffix, "巡回赛", null, start, start.plusSeconds(7200),
                venue == null ? null : venue.id()));
    }

    private SessionView session(String suffix, EventView event) {
        Instant start = event.startTime();
        return events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, start.plusSeconds(7200)));
    }

    @Test
    void venueCarriesCityAddressAndCoordinates() {
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + uid(), "上海体育馆", "上海", "徐汇区零陵路", 31.1885, 121.4380));

        assertThat(venue.city()).isEqualTo("上海");
        assertThat(venue.address()).isEqualTo("徐汇区零陵路");
        assertThat(venue.latitude()).isEqualTo(31.1885);
        assertThat(venue.longitude()).isEqualTo(121.4380);
    }

    @Test
    void coordinatesStayNullWhenTheVenueHasNoCoordinatesYet() {
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + uid(), "待定场馆", "北京", null));

        assertThat(venue.city()).isEqualTo("北京");
        assertThat(venue.address()).isNull();
        assertThat(venue.latitude()).isNull();
        assertThat(venue.longitude()).isNull();
    }

    @Test
    void sessionGeoInheritsTheEventVenueWhenNotOverridden() {
        String suffix = uid();
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "北京工人体育场", "北京", "朝阳工人体育场北路", 39.9308, 116.4435));
        EventView event = eventWithVenue(suffix, venue);
        SessionView session = session(suffix, event);

        SessionVenueView geo = events.sessionVenue(session.sessionCode());

        assertThat(geo).isNotNull();
        assertThat(geo.venueCode()).isEqualTo(venue.venueCode());
        assertThat(geo.city()).isEqualTo("北京");
        assertThat(geo.latitude()).isEqualTo(39.9308);
    }

    @Test
    void sessionVenueOverridesTheEventVenue() {
        String suffix = uid();
        VenueView home = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-a", "主赛场", "北京", "朝阳", 39.9308, 116.4435));
        VenueView onTour = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-b", "上海站", "上海", "徐汇", 31.1885, 121.4380));
        EventView event = eventWithVenue(suffix, home);
        SessionView session = session(suffix, event);

        events.attachSessionVenue(session.sessionCode(), onTour.venueCode());

        SessionVenueView geo = events.sessionVenue(session.sessionCode());
        assertThat(geo.venueCode()).isEqualTo(onTour.venueCode());
        assertThat(geo.city()).isEqualTo("上海");
    }

    @Test
    void sessionWithoutAnyVenueReportsNoGeo() {
        String suffix = uid();
        EventView event = eventWithVenue(suffix, null);
        SessionView session = session(suffix, event);

        assertThat(events.sessionVenue(session.sessionCode())).isNull();
    }

    @Test
    void detachingTheSessionVenueFallsBackToTheEventVenue() {
        String suffix = uid();
        VenueView home = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-a", "主赛场", "北京", null, null, null));
        VenueView onTour = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-b", "上海站", "上海", null, null, null));
        EventView event = eventWithVenue(suffix, home);
        SessionView session = session(suffix, event);

        events.attachSessionVenue(session.sessionCode(), onTour.venueCode());
        events.detachSessionVenue(session.sessionCode());

        assertThat(events.sessionVenue(session.sessionCode()).venueCode())
                .isEqualTo(home.venueCode());
    }

    @Test
    void venuesAreListedForTheOpsConsole() {
        String code = "v-" + uid();
        events.createVenue(new CreateVenueCommand(code, "列表场馆", "广州", "天河路", 23.1291, 113.2644));

        assertThat(events.venues())
                .extracting(VenueView::venueCode)
                .contains(code);
    }

    @Test
    void unknownSessionOrVenueIsRejected() {
        String suffix = uid();
        EventView event = eventWithVenue(suffix, null);
        SessionView session = session(suffix, event);

        assertThatThrownBy(() -> events.attachSessionVenue("nope-" + suffix, "v-nope"))
                .isInstanceOf(SessionNotFoundException.class);
        assertThatThrownBy(() -> events.attachSessionVenue(session.sessionCode(), "v-nope"))
                .isInstanceOf(VenueNotFoundException.class);
        assertThatThrownBy(() -> events.detachSessionVenue("nope-" + suffix))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void publicSessionCarriesResolvedGeo() {
        String suffix = uid();
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "北京工人体育场", "北京", "朝阳工人体育场北路", 39.9308, 116.4435));
        EventView event = eventWithVenue(suffix, venue);
        events.publishEvent(event.eventCode());
        SessionView session = session(suffix, event);

        SessionView view = events.publicSession(session.id());
        assertThat(view.geo()).isNotNull();
        assertThat(view.geo().venueCode()).isEqualTo(venue.venueCode());
        assertThat(view.geo().venueName()).isEqualTo("北京工人体育场");
        assertThat(view.geo().city()).isEqualTo("北京");
        assertThat(view.geo().latitude()).isEqualTo(39.9308);
    }

    @Test
    void publicSessionGeoPrefersTheSessionVenueOverride() {
        String suffix = uid();
        VenueView home = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-a", "主赛场", "北京", null, null, null));
        VenueView onTour = events.createVenue(new CreateVenueCommand(
                "v-" + suffix + "-b", "上海站", "上海", "徐汇", 31.1885, 121.4380));
        EventView event = eventWithVenue(suffix, home);
        events.publishEvent(event.eventCode());
        SessionView session = session(suffix, event);
        events.attachSessionVenue(session.sessionCode(), onTour.venueCode());

        assertThat(events.publicSession(session.id()).geo().venueCode())
                .isEqualTo(onTour.venueCode());
    }

    @Test
    void publicSessionWithoutAnyVenueHasNullGeo() {
        String suffix = uid();
        EventView event = eventWithVenue(suffix, null);
        events.publishEvent(event.eventCode());
        SessionView session = session(suffix, event);

        assertThat(events.publicSession(session.id()).geo()).isNull();
    }

    @Test
    void publicSessionsListCarriesGeoForEachSession() {
        String suffix = uid();
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "上海大舞台", "上海", "天钥桥路", 31.1885, 121.4380));
        EventView event = eventWithVenue(suffix, venue);
        events.publishEvent(event.eventCode());
        session(suffix, event);

        java.util.List<SessionView> publicList = events.publicSessions(event.id());
        assertThat(publicList).isNotEmpty();
        assertThat(publicList).allMatch(s -> s.geo() != null && "上海".equals(s.geo().city()));
    }
}