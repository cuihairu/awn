package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.api.auth.ApiTokenService;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ApiTokenService tokens;
    @Autowired OrderFacade orders;
    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;

    @Test
    void registrationAndLoginIssueVerifiedBearerTokens() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"a-strong-password\","
                + "\"displayName\":\"Auth test\"}";
        String registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roles[0]").value("BUYER"))
                .andReturn().getResponse().getContentAsString();
        String login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = login.replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(tokens.verify(token)).isPositive();
        assertThat(registration).contains("accessToken");
    }

    @Test
    void buyerEndpointsRejectMissingInvalidAndForeignIdentity() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-missing-auth")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String suffix = UUID.randomUUID().toString().substring(0, 10);
        String product = products.productCode(suffix);
        String pool = "auth-pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(pool, product, "auth-session-" + suffix, 2));
        var order = orders.createOrder(new CreateOrderCommand("auth-key-" + suffix, 42L, "CNY",
                List.of(new OrderLineCommand(pool, product, "Auth item", "auth-session-" + suffix,
                        null, 1, 10000, 0, 0))));

        mvc.perform(get("/api/v1/orders/" + order.orderNo())
                        .header("Authorization", "Bearer " + tokens.issue(43L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void orderCreationIgnoresCallerSuppliedUserId() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 10);
        String product = products.productCode(suffix);
        String pool = "auth-create-pool-" + suffix;
        String session = "auth-create-session-" + suffix;
        inventory.createPool(new CreatePoolCommand(pool, product, session, 2));
        String response = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "auth-create-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"currency\":\"CNY\",\"lines\":[{"
                                + "\"poolCode\":\"" + pool + "\",\"productId\":\"" + product + "\","
                                + "\"productNameSnapshot\":\"Auth item\",\"sessionId\":\"" + session + "\","
                                + "\"quantity\":1,\"unitPrice\":10000,\"discountAmount\":0,\"feeAmount\":0}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNo = response.replaceAll(".*\\\"orderNo\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(orders.ownerOf(orderNo)).isEqualTo(42L);
    }
}
