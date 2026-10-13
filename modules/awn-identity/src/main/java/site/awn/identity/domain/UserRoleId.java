package site.awn.identity.domain;

import java.io.Serializable;
import java.util.Objects;

/** UserRole 复合主键 */
public class UserRoleId implements Serializable {

    private Long userId;
    private String role;

    public UserRoleId() {
    }

    public UserRoleId(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }
}
