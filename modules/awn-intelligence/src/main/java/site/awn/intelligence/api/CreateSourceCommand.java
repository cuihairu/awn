package site.awn.intelligence.api;

/** 注册情报来源（sourceNo 可空则自动生成；适配器按 sourceNo 绑定） */
public record CreateSourceCommand(String sourceNo, String name, String kind, int trustTier) {
}
