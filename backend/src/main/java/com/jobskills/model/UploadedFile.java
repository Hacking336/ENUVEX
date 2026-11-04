package com.jobskills.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An image uploaded by a user (profile photo or employer logo).
 *
 * <p>Bytes live in the database rather than on the container filesystem because
 * managed hosts such as Render's free tier wipe local files on every restart,
 * redeploy and spin-down. The filename is the primary key so the existing
 * {@code /uploads/{filename}} URLs stored in profile rows keep working.
 */
@Entity
@Table(name = "uploaded_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @Column(nullable = false, length = 255)
    private String filename;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private Integer sizeBytes;

    // Plain byte[] maps to BYTEA. @Lob would map to a Postgres large object
    // (oid) instead, which is not what the migration creates.
    @Column(nullable = false)
    private byte[] data;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
