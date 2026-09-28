package com.riskregister.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mitigations")
public class Mitigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_id", nullable = false)
    private Risk risk;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private int effectiveness;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getEffectiveness() { return effectiveness; }
    public void setEffectiveness(int effectiveness) { this.effectiveness = effectiveness; }
    public Instant getCreatedAt() { return createdAt; }
}
