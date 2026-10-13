package site.awn.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.identity.api.IdentityFacade;
import site.awn.identity.api.RegisterCommand;
import site.awn.identity.api.UserView;
import site.awn.identity.domain.InvalidCredentialsException;
import site.awn.identity.domain.SystemRole;
import site.awn.identity.domain.UserConflictException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * identity 模块：注册、凭据校验（登录）、角色校验。
 * 验收口径（module-boundaries.md）：注册 / 登录 / 角色校验 + 集成测试。
 * 口令只存 BCrypt 哈希；未知邮箱与错误口令返回同一错误（不泄露账号是否存在）。
 */
@SpringBootTest
class IdentityModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired IdentityFacade identity;

    @Test
    void registerPersistsUserAndRejectsDuplicateEmail() {
        String email = uniqueEmail();
        UserView user = identity.register(new RegisterCommand(email, "correct horse battery", "测试用户"));

        assertThat(user.id()).isPositive();
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.status()).isEqualTo("ACTIVE");
        assertThat(user.roles()).as("注册即授予默认 BUYER 角色").containsExactly("BUYER");

        // 同邮箱重复注册必须被拒（唯一约束语义）
        assertThatThrownBy(() -> identity.register(new RegisterCommand(email, "another-pass-123", "重复")))
                .isInstanceOf(UserConflictException.class)
                .hasMessageContaining(email);
    }

    @Test
    void registerRejectsWeakPasswordsAndMalformedEmails() {
        assertThatThrownBy(() -> identity.register(new RegisterCommand(uniqueEmail(), "short", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
        assertThatThrownBy(() -> identity.register(new RegisterCommand("not-an-email", "long-enough-pass", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void verifyCredentialsAcceptsCorrectPasswordOnly() {
        String email = uniqueEmail();
        long userId = identity.register(new RegisterCommand(email, "correct horse battery", null)).id();

        assertThat(identity.verifyCredentials(email, "correct horse battery")).isEqualTo(userId);

        // 错误口令与未知邮箱：同一异常同一消息，不泄露账号存在性
        InvalidCredentialsException wrongPassword = catchThrowableOfType(
                () -> identity.verifyCredentials(email, "wrong password"), InvalidCredentialsException.class);
        InvalidCredentialsException unknownEmail = catchThrowableOfType(
                () -> identity.verifyCredentials(uniqueEmail(), "whatever password"), InvalidCredentialsException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    void rolesAreGrantedAndCheckedIdempotently() {
        long userId = identity.register(new RegisterCommand(uniqueEmail(), "long-enough-pass", null)).id();

        assertThat(identity.hasRole(userId, SystemRole.ADMIN)).isFalse();
        UserView granted = identity.grantRole(userId, SystemRole.ADMIN);
        assertThat(granted.roles()).contains("BUYER", "ADMIN");
        assertThat(identity.hasRole(userId, SystemRole.ADMIN)).isTrue();

        // 重复授权幂等：不报错、不产生重复行
        UserView again = identity.grantRole(userId, SystemRole.ADMIN);
        assertThat(again.roles()).containsExactlyInAnyOrder("BUYER", "ADMIN");

        // 角色校验对未知用户返回 false 而不是抛出（授权检查不得放大故障）
        assertThat(identity.hasRole(-1L, SystemRole.ADMIN)).isFalse();
    }

    // ------------------------------------------------------------------

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 10) + "@example.com";
    }
}
