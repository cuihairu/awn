package site.awn.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/** 用户角色授予：(user_id, role) 联合主键，重复授权由主键幂等吸收 */
@Entity
@Table(name = "identity_user_roles")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "granted_at", insertable = false, updatable = false)
    private Instant grantedAt;

    protected UserRole() {
    }

    public UserRole(Long userId, SystemRole role) {
        this.userId = userId;
        this.role = role.name();
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}
