package site.awn.campaign.api;

/**
 * 添加专场活动引用命令。
 */
public record AddEventCommand(
        Long eventId,
        Integer displayOrder
) {
}
