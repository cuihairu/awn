package site.awn.campaign.api;

/**
 * 添加须知条目命令。content 为纯文本（段落以空行分隔），不接受 HTML。
 */
public record AddRuleCommand(
        String title,
        String content,
        Integer displayOrder
) {
}