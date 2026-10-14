package site.awn.star.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 图鉴作品：名称、类型、年份、简介。 */
@Entity
@Table(name = "works")
public class Work {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type;

    private Integer year;

    private String description;

    @Column(nullable = false)
    private String status = STATUS_DRAFT;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Work() {
    }

    public Work(String title, String type, Integer year, String description) {
        this.title = title;
        this.type = type;
        this.year = year;
        this.description = description;
    }

    /** DRAFT → PUBLISHED 守卫；重复发布抛冲突 */
    public void publish() {
        if (STATUS_PUBLISHED.equals(status)) {
            throw new StarConflictException("work already published: " + id);
        }
        this.status = STATUS_PUBLISHED;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public Integer getYear() {
        return year;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
