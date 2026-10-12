package site.awn.inventory.domain;

/**
 * 流水类型。RESERVE 增加预留；CONFIRM 预留转已售；RELEASE 归还预留；ADJUST 人工校正（走审计）。
 */
public enum LedgerType {
    RESERVE,
    CONFIRM,
    RELEASE,
    ADJUST
}
