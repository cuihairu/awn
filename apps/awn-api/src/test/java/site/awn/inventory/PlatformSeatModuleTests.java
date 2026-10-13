package site.awn.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.catalog.domain.ProductNotFoundException;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.SeatView;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.SeatNotFoundException;
import site.awn.inventory.domain.SeatStatus;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台座位型票品（独立模型，todo「完全没有」档验收口径）：
 * 逐座状态机 IN_STOCK → RESERVED → SOLD → DELIVERED，INVALID 终态；
 * 并发抢座守卫条件 UPDATE 只放行一单；同场次同物理位置唯一。
 * 禁止复用挂牌票品 ticket_units（其外键挂 listings，语义是「卖家的票」）。
 */
@SpringBootTest
class PlatformSeatModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private List<CreateSeatsCommand.SeatPosition> positions(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new CreateSeatsCommand.SeatPosition("A区", "1", String.valueOf(i)))
                .toList();
    }

    @Test
    void createSeatsListsPositionsWithStockStatus() {
        String productCode = products.productCode("seat-" + uid());
        String sessionCode = "sess-" + uid();

        List<SeatView> created = inventory.createSeats(
                new CreateSeatsCommand(productCode, sessionCode, positions(3)));

        assertThat(created).hasSize(3);
        assertThat(created).allMatch(seat -> seat.status() == SeatStatus.IN_STOCK);
        assertThat(created).allMatch(seat -> seat.orderNo() == null);
        // 座位号唯一，物理位置完整保留
        assertThat(created.stream().map(SeatView::seatNo).distinct()).hasSize(3);
        assertThat(created.stream().map(SeatView::seat))
                .containsExactly("1", "2", "3");

        List<SeatView> listed = inventory.seatsOfProduct(productCode);
        assertThat(listed).hasSize(3);
        assertThat(listed.stream().map(SeatView::seatNo))
                .containsExactlyInAnyOrderElementsOf(
                        created.stream().map(SeatView::seatNo).toList());
    }

    @Test
    void createSeatsValidatesProductAndRejectsDuplicatePosition() {
        String sessionCode = "sess-" + uid();

        // 商品编码必须真实存在（与建池同口径）
        assertThatThrownBy(() -> inventory.createSeats(
                new CreateSeatsCommand("missing-" + uid(), sessionCode, positions(1))))
                .isInstanceOf(ProductNotFoundException.class);

        // 同批次内重复位置拒绝
        String productCode = products.productCode("seat-" + uid());
        CreateSeatsCommand.SeatPosition same = new CreateSeatsCommand.SeatPosition("A区", "1", "9");
        assertThatThrownBy(() -> inventory.createSeats(
                new CreateSeatsCommand(productCode, sessionCode, List.of(same, same))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate seat position");

        // 同场次同物理位置已被占用：换一个商品也拒绝（一个座位不能属于两个商品）
        inventory.createSeats(new CreateSeatsCommand(productCode, sessionCode, List.of(same)));
        String otherProduct = products.productCode("seat-" + uid());
        assertThatThrownBy(() -> inventory.createSeats(
                new CreateSeatsCommand(otherProduct, sessionCode, List.of(same))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seat position already exists");
    }

    @Test
    void concurrentReserveOfSameSeatAllowsExactlyOneWinner() throws Exception {
        String productCode = products.productCode("seat-" + uid());
        String seatNo = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(), positions(1)))
                .get(0).seatNo();

        int threads = 16;
        var latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        var success = new AtomicInteger();
        var conflict = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int seq = i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    inventory.reserveSeat(seatNo, "ORD-" + seq);
                    success.incrementAndGet();
                } catch (InventoryConflictException e) {
                    conflict.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(success.get()).as("并发抢座只放行一单").isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);

        SeatView seat = inventory.seat(seatNo);
        assertThat(seat.status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(seat.orderNo()).startsWith("ORD-");
    }

    @Test
    void reserveConfirmDeliverLifecyclePreservesOrderNo() {
        String productCode = products.productCode("seat-" + uid());
        String seatNo = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(), positions(1)))
                .get(0).seatNo();
        String orderNo = "ORD-" + uid();

        assertThat(inventory.reserveSeat(seatNo, orderNo))
                .extracting(SeatView::status, SeatView::orderNo)
                .containsExactly(SeatStatus.RESERVED, orderNo);

        // 确认出票：订单号保留（对账与交付追溯）
        assertThat(inventory.confirmSeat(seatNo))
                .extracting(SeatView::status, SeatView::orderNo)
                .containsExactly(SeatStatus.SOLD, orderNo);

        assertThat(inventory.deliverSeat(seatNo).status()).isEqualTo(SeatStatus.DELIVERED);

        // DELIVERED 是终态：再交付 / 释放 / 作废一律拒绝
        assertThatThrownBy(() -> inventory.deliverSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: DELIVERED");
        assertThatThrownBy(() -> inventory.releaseSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class);
        assertThatThrownBy(() -> inventory.invalidateSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class);
    }

    @Test
    void releaseReturnsSeatToStockAndAllowsReReservation() {
        String productCode = products.productCode("seat-" + uid());
        String seatNo = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(), positions(1)))
                .get(0).seatNo();

        inventory.reserveSeat(seatNo, "ORD-first-" + uid());

        SeatView released = inventory.releaseSeat(seatNo);
        assertThat(released.status()).isEqualTo(SeatStatus.IN_STOCK);
        assertThat(released.orderNo()).isNull();

        // 释放后可被下一个订单重新预留
        String secondOrder = "ORD-second-" + uid();
        assertThat(inventory.reserveSeat(seatNo, secondOrder))
                .extracting(SeatView::status, SeatView::orderNo)
                .containsExactly(SeatStatus.RESERVED, secondOrder);
    }

    @Test
    void illegalTransitionsRejectedWithPreciseMessage() {
        String productCode = products.productCode("seat-" + uid());
        String seatNo = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(), positions(1)))
                .get(0).seatNo();

        // 未预留直接确认 / 释放 / 交付：状态机拒绝
        assertThatThrownBy(() -> inventory.confirmSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: IN_STOCK -> SOLD");
        assertThatThrownBy(() -> inventory.releaseSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: IN_STOCK -> IN_STOCK");
        assertThatThrownBy(() -> inventory.deliverSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: IN_STOCK -> DELIVERED");

        // 已售座位不得作废（出票后走退款 / 异常流程，不静默作废）
        inventory.reserveSeat(seatNo, "ORD-" + uid());
        inventory.confirmSeat(seatNo);
        assertThatThrownBy(() -> inventory.invalidateSeat(seatNo))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: SOLD -> INVALID");
    }

    @Test
    void invalidateIsTerminalFromStockOrReserved() {
        String productCode = products.productCode("seat-" + uid());
        String inStockSeat = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(), positions(1)))
                .get(0).seatNo();
        String reservedSeat = inventory.createSeats(
                new CreateSeatsCommand(productCode, "sess-" + uid(),
                        List.of(new CreateSeatsCommand.SeatPosition("B区", "1", "1"))))
                .get(0).seatNo();

        assertThat(inventory.invalidateSeat(inStockSeat).status()).isEqualTo(SeatStatus.INVALID);

        inventory.reserveSeat(reservedSeat, "ORD-" + uid());
        SeatView invalidated = inventory.invalidateSeat(reservedSeat);
        assertThat(invalidated.status()).isEqualTo(SeatStatus.INVALID);
        assertThat(invalidated.orderNo()).isNull();

        // INVALID 是终态
        assertThatThrownBy(() -> inventory.reserveSeat(reservedSeat, "ORD-other-" + uid()))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("transition not allowed: INVALID -> RESERVED");
    }

    @Test
    void unknownSeatOperationsThrowNotFound() {
        String seatNo = "SEAT-missing-" + uid();

        assertThatThrownBy(() -> inventory.seat(seatNo))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining(seatNo);
        assertThatThrownBy(() -> inventory.reserveSeat(seatNo, "ORD-" + uid()))
                .isInstanceOf(SeatNotFoundException.class);
        assertThatThrownBy(() -> inventory.confirmSeat(seatNo))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void seatStatusMatrixIsExhaustivelyGuarded() {
        for (SeatStatus from : SeatStatus.values()) {
            for (SeatStatus to : SeatStatus.values()) {
                boolean legal = switch (from) {
                    case IN_STOCK -> to == SeatStatus.RESERVED || to == SeatStatus.INVALID;
                    case RESERVED -> to == SeatStatus.SOLD || to == SeatStatus.IN_STOCK
                            || to == SeatStatus.INVALID;
                    case SOLD -> to == SeatStatus.DELIVERED;
                    case DELIVERED, INVALID -> false;
                };
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(legal);
            }
        }
    }
}
