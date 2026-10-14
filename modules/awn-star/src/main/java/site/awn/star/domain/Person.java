package site.awn.star.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 图鉴人物。照片红线：photoUrl 必须有 photoSource（授权来源说明）才会被公开透出，
 * 否则公开视图按未收录处理（前端渲染占位图，不生成人脸）。
 */
@Entity
@Table(name = "persons")
public class Person {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_name")
    private String originalName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "aliases", columnDefinition = "text[]")
    private List<String> aliases = new ArrayList<>();

    @Column(nullable = false)
    private String nationality;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "profession_types", columnDefinition = "text[]")
    private List<String> professionTypes = new ArrayList<>();

    private String bio;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "photo_source")
    private String photoSource;

    @Column(nullable = false)
    private String status = STATUS_DRAFT;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Person() {
    }

    public Person(String name, String originalName, List<String> aliases,
                  String nationality, List<String> professionTypes, String bio,
                  String photoUrl, String photoSource) {
        this.name = name;
        this.originalName = originalName;
        this.aliases = aliases;
        this.nationality = nationality;
        this.professionTypes = professionTypes;
        this.bio = bio;
        this.photoUrl = photoUrl;
        this.photoSource = photoSource;
    }

    /** DRAFT → PUBLISHED 守卫；重复发布抛冲突 */
    public void publish() {
        if (STATUS_PUBLISHED.equals(status)) {
            throw new StarConflictException("person already published: " + id);
        }
        this.status = STATUS_PUBLISHED;
    }

    /** 照片是否可公开：有 URL 且有授权来源说明 */
    public boolean hasPublishablePhoto() {
        return photoUrl != null && !photoUrl.isBlank()
                && photoSource != null && !photoSource.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOriginalName() {
        return originalName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getNationality() {
        return nationality;
    }

    public List<String> getProfessionTypes() {
        return professionTypes;
    }

    public String getBio() {
        return bio;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getPhotoSource() {
        return photoSource;
    }

    public String getStatus() {
        return status;
    }
}
