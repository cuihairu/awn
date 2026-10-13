package site.awn.order.api;

import java.util.List;

/**
 * 报价命令（结算预览）：行与订单行同构。
 * 报价不预留库存、不落任何表、不产生订单，只按服务端口径计算金额。
 */
public record QuoteCommand(String currency, List<OrderLineCommand> lines) {

    public QuoteCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
