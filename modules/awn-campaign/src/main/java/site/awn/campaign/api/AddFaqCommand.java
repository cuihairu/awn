package site.awn.campaign.api;

/**
 * 添加常见问题命令。问答均为展示型纯文本，不接受 HTML。
 */
public record AddFaqCommand(
        String question,
        String answer,
        Integer displayOrder
) {
}