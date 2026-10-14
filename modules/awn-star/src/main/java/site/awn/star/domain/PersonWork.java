package site.awn.star.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 人物 ↔ 作品关系行：一人在一部作品中的一种身份一行。
 * 不扁平化成两端 id——role_type（结构化身份）、role_name（自由文本，如「饰 李逍遥」）、
 * billing_order（署名次序）都挂在关系上。
 */
@Entity
@Table(name = "person_works")
public class PersonWork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(name = "role_type", nullable = false)
    private String roleType;

    @Column(name = "role_name", nullable = false)
    private String roleName = "";

    @Column(name = "billing_order")
    private Integer billingOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PersonWork() {
    }

    public PersonWork(Long personId, Long workId, String roleType,
                      String roleName, Integer billingOrder) {
        this.personId = personId;
        this.workId = workId;
        this.roleType = roleType;
        this.roleName = roleName;
        this.billingOrder = billingOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getPersonId() {
        return personId;
    }

    public Long getWorkId() {
        return workId;
    }

    public String getRoleType() {
        return roleType;
    }

    public String getRoleName() {
        return roleName;
    }

    public Integer getBillingOrder() {
        return billingOrder;
    }
}
