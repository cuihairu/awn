package site.awn.identity.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.identity.api.IdentityFacade;
import site.awn.identity.api.RegisterCommand;
import site.awn.identity.api.UserView;
import site.awn.identity.domain.InvalidCredentialsException;
import site.awn.identity.domain.SystemRole;
import site.awn.identity.domain.User;
import site.awn.identity.domain.UserConflictException;
import site.awn.identity.domain.UserNotFoundException;
import site.awn.identity.domain.UserRole;
import site.awn.identity.infrastructure.UserRepository;
import site.awn.identity.infrastructure.UserRoleRepository;

import java.util.List;
import java.util.regex.Pattern;

/**
 * identity 用例实现。
 * 安全语义：
 * - 口令只存 BCrypt 哈希（spring-security-crypto），明文不落库不出模块；
 * - 未知邮箱与错误口令抛同一异常同一消息，不泄露账号存在性；
 * - 角色授予幂等（(user_id, role) 主键吸收重复）。
 */
@Service
public class IdentityFacadeImpl implements IdentityFacade {

    /** 简单邮箱形状校验：local@domain.tld，无空白 */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public IdentityFacadeImpl(UserRepository users, UserRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    @Transactional
    public UserView register(RegisterCommand command) {
        String email = normalizeEmail(command.email());
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("malformed email: " + command.email());
        }
        if (command.password() == null || command.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.existsByEmail(email)) {
            throw new UserConflictException("email already registered: " + email);
        }
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode(command.password()),
                command.displayName()));
        roles.save(new UserRole(user.getId(), SystemRole.BUYER));
        return toView(user, List.of(SystemRole.BUYER.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public long verifyCredentials(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email)).orElseThrow(InvalidCredentialsException::new);
        if (!user.canLogin() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user.getId();
    }

    @Override
    @Transactional
    public UserView grantRole(long userId, SystemRole role) {
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException("user not found: " + userId));
        if (!roles.existsByUserIdAndRole(user.getId(), role.name())) {
            roles.save(new UserRole(user.getId(), role));
        }
        return userOf(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(long userId, SystemRole role) {
        return roles.existsByUserIdAndRole(userId, role.name());
    }

    @Override
    @Transactional(readOnly = true)
    public UserView userOf(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException("user not found: " + userId));
        List<String> granted = roles.findByUserIdOrderByRoleAsc(user.getId()).stream()
                .map(UserRole::getRole)
                .toList();
        return toView(user, granted);
    }

    // ------------------------------------------------------------------

    /** 邮箱大小写归一（大写邮箱视为同一账号） */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static UserView toView(User user, List<String> roles) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getStatus().name(), List.copyOf(roles));
    }
}
