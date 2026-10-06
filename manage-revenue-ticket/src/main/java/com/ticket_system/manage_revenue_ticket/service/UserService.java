package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.common.util.PageRequests;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminCreateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminUpdateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserCountsResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserResponse;
import com.ticket_system.manage_revenue_ticket.Enum.DriverStatus;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.projection.RoleCountProjection;
import com.ticket_system.manage_revenue_ticket.projection.UserAuthStateProjection;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Admin user management (/api/user). Rules agreed in spec review 1.2:
 * D4 — an admin creates or assigns only DRIVER, COLLECTOR, CUSTOMER;
 * D5 — no one locks or changes the role of themselves, of the last active ADMIN,
 * or of a DRIVER who still has an unfinished trip. There is no delete (D2): lock instead.
 */
@Service
public class UserService {

    static final Set<UserRole> ASSIGNABLE_ROLES = Set.of(UserRole.DRIVER, UserRole.COLLECTOR, UserRole.CUSTOMER);
    // Name of the unique constraint on users.email (User.java, V1__baseline.sql).
    private static final String EMAIL_UNIQUE_CONSTRAINT = "email_unique";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final TripRepository tripRepository;
    private final AuthService authService;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                       TripRepository tripRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.tripRepository = tripRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(UserRole role, String keyword, int page, int size) {
        // The query orders by id desc itself; the Pageable only carries page and size.
        return PageResponse.from(
                userRepository.search(role, likePattern(keyword), PageRequests.of(page, size)),
                row -> UserResponse.from(row.user(), row.profile()));
    }

    @Transactional(readOnly = true)
    public UserCountsResponse getCounts() {
        Map<UserRole, Long> byRole = new EnumMap<>(UserRole.class);
        for (UserRole role : UserRole.values()) {
            byRole.put(role, 0L);
        }
        long total = 0;
        for (RoleCountProjection row : userRepository.countGroupByRole()) {
            byRole.put(row.getRole(), row.getTotal());
            total += row.getTotal();
        }
        return new UserCountsResponse(total, byRole);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = findUser(userId);
        return UserResponse.from(user, profileRepository.findByUserId(userId).orElse(null));
    }

    @Transactional
    public UserResponse create(AdminCreateUserRequest request) {
        requireAssignable(request.getRole());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw duplicateEmail(email);
        }
        User user = User.builder()
                .email(email)
                .password(authService.encodePassword(request.getPassword()))
                .role(request.getRole())
                .isActive(true)
                .driverStatus(request.getRole() == UserRole.DRIVER ? DriverStatus.PENDING : null)
                .build();
        User saved = saveUniqueEmail(user, email);
        Profile profile = profileRepository.save(Profile.builder()
                .user(saved)
                .fullName(request.getFullName().trim())
                .phone(blankToNull(request.getPhone()))
                .email(email)
                .build());
        return UserResponse.from(saved, profile);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse update(Long userId, AdminUpdateUserRequest request, Long callerId) {
        User user = lockedUserForChange(userId);
        UserRole newRole = request.getRole();
        if (newRole != user.getRole()) {
            requireAssignable(newRole);
            if (Objects.equals(userId, callerId)) {
                throw new ConflictException("Không thể tự đổi vai trò của chính mình");
            }
            requireNotLastActiveAdmin(user, "Không thể hạ vai trò quản trị viên cuối cùng đang hoạt động");
            requireNoUnfinishedTrip(user, "Tài xế còn chuyến chưa kết thúc, không thể đổi vai trò");
            user.setRole(newRole);
            if (newRole == UserRole.DRIVER && user.getDriverStatus() == null) {
                user.setDriverStatus(DriverStatus.PENDING);
            }
            user = userRepository.save(user);
        }

        Profile profile = profileRepository.findByUserId(userId).orElseGet(Profile::new);
        if (profile.getUser() == null) {
            // Users created by public register have no profile yet.
            profile.setUser(user);
            profile.setEmail(user.getEmail());
        }
        profile.setFullName(request.getFullName().trim());
        profile.setPhone(blankToNull(request.getPhone()));
        return UserResponse.from(user, profileRepository.save(profile));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse setActive(Long userId, boolean active, Long callerId) {
        User user = lockedUserForChange(userId);
        boolean currentlyActive = !Boolean.FALSE.equals(user.getIsActive());
        if (currentlyActive != active) {
            if (!active) {
                if (Objects.equals(userId, callerId)) {
                    throw new ConflictException("Không thể tự khoá tài khoản của chính mình");
                }
                requireNotLastActiveAdmin(user, "Không thể khoá quản trị viên cuối cùng đang hoạt động");
                requireNoUnfinishedTrip(user, "Tài xế còn chuyến chưa kết thúc, không thể khoá");
            }
            user.setIsActive(active);
            user = userRepository.save(user);
        }
        return UserResponse.from(user, profileRepository.findByUserId(userId).orElse(null));
    }

    /**
     * Lead review 1.3 L14: lock the user row before reading it, like TripService does for drivers, so
     * locking / re-roling a driver and creating a trip for that driver are serialised, and the
     * driverStatus TripService just recomputed is never overwritten by a stale copy. The role is read
     * through a projection (no entity loaded before the lock). For an admin, the active-admin rows are
     * locked first, in the same order lockActiveAdmins() always uses, so two admins acting on each
     * other cannot deadlock.
     */
    private User lockedUserForChange(Long userId) {
        UserAuthStateProjection state = userRepository.findAuthStateById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (state.getRole() == UserRole.ADMIN) {
            userRepository.lockActiveAdmins();
        }
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private static void requireAssignable(UserRole role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Chỉ được gán vai trò DRIVER, COLLECTOR hoặc CUSTOMER");
        }
    }

    // lockActiveAdmins() holds a write lock on the active ADMIN rows until commit: a concurrent request
    // locking or demoting another admin waits here and then sees this transaction's result.
    private void requireNotLastActiveAdmin(User user, String message) {
        if (user.getRole() == UserRole.ADMIN
                && !Boolean.FALSE.equals(user.getIsActive())
                && userRepository.lockActiveAdmins().size() <= 1) {
            throw new ConflictException(message);
        }
    }

    private void requireNoUnfinishedTrip(User user, String message) {
        if (user.getRole() == UserRole.DRIVER
                && tripRepository.existsByDriverIdAndStatusIn(user.getId(), BusService.UNFINISHED_TRIP_STATUSES)) {
            throw new ConflictException(message);
        }
    }

    // Two concurrent creates with the same email both pass existsByEmail; the unique key decides.
    private User saveUniqueEmail(User user, String email) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw duplicateEmail(email);
            }
            throw ex;
        }
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private static ConflictException duplicateEmail(String email) {
        return new ConflictException("Email " + email + " đã được sử dụng");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Lowercased %keyword% with LIKE wildcards escaped, or null when there is nothing to match.
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
