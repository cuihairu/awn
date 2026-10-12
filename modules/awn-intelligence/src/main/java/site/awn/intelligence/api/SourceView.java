package site.awn.intelligence.api;

/** 情报来源视图 */
public record SourceView(String sourceNo, String name, String kind, int trustTier, boolean enabled) {
}
