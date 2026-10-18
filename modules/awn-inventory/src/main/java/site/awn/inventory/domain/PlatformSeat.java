package site.awn.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 平台座位型票品：平台自营的逐座交易单元（唯一编号的座位，归属场次 + 商品编码）。
 * 独立模型：不复用挂牌票品 ticket_units（trading-model.md 关系要点 3）。
 * 并发抢座由仓储层守卫条件 UPDATE 裁决，本实体的 transitionTo 守卫状态机合法性。
 */
@Entity
@Table(name = "platform_seats")
public class PlatformSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_no", nullable = false, unique = true)
    private String seatNo;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "session_code", nullable = false)
    private String sessionCode;

    @Column(name = "section_name", nullable = false)
    private String sectionName;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_label", nullable = false)
    private String seatLabel;

    /** 当前占用该座位的订单号（预留 / 已售时填写，释放时清空） */
    @Column(name = "order_no")
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status;

    /** 座位坐标（S2 契约先行，V27）：可选，null = 缺失，前端回退网格；写入路径随数据管线落地 */
    @Column(name = "pos_x")
    private Double posX;

    /** 座位坐标（S2 契约先行，V27）：与 posX 成对，成对缺失或成对写入 */
    @Column(name = "pos_y")
    private Double posY;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected PlatformSeat() {
    }

    public PlatformSeat(String seatNo, String productCode, String sessionCode,
                        String sectionName, String rowLabel, String seatLabel) {
        this.seatNo = seatNo;
        this.productCode = productCode;
        this.sessionCode = sessionCode;
        this.sectionName = sectionName;
        this.rowLabel = rowLabel;
        this.seatLabel = seatLabel;
        this.status = SeatStatus.IN_STOCK;
    }

    /** 状态机守卫：非法迁移抛 InventoryConflictException（释放/下单/退款链路统一口径） */
    public void transitionTo(SeatStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InventoryConflictException(
                    "seat transition not allowed: " + status + " -> " + target + " (seat " + seatNo + ")");
        }
        this.status = target;
    }

    public void attachOrder(String orderNo) {
        this.orderNo = orderNo;
    }

    public void detachOrder() {
        this.orderNo = null;
    }

    public Long getId() {
        return id;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public String getSeatLabel() {
        return seatLabel;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Double getPosX() {
        return posX;
    }

    public Double getPosY() {
        return posY;
    }
}
