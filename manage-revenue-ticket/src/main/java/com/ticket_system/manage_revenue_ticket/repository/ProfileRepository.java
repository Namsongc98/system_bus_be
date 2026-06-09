package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile,Long> {
}
