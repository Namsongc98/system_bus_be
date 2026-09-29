package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.Dto.response.PageResponse;
import com.ticket_system.common.Enum.UserRole;
import com.ticket_system.common.exception.ConflictException;
import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminCreateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.request.AdminUpdateUserRequest;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserCountsResponse;
import com.ticket_system.manage_revenue_ticket.Dto.response.UserResponse;
import com.ticket_system.manage_revenue_ticket.Enum.DriverStatus;
import com.ticket_system.manage_revenue_ticket.entity.Profile;
import com.ticket_system.manage_revenue_ticket.entity.User;
import com.ticket_system.manage_revenue_ticket.projection.RoleCountProjection;
import com.ticket_system.manage_revenue_ticket.repository.ProfileRepository;
import com.ticket_system.manage_revenue_ticket.repository.TripRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserRepository;
import com.ticket_system.manage_revenue_ticket.repository.UserWithProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 1.2: roles an admin may assign (D4 = B), lock / role-change guards (D5), no delete (D2 = A). */
class UserServiceTest {

    private static final long CALLER = 1L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserService userService =
            new UserService(userRepository, profileRepository, tripRepository, authService);

    // ─── list / counts / get ────────────────────────────────────────────────

    @Test
    void listMapsProfileAndPassesEscapedKeyword() {
        User withProfile = user(7L, UserRole.DRIVER, true);
        User withoutProfile = user(8L, UserRole.CUSTOMER, true);
        when(userRepository.search(eq(UserRole.DRIVER), eq("%a\\_b\\%%"), any())).thenAnswer(inv -> new PageImpl<>(
                List.of(new UserWithProfile(withProfile, profile(withProfile, "Tài Xế", "0901")),
                        new UserWithProfile(withoutProfile, null)),
                inv.getArgument(2), 2));

        PageResponse<UserResponse> page = userService.getUsers(UserRole.DRIVER, "  A_B% ", 0, 10);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(UserResponse::fullName).containsExactly("Tài Xế", null);
        assertThat(page.content().get(0).phone()).isEqualTo("0901");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(eq(UserRole.DRIVER), eq("%a\\_b\\%%"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void blankKeywordMeansNoFilter() {
        assertThat(UserService.likePattern(null)).isNull();
        assertThat(UserService.likePattern("   ")).isNull();
        assertThat(UserService.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void listRejectsOutOfRangeSize() {
        assertThatThrownBy(() -> userService.getUsers(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.getUsers(null, null, -1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsHaveEveryRoleAndATotal() {
        when(userRepository.countGroupByRole()).thenReturn(List.of(
                roleCount(UserRole.DRIVER, 3L), roleCount(UserRole.CUSTOMER, 10L)));

        UserCountsResponse counts = userService.getCounts();

        assertThat(counts.total()).isEqualTo(13);
        assertThat(counts.byRole()).containsOnlyKeys(UserRole.values());
        assertThat(counts.byRole().get(UserRole.DRIVER)).isEqualTo(3);
        assertThat(counts.byRole().get(UserRole.ADMIN)).isZero();
    }

    @Test
    void getUnknownUserIsNotFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void responseNeverCarriesPasswordAndTreatsNullActiveAsActive() {
        User user = user(5L, UserRole.CUSTOMER, true);
        user.setIsActive(null);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(5L)).thenReturn(Optional.empty());

        UserResponse response = userService.getUser(5L);

        assertThat(response.active()).isTrue();
        assertThat(UserResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("password");
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void createNormalisesEmailHashesPasswordAndSavesProfile() {
        when(authService.encodePassword("secret1")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), 20L));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse created = userService.create(createRequest(" Tai.Xe@Test.VN ", UserRole.DRIVER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("tai.xe@test.vn");
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getIsActive()).isTrue();
        assertThat(saved.getValue().getDriverStatus()).isEqualTo(DriverStatus.PENDING);
        assertThat(created.id()).isEqualTo(20L);
        assertThat(created.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(created.phone()).isNull();
        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getUser().getId()).isEqualTo(20L);
        assertThat(profile.getValue().getEmail()).isEqualTo("tai.xe@test.vn");
    }

    @Test
    void createNonDriverHasNoDriverStatus() {
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), 21L));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.create(createRequest("c@test.vn", UserRole.COLLECTOR));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDriverStatus()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "EMPLOYEE"})
    void createRejectsRolesAnAdminCannotAssign(UserRole role) {
        assertThatThrownBy(() -> userService.create(createRequest("x@test.vn", role)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDuplicateEmailIsConflict() {
        when(userRepository.existsByEmail("dup@test.vn")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(createRequest("DUP@test.vn", UserRole.CUSTOMER)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRacingSameEmailIsConflict() {
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Duplicate entry for key 'users.email_unique'")));

        assertThatThrownBy(() -> userService.create(createRequest("race@test.vn", UserRole.CUSTOMER)))
                .isInstanceOf(ConflictException.class);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void otherIntegrityViolationIsNotReportedAsDuplicateEmail() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("Column 'password' cannot be null");
        when(userRepository.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> userService.create(createRequest("x@test.vn", UserRole.CUSTOMER))).isSameAs(other);
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void updateCreatesProfileForUserWithoutOne() {
        User customer = user(30L, UserRole.CUSTOMER, true);
        when(userRepository.findById(30L)).thenReturn(Optional.of(customer));
        when(profileRepository.findByUserId(30L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse updated = userService.update(30L, updateRequest(" Khách Mới ", " ", UserRole.CUSTOMER), CALLER);

        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getUser()).isSameAs(customer);
        assertThat(profile.getValue().getEmail()).isEqualTo(customer.getEmail());
        assertThat(updated.fullName()).isEqualTo("Khách Mới");
        assertThat(updated.phone()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateToDriverSetsPendingDriverStatus() {
        User collector = user(31L, UserRole.COLLECTOR, true);
        when(userRepository.findById(31L)).thenReturn(Optional.of(collector));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByUserId(31L)).thenReturn(Optional.of(profile(collector, "Cũ", null)));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse updated = userService.update(31L, updateRequest("Mới", "0909", UserRole.DRIVER), CALLER);

        assertThat(updated.role()).isEqualTo(UserRole.DRIVER);
        assertThat(updated.driverStatus()).isEqualTo(DriverStatus.PENDING);
        assertThat(updated.phone()).isEqualTo("0909");
    }

    @Test
    void updateKeepingAnUnassignableRoleIsAllowed() {
        User admin = user(32L, UserRole.ADMIN, true);
        when(userRepository.findById(32L)).thenReturn(Optional.of(admin));
        when(profileRepository.findByUserId(32L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse updated = userService.update(32L, updateRequest("Admin", null, UserRole.ADMIN), CALLER);

        assertThat(updated.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updatePromotingToAdminIsRejected() {
        when(userRepository.findById(33L)).thenReturn(Optional.of(user(33L, UserRole.CUSTOMER, true)));

        assertThatThrownBy(() -> userService.update(33L, updateRequest("X", null, UserRole.ADMIN), CALLER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateOwnRoleIsConflict() {
        when(userRepository.findById(CALLER)).thenReturn(Optional.of(user(CALLER, UserRole.ADMIN, true)));

        assertThatThrownBy(() -> userService.update(CALLER, updateRequest("Me", null, UserRole.CUSTOMER), CALLER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void demotingLastActiveAdminIsConflict() {
        when(userRepository.findById(34L)).thenReturn(Optional.of(user(34L, UserRole.ADMIN, true)));
        when(userRepository.lockActiveAdmins()).thenReturn(activeAdmins(1));

        assertThatThrownBy(() -> userService.update(34L, updateRequest("A", null, UserRole.CUSTOMER), CALLER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void demotingAnAdminWhenAnotherIsActiveIsAllowed() {
        User admin = user(35L, UserRole.ADMIN, true);
        when(userRepository.findById(35L)).thenReturn(Optional.of(admin));
        when(userRepository.lockActiveAdmins()).thenReturn(activeAdmins(2));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByUserId(35L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.update(35L, updateRequest("A", null, UserRole.COLLECTOR), CALLER).role())
                .isEqualTo(UserRole.COLLECTOR);
    }

    @Test
    void changingRoleOfDriverWithUnfinishedTripIsConflict() {
        when(userRepository.findById(36L)).thenReturn(Optional.of(user(36L, UserRole.DRIVER, true)));
        when(tripRepository.existsByDriverIdAndStatusIn(eq(36L), any())).thenReturn(true);

        assertThatThrownBy(() -> userService.update(36L, updateRequest("D", null, UserRole.COLLECTOR), CALLER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateUnknownUserIsNotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, updateRequest("X", null, UserRole.CUSTOMER), CALLER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── lock / unlock ──────────────────────────────────────────────────────

    @Test
    void lockAndUnlock() {
        User customer = user(40L, UserRole.CUSTOMER, true);
        when(userRepository.findById(40L)).thenReturn(Optional.of(customer));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.setActive(40L, false, CALLER).active()).isFalse();
        assertThat(customer.getIsActive()).isFalse();
        assertThat(userService.setActive(40L, true, CALLER).active()).isTrue();
    }

    @Test
    void settingTheCurrentStateIsIdempotentAndSkipsGuards() {
        // Already locked: locking again must not fail even though it is the caller.
        when(userRepository.findById(CALLER)).thenReturn(Optional.of(user(CALLER, UserRole.ADMIN, false)));

        assertThat(userService.setActive(CALLER, false, CALLER).active()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void lockingSelfIsConflict() {
        when(userRepository.findById(CALLER)).thenReturn(Optional.of(user(CALLER, UserRole.ADMIN, true)));
        when(userRepository.lockActiveAdmins()).thenReturn(activeAdmins(5));

        assertThatThrownBy(() -> userService.setActive(CALLER, false, CALLER)).isInstanceOf(ConflictException.class);
    }

    @Test
    void lockingLastActiveAdminIsConflict() {
        when(userRepository.findById(41L)).thenReturn(Optional.of(user(41L, UserRole.ADMIN, true)));
        when(userRepository.lockActiveAdmins()).thenReturn(activeAdmins(1));

        assertThatThrownBy(() -> userService.setActive(41L, false, CALLER)).isInstanceOf(ConflictException.class);
    }

    @Test
    void lockingDriverWithUnfinishedTripIsConflict() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user(42L, UserRole.DRIVER, true)));
        when(tripRepository.existsByDriverIdAndStatusIn(eq(42L), any())).thenReturn(true);

        assertThatThrownBy(() -> userService.setActive(42L, false, CALLER)).isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unlockingSkipsGuards() {
        User driver = user(43L, UserRole.DRIVER, false);
        when(userRepository.findById(43L)).thenReturn(Optional.of(driver));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.setActive(43L, true, CALLER).active()).isTrue();
        verify(tripRepository, never()).existsByDriverIdAndStatusIn(anyLong(), any());
    }

    // ─── guard edge cases (test-gap review) ─────────────────────────────────

    @Test
    void updateKeepingTheSameRoleSkipsEveryGuard() {
        User driver = user(CALLER, UserRole.DRIVER, true);
        when(userRepository.findById(CALLER)).thenReturn(Optional.of(driver));
        when(profileRepository.findByUserId(CALLER)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Own row, driver with an unfinished trip: editing name/phone only must still work.
        userService.update(CALLER, updateRequest("Tài Xế", null, UserRole.DRIVER), CALLER);

        verify(tripRepository, never()).existsByDriverIdAndStatusIn(anyLong(), any());
        verify(userRepository, never()).lockActiveAdmins();
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateToEmployeeIsRejected() {
        when(userRepository.findById(37L)).thenReturn(Optional.of(user(37L, UserRole.CUSTOMER, true)));

        assertThatThrownBy(() -> userService.update(37L, updateRequest("X", null, UserRole.EMPLOYEE), CALLER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void demotingALockedAdminDoesNotNeedAnotherActiveAdmin() {
        User lockedAdmin = user(38L, UserRole.ADMIN, false);
        when(userRepository.findById(38L)).thenReturn(Optional.of(lockedAdmin));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByUserId(38L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.update(38L, updateRequest("A", null, UserRole.CUSTOMER), CALLER).role())
                .isEqualTo(UserRole.CUSTOMER);
        verify(userRepository, never()).lockActiveAdmins();
    }

    @Test
    void becomingADriverAgainKeepsAnExistingDriverStatus() {
        User collector = user(39L, UserRole.COLLECTOR, true);
        collector.setDriverStatus(DriverStatus.ACTIVE);
        when(userRepository.findById(39L)).thenReturn(Optional.of(collector));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.findByUserId(39L)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.update(39L, updateRequest("C", null, UserRole.DRIVER), CALLER).driverStatus())
                .isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void lockingANonDriverNonAdminHitsNoGuardQuery() {
        when(userRepository.findById(44L)).thenReturn(Optional.of(user(44L, UserRole.COLLECTOR, true)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActive(44L, false, CALLER);

        verify(tripRepository, never()).existsByDriverIdAndStatusIn(anyLong(), any());
        verify(userRepository, never()).lockActiveAdmins();
    }

    @Test
    void lockingAUserWithNullActiveSavesFalse() {
        User legacy = user(45L, UserRole.CUSTOMER, true);
        legacy.setIsActive(null);
        when(userRepository.findById(45L)).thenReturn(Optional.of(legacy));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.setActive(45L, false, CALLER).active()).isFalse();
        assertThat(legacy.getIsActive()).isFalse();
    }

    @Test
    void getUserReturnsProfileFields() {
        User driver = user(46L, UserRole.DRIVER, true);
        when(userRepository.findById(46L)).thenReturn(Optional.of(driver));
        when(profileRepository.findByUserId(46L)).thenReturn(Optional.of(profile(driver, "Tài Xế", "0901")));

        UserResponse response = userService.getUser(46L);

        assertThat(response.fullName()).isEqualTo("Tài Xế");
        assertThat(response.phone()).isEqualTo("0901");
    }

    @Test
    void createTrimsPhone() {
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0), 47L));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AdminCreateUserRequest request = createRequest("p@test.vn", UserRole.CUSTOMER);
        request.setPhone(" 0901 234 ");

        assertThat(userService.create(request).phone()).isEqualTo("0901 234");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static List<User> activeAdmins(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(id -> user(100 + id, UserRole.ADMIN, true))
                .toList();
    }

    private static User user(long id, UserRole role, boolean active) {
        return User.builder().id(id).email("u" + id + "@test.vn").password("hash").role(role).isActive(active).build();
    }

    private static User withId(User user, long id) {
        user.setId(id);
        return user;
    }

    private static Profile profile(User user, String fullName, String phone) {
        return Profile.builder().user(user).fullName(fullName).phone(phone).build();
    }

    private static RoleCountProjection roleCount(UserRole role, long total) {
        return new RoleCountProjection() {
            @Override public UserRole getRole() { return role; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static AdminCreateUserRequest createRequest(String email, UserRole role) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setEmail(email);
        request.setPassword("secret1");
        request.setRole(role);
        request.setFullName(" Nguyễn Văn A ");
        request.setPhone("");
        return request;
    }

    private static AdminUpdateUserRequest updateRequest(String fullName, String phone, UserRole role) {
        AdminUpdateUserRequest request = new AdminUpdateUserRequest();
        request.setFullName(fullName);
        request.setPhone(phone);
        request.setRole(role);
        return request;
    }
}
