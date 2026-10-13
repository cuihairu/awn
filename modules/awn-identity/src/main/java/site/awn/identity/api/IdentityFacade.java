package site.awn.identity.api;

import site.awn.identity.domain.SystemRole;

import java.util.List;

/**
 * identity 模块对外接口：注册、凭据校验（登录）、角色授予与校验。
 * 边界：不直接修改订单和库存；HTTP 会话 / 令牌机制属 API 层后续工作，
 * 本模块只提供凭据验证与角色事实。
 */
public interface IdentityFacade {

    /**
     * 注册新用户：邮箱格式与口令强度校验，口令 BCrypt 哈希落库；
     * 注册即授予默认 BUYER 角色。同邮箱重复注册抛冲突。
     */
    UserView register(RegisterCommand command);

    /**
     * 凭据校验（登录）：通过返回 userId；
     * 未知邮箱与错误口令抛同一 InvalidCredentialsException（不泄露账号存在性）。
     */
    long verifyCredentials(String email, String password);

    /** 授予角色（幂等）：重复授予不报错、不产生重复行 */
    UserView grantRole(long userId, SystemRole role);

    /** 角色校验：未授予 / 用户不存在返回 false */
    boolean hasRole(long userId, SystemRole role);

    UserView userOf(long userId);
}
