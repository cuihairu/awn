package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.order.domain.IdempotencyConflictException;
import site.awn.order.domain.OrderConflictException;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.shared.error.ErrorCode;
import site.awn.support.AbstractPostgresIntegrationTest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 01：统一异常体系与错误码。
 * 任意异常必须返回统一错误信封（docs/api/contracts.md），
 * 错误码与 HTTP 状态按契约映射，未预期异常不得泄露内部信息。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ApiExceptionHandlerTests extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void errorCodesHaveDistinctNamesAndValidStatuses() {
        Set<String> names = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(names.add(code.name())).as("错误码必须唯一: " + code).isTrue();
            assertThat(code.httpStatus()).as("HTTP 状态必须合法: " + code).isBetween(400, 599);
        }
        // 契约表中的码一个不少
        assertThat(names).containsExactlyInAnyOrder(
                "VALIDATION_FAILED", "NOT_FOUND", "ORDER_NOT_FOUND", "INVENTORY_POOL_NOT_FOUND",
                "EVENT_NOT_FOUND", "CAMPAIGN_NOT_FOUND",
                "PAYMENT_NOT_FOUND", "REFUND_NOT_FOUND",
                "ORDER_CONFLICT", "IDEMPOTENCY_CONFLICT", "INSUFFICIENT_INVENTORY",
                "INVENTORY_CONFLICT", "PAYMENT_CONFLICT", "PAYMENT_CALLBACK_REJECTED",
                "REFUND_CONFLICT", "CONFLICT", "RATE_LIMITED", "INTERNAL_ERROR",
                "SESSION_NOT_FOUND", "VENUE_NOT_FOUND", "PERSON_NOT_FOUND", "WORK_NOT_FOUND",
                "WAITLIST_NOT_FOUND", "WAITLIST_CONFLICT",
                "DISPUTE_NOT_FOUND", "DISPUTE_CONFLICT", "SEAT_NOT_FOUND");
        assertThat(Arrays.stream(ErrorCode.values()).count()).isEqualTo(27);
    }

    @Test
    void domainExceptionsMapToContractCodes() throws Exception {
        mockMvc.perform(get("/internal/error/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("quantity must be positive"));

        mockMvc.perform(get("/internal/error/insufficient-inventory"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));

        mockMvc.perform(get("/internal/error/pool-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_POOL_NOT_FOUND"));

        mockMvc.perform(get("/internal/error/inventory-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"));

        mockMvc.perform(get("/internal/error/order-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/internal/error/order-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));

        mockMvc.perform(get("/internal/error/idempotency-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void unreadableBodyReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/internal/error/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("request body is not readable"));
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        String body = mockMvc.perform(post("/internal/error/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"", "note":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"name\":\"must not be blank\"")
                .contains("\"note\":\"size must be between 0 and 5\"");
    }

    @Test
    void unexpectedExceptionHidesInternalDetails() throws Exception {
        String body = mockMvc.perform(get("/internal/error/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andReturn().getResponse().getContentAsString();

        // 不泄露异常原文与堆栈
        assertThat(body).doesNotContain("vault_secrets");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at site.awn");
    }

    @TestConfiguration
    static class ProbeControllerConfig {

        @Bean
        ExceptionProbeController exceptionProbeController() {
            return new ExceptionProbeController();
        }
    }

    /** 仅测试用：逐类触发异常，探测信封映射 */
    @RestController
    static class ExceptionProbeController {

        @GetMapping("/internal/error/validation")
        public String validation() {
            throw new IllegalArgumentException("quantity must be positive");
        }

        @GetMapping("/internal/error/insufficient-inventory")
        public String insufficientInventory() {
            throw new InsufficientInventoryException("insufficient inventory for pool p1");
        }

        @GetMapping("/internal/error/pool-not-found")
        public String poolNotFound() {
            throw new PoolNotFoundException("pool not found: p1");
        }

        @GetMapping("/internal/error/inventory-conflict")
        public String inventoryConflict() {
            throw new InventoryConflictException("cannot confirm released reservation: r1");
        }

        @GetMapping("/internal/error/order-not-found")
        public String orderNotFound() {
            throw new OrderNotFoundException("ORD-x");
        }

        @GetMapping("/internal/error/order-conflict")
        public String orderConflict() {
            throw new OrderConflictException("cancel not allowed from status PAID");
        }

        @GetMapping("/internal/error/idempotency-conflict")
        public String idempotencyConflict() {
            throw new IdempotencyConflictException("idempotency key already used by a different request: k1");
        }

        @GetMapping("/internal/error/unexpected")
        public String unexpected() {
            throw new IllegalStateException("SELECT vault_secrets FROM hidden_table");
        }

        @PostMapping("/internal/error/echo")
        public java.util.Map<String, Object> echo(
                @RequestBody java.util.Map<String, Object> body) {
            return body;
        }

        @PostMapping("/internal/error/validate")
        public Map<String, String> validate(@Valid @RequestBody ValidationProbeRequest request) {
            return Map.of("name", request.name());
        }
    }

    record ValidationProbeRequest(
            @NotBlank String name,
            @Size(max = 5) String note
    ) {
    }
}
