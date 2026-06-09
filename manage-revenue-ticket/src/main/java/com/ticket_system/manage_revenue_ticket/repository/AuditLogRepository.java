package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
