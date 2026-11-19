package com.ems.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "leave_requests", indexes = @Index(name = "idx_leave_status", columnList = "status"))
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(length = 500)
    private String reviewComment;

    private Instant reviewedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaveRequest() {
    }

    public LeaveRequest(Employee employee, LeaveType type, LocalDate startDate, LocalDate endDate, String reason) {
        this.employee = employee;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Inclusive number of calendar days covered by this request. */
    public long getDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public void review(LeaveStatus decision, User reviewer, String comment) {
        this.status = decision;
        this.reviewedBy = reviewer;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public LeaveType getType() { return type; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public User getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
