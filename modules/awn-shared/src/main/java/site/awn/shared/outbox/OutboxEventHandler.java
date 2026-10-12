package site.awn.shared.outbox;

/**
 * Outbox 事件处理器：各业务模块实现并以 consumer 维度消费事件。
 * 实现方的 handle 与其业务副作用在同一数据库事务内执行（分发器保证），
 * 消费成功由 Inbox 去重记录（同一事件对同一消费者只生效一次）。
 */
public interface OutboxEventHandler {

    /** 消费者标识，写入 inbox_messages.consumer */
    String consumer();

    /** 是否消费该事件类型 */
    boolean supports(String eventType);

    /** 处理事件；抛出异常将触发按退避重试，超上限转 FAILED */
    void handle(OutboxMessage message);
}
