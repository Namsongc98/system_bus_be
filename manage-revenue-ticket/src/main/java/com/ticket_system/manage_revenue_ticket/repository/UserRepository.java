package com.ticket_system.manage_revenue_ticket.repository;

import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.projection.RoleCountProjection;
import com.ticket_system.manage_revenue_ticket.projection.UserAuthStateProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long > {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String Email);

    /**
     * Admin user list: each user with its profile (null when the user has none), in one query.
     * {@code keyword} is an already-escaped, lowercased LIKE pattern ({@code %kw%}) or null.
     */
    @Query(value = """
            select new com.ticket_system.manage_revenue_ticket.repository.UserWithProfile(u, p)
            from User u left join Profile p on p.user = u
            where (:role is null or u.role = :role)
              and (:keyword is null
                   or lower(u.email) like :keyword escape '\\'
                   or lower(p.fullName) like :keyword escape '\\')
            order by u.id desc
            """,
            countQuery = """
            select count(u)
            from User u left join Profile p on p.user = u
            where (:role is null or u.role = :role)
              and (:keyword is null
                   or lower(u.email) like :keyword escape '\\'
                   or lower(p.fullName) like :keyword escape '\\')
            """)
    Page<UserWithProfile> search(@Param("role") UserRole role, @Param("keyword") String keyword, Pageable pageable);

    @Query("select u.role as role, count(u) as total from User u group by u.role")
    List<RoleCountProjection> countGroupByRole();

    /** is_active and role of one user, read by the auth interceptor on every authenticated request. */
    @Query("select u.isActive as isActive, u.role as role from User u where u.id = :id")
    Optional<UserAuthStateProjection> findAuthStateById(@Param("id") Long id);

    /**
     * Locks every active ADMIN row (SELECT ... FOR UPDATE) for the rest of the transaction, so two
     * admins locking or demoting each other at the same time cannot both pass the last-admin check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.ticket_system.common.Enum.UserRole.ADMIN and u.isActive = true order by u.id")
    List<User> lockActiveAdmins();

    /** Row lock so two trips cannot both pass the overlap check for this driver. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
