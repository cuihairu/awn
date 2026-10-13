package site.awn.intelligence.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.intelligence.api.CollectAllReport;
import site.awn.intelligence.api.CreateSourceCommand;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.intelligence.api.PricePointView;
import site.awn.intelligence.api.SourceView;
import site.awn.intelligence.domain.CollectReport;
import site.awn.intelligence.domain.DataSource;
import site.awn.intelligence.domain.IntelligenceAdapter;
import site.awn.intelligence.domain.NormalizedObservation;
import site.awn.intelligence.domain.ObservationDraft;
import site.awn.intelligence.domain.PriceHistoryEntry;
import site.awn.intelligence.domain.RawObservation;
import site.awn.intelligence.domain.SourceKind;
import site.awn.intelligence.domain.SourceNotFoundException;
import site.awn.intelligence.infrastructure.DataSourceRepository;
import site.awn.intelligence.infrastructure.NormalizedObservationRepository;
import site.awn.intelligence.infrastructure.PriceHistoryRepository;
import site.awn.intelligence.infrastructure.RawObservationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 情报用例实现。
 * 采集流程：适配器拉取 → 原始落库 → 逐条标准化 + 质量检查 → 通过者进价格历史。
 * 采集失败被完整记录在 CollectReport 中返回，绝不向上抛出——
 * 情报采集失败不影响交易（隔离约束：本模块也不写任何交易表）。
 */
@Service
public class IntelligenceFacadeImpl implements IntelligenceFacade {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceFacadeImpl.class);

    private final DataSourceRepository sources;
    private final RawObservationRepository raws;
    private final NormalizedObservationRepository normalized;
    private final PriceHistoryRepository history;
    private final Map<String, IntelligenceAdapter> adaptersBySourceNo;

    public IntelligenceFacadeImpl(DataSourceRepository sources,
                                  RawObservationRepository raws,
                                  NormalizedObservationRepository normalized,
                                  PriceHistoryRepository history,
                                  List<IntelligenceAdapter> adapters) {
        this.sources = sources;
        this.raws = raws;
        this.normalized = normalized;
        this.history = history;
        Map<String, IntelligenceAdapter> index = new HashMap<>();
        for (IntelligenceAdapter adapter : adapters) {
            index.put(adapter.sourceNo(), adapter);
        }
        this.adaptersBySourceNo = Map.copyOf(index);
    }

    @Override
    @Transactional
    public SourceView registerSource(CreateSourceCommand command) {
        SourceKind kind;
        try {
            kind = SourceKind.valueOf(command.kind());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown source kind: " + command.kind());
        }
        DataSource source = new DataSource(
                command.sourceNo() == null || command.sourceNo().isBlank()
                        ? "SRC-" + UUID.randomUUID().toString().substring(0, 12)
                        : command.sourceNo(),
                command.name(), kind, command.trustTier());
        return toView(sources.save(source));
    }

    @Override
    @Transactional(readOnly = true)
    public SourceView sourceOf(String sourceNo) {
        return sources.findBySourceNo(sourceNo).map(this::toView)
                .orElseThrow(() -> new SourceNotFoundException("source not found: " + sourceNo));
    }

    @Override
    @Transactional
    public CollectReport collect(String sourceNo) {
        DataSource source = sources.findBySourceNo(sourceNo)
                .orElseThrow(() -> new SourceNotFoundException("source not found: " + sourceNo));
        IntelligenceAdapter adapter = adaptersBySourceNo.get(sourceNo);
        if (adapter == null) {
            return new CollectReport(sourceNo, 0, 0, 0, "no adapter bound for source: " + sourceNo);
        }
        List<ObservationDraft> drafts;
        try {
            drafts = adapter.fetch();
        } catch (Exception e) {
            // 采集失败：记录并返回，不影响交易
            log.warn("intelligence collect failed for {}: {}", sourceNo, e.getMessage());
            return new CollectReport(sourceNo, 0, 0, 0, e.getMessage());
        }
        int ok = 0;
        int rejected = 0;
        for (ObservationDraft draft : drafts) {
            // 来源内去重：同一外部事件引用只采集一次（ UNIQUE(source_id, ref) 兜底）
            if (raws.existsBySourceIdAndSourceEventRef(source.getId(), draft.externalRef())) {
                continue;
            }
            RawObservation raw = new RawObservation(source.getId(), draft.externalRef(), draft.productRef(),
                    draft.observedAt(), draft.price(), draft.currency(), draft.availabilityLabel(),
                    draft.rawPayload());
            raws.save(raw);
            raws.flush(); // 标准化引用原始观察主键
            java.util.Optional<NormalizedObservation> result = raw.normalize(source);
            if (result.isEmpty()) {
                rejected++;
                continue;
            }
            normalized.save(result.get());
            history.save(new PriceHistoryEntry(result.get(), null));
            ok++;
        }
        return new CollectReport(sourceNo, drafts.size(), ok, rejected, null);
    }

    /**
     * 全量采集（调度器入口）：逐个启用来源调用 {@link #collect}，单源失败不中断整轮。
     * collect 已把适配器失败记录在报告中不抛出；整轮一个事务，某源持久化异常会回滚本轮、
     * 下轮重采（来源内去重保证幂等）。停用来源跳过；手动单源采集不受限。
     */
    @Override
    @Transactional
    public CollectAllReport collectAll() {
        List<CollectReport> reports = new ArrayList<>();
        int failed = 0;
        for (DataSource source : sources.findAll()) {
            if (!source.isEnabled()) {
                continue;
            }
            CollectReport report = collect(source.getSourceNo());
            if (report.failed()) {
                failed++;
            }
            reports.add(report);
        }
        return new CollectAllReport(reports.size(), failed, List.copyOf(reports));
    }

    @Override
    @Transactional
    public SourceView disableSource(String sourceNo) {
        DataSource source = sources.findBySourceNo(sourceNo)
                .orElseThrow(() -> new SourceNotFoundException("source not found: " + sourceNo));
        source.disable();
        return toView(sources.save(source));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricePointView> priceHistoryOf(String productCode) {
        Map<Long, DataSource> sourceCache = new HashMap<>();
        return history.findByProductCodeOrderByObservedAtDesc(productCode).stream()
                .map(entry -> {
                    DataSource source = sourceCache.computeIfAbsent(entry.getSourceId(),
                            id -> sources.findById(id).orElse(null));
                    if (source == null) {
                        return null;
                    }
                    return new PricePointView(entry.getPrice(), entry.getCurrency(), entry.getAvailability(),
                            source.getSourceNo(), source.getName(), source.getKind().name(),
                            entry.getTrustTier(), entry.getObservedAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ------------------------------------------------------------------

    private SourceView toView(DataSource s) {
        return new SourceView(s.getSourceNo(), s.getName(), s.getKind().name(), s.getTrustTier(), s.isEnabled());
    }
}
