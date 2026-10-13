package site.awn.notification.domain;

/** 通知投递结果：通道受理成功 / 失败（失败留痕，不重试于本模块内） */
public enum NotificationStatus {
    SENT,
    FAILED
}
