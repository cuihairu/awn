package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.awn.event.api.EventFacade;
import site.awn.event.api.SessionView;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.intelligence.api.PricePointView;

import java.util.List;

/**
 * 公共情报接口（组合根）：活动维度的价格走势。
 *
 * <p>价格历史按场次归集，因此先解析活动下的场次代码，再查情报模块；
 * 活动未知或未公开 → 404（EVENT_NOT_FOUND）。
 */
@RestController
public class PublicIntelligenceController {

    private final EventFacade events;
    private final IntelligenceFacade intelligence;

    public PublicIntelligenceController(EventFacade events, IntelligenceFacade intelligence) {
        this.events = events;
        this.intelligence = intelligence;
    }

    /** 活动下所有场次的价格历史，按观测时间倒序；活动未知或未公开 → 404 */
    @GetMapping("/api/v1/intelligence/events/{eventId}/prices")
    public List<PricePointView> eventPrices(@PathVariable Long eventId) {
        events.eventById(eventId);
        List<String> sessionCodes = events.sessions(eventId).stream()
                .map(SessionView::sessionCode)
                .toList();
        return intelligence.priceHistoryOfSessions(sessionCodes);
    }
}
