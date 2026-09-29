package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;

/** One row of {@link UserRepository#search}: a user and its profile, which may be null. */
public record UserWithProfile(User user, Profile profile) {
}
