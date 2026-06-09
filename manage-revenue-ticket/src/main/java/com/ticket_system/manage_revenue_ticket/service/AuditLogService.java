package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.manage_revenue_ticket.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    @Autowired
    private AuditLogRepository auditLogRepository;
}
