package com.ticketbooking.auth.service;

import com.ticketbooking.auth.dto.request.AdminCreateOrganizerRequest;
import com.ticketbooking.auth.dto.request.ChangePasswordRequest;
import com.ticketbooking.auth.dto.request.ForgotPasswordRequest;
import com.ticketbooking.auth.dto.request.LoginRequest;
import com.ticketbooking.auth.dto.request.RegisterRequest;
import com.ticketbooking.auth.dto.request.ResetPasswordRequest;
import com.ticketbooking.auth.dto.request.VerifyEmailRequest;
import com.ticketbooking.auth.dto.response.AdminCreateOrganizerResponse;
import com.ticketbooking.auth.dto.response.AuthResponse;
import com.ticketbooking.auth.dto.response.LoginResult;
import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.entity.RefreshToken;
import com.ticketbooking.auth.enums.AccountStatus;
import com.ticketbooking.auth.enums.ClientType;
import com.ticketbooking.auth.enums.Role;
import com.ticketbooking.auth.event.AuthEventPublisher;
import com.ticketbooking.auth.repository.AccountRepository;
import com.ticketbooking.auth.repository.RefreshTokenRepository;
import com.ticketbooking.auth.security.JwtTokenProvider;
import com.ticketbooking.auth.service.impl.AuthServiceImpl;
import com.ticketbooking.common.exception.BadRequestException;
import com.ticketbooking.common.exception.ConflictException;
import com.ticketbooking.common.exception.InvalidCredentialsException;
import com.ticketbooking.common.exception.InvalidTokenException;
import com.ticketbooking.common.exception.ResourceNotFoundException;
import com.ticketbooking.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OtpService otpService;

    @Mock
    private AuthEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    private Account testAccount;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        testAccount = Account.builder()
                .id(accountId)
                .email("user@example.com")
                .passwordHash("hashed_password")
                .role(Role.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");
        when(accountRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(UUID.randomUUID());
            return acc;
        });

        AuthResponse.UserInfo result = authService.register(request);

        assertNotNull(result);
        assertEquals("new@example.com", result.email());
        assertEquals("CUSTOMER", result.role());
        // Đăng ký công khai luôn tạo tài khoản PENDING, chờ xác thực OTP
        assertEquals("PENDING", result.status());
        verify(accountRepository).save(any(Account.class));
        verify(otpService).issueOtp(result.id(), OtpPurpose.EMAIL_VERIFICATION);
    }

    @Test
    void testRegisterDuplicateEmailThrowsConflict() {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123");
        when(accountRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void testLoginWebClient_ReturnsNullRefreshTokenInBody() {
        LoginRequest request = new LoginRequest("user@example.com", "raw_password");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("raw_password", "hashed_password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("mock_access_token");
        when(jwtTokenProvider.createRefreshToken("user@example.com")).thenReturn("mock_refresh_token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(86400L);

        LoginResult result = authService.login(request, ClientType.WEB);

        assertNotNull(result);
        assertEquals(ClientType.WEB, result.clientType());
        assertEquals("mock_refresh_token", result.rawRefreshToken());
        // Cho Web: JSON body tuyệt đối KHÔNG có refreshToken
        assertNull(result.authResponse().refreshToken());
        assertEquals("mock_access_token", result.authResponse().accessToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void testLoginMobileClient_ReturnsRefreshTokenInBody() {
        LoginRequest request = new LoginRequest("user@example.com", "raw_password");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("raw_password", "hashed_password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("mock_access_token");
        when(jwtTokenProvider.createRefreshToken("user@example.com")).thenReturn("mock_refresh_token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(86400L);

        LoginResult result = authService.login(request, ClientType.MOBILE);

        assertNotNull(result);
        assertEquals(ClientType.MOBILE, result.clientType());
        // Cho Mobile: JSON body CÓ refreshToken để lưu SecureStorage
        assertEquals("mock_refresh_token", result.authResponse().refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void testLoginWrongPassword_ThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong_password");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, ClientType.WEB));
    }

    @Test
    void testLoginPendingAccount_ThrowsUnauthorized() {
        // Customer chưa xác thực OTP — trạng thái PENDING duy nhất còn tồn tại sau
        // khi bỏ luồng "Organizer tự đăng ký chờ duyệt".
        Account pendingAccount = Account.builder()
                .id(accountId)
                .email("pending@example.com")
                .passwordHash("hashed_password")
                .role(Role.CUSTOMER)
                .status(AccountStatus.PENDING)
                .build();
        LoginRequest request = new LoginRequest("pending@example.com", "raw_password");
        when(accountRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pendingAccount));
        when(passwordEncoder.matches("raw_password", "hashed_password")).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> authService.login(request, ClientType.WEB));
    }

    @Test
    void testVerifyEmailSuccess_ActivatesAccountAndLogsIn() {
        Account pendingAccount = Account.builder()
                .id(accountId)
                .email("pending@example.com")
                .passwordHash("hashed_password")
                .role(Role.CUSTOMER)
                .status(AccountStatus.PENDING)
                .build();
        VerifyEmailRequest request = new VerifyEmailRequest("pending@example.com", "123456");
        when(accountRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pendingAccount));
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("access_token");
        when(jwtTokenProvider.createRefreshToken("pending@example.com")).thenReturn("refresh_token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(86400L);

        LoginResult result = authService.verifyEmail(request, ClientType.WEB);

        verify(otpService).verifyOtp(accountId, "123456", OtpPurpose.EMAIL_VERIFICATION);
        assertEquals(AccountStatus.ACTIVE, pendingAccount.getStatus());
        assertEquals("ACTIVE", result.authResponse().user().status());
        verify(accountRepository).save(pendingAccount);
    }

    @Test
    void testVerifyEmailAlreadyActive_ThrowsConflict() {
        VerifyEmailRequest request = new VerifyEmailRequest("user@example.com", "123456");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));

        assertThrows(ConflictException.class, () -> authService.verifyEmail(request, ClientType.WEB));
        verify(otpService, never()).verifyOtp(any(), any(), any());
    }

    @Test
    void testForgotPasswordExistingAccount_IssuesOtp() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));

        authService.forgotPassword(request);

        verify(otpService).issueOtp(accountId, OtpPurpose.PASSWORD_RESET);
    }

    @Test
    void testForgotPasswordUnknownAccount_DoesNotThrowOrIssueOtp() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");
        when(accountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword(request));
        verify(otpService, never()).issueOtp(any(), any());
    }

    @Test
    void testResetPasswordSuccess_ChangesPasswordAndRevokesTokens() {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "new_password123");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.encode("new_password123")).thenReturn("hashed_new_password");

        authService.resetPassword(request);

        verify(otpService).verifyOtp(accountId, "123456", OtpPurpose.PASSWORD_RESET);
        assertEquals("hashed_new_password", testAccount.getPasswordHash());
        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
    }

    @Test
    void testResetPasswordUnknownAccount_ThrowsResourceNotFound() {
        ResetPasswordRequest request = new ResetPasswordRequest("unknown@example.com", "123456", "new_password123");
        when(accountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.resetPassword(request));
        verify(otpService, never()).verifyOtp(any(), any(), any());
    }

    @Test
    void testResetPasswordExpiredOtp_ThrowsBadRequestInsteadOf401() {
        ResetPasswordRequest request = new ResetPasswordRequest("user@example.com", "123456", "new_password123");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        doThrow(new InvalidTokenException("Mã OTP đã hết hạn hoặc không tồn tại. Vui lòng yêu cầu gửi lại mã."))
                .when(otpService).verifyOtp(accountId, "123456", OtpPurpose.PASSWORD_RESET);

        assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @Test
    void testCreateOrganizer_CreatesActiveAccountWithTempPassword() {
        AdminCreateOrganizerRequest request = new AdminCreateOrganizerRequest("organizer@example.com", "Công ty ABC");
        when(accountRepository.existsByEmail("organizer@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_temp_password");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(UUID.randomUUID());
            return acc;
        });

        AdminCreateOrganizerResponse response = authService.createOrganizer(request);

        assertNotNull(response.tempPassword());
        assertEquals(12, response.tempPassword().length());
        assertEquals("ORGANIZER", response.account().role());
        assertEquals("ACTIVE", response.account().status());
        verify(accountRepository).save(argThat(acc ->
                acc.getRole() == Role.ORGANIZER
                        && acc.getStatus() == AccountStatus.ACTIVE
                        && acc.isRequirePasswordChange()));
        verify(eventPublisher).publishOrganizerCreated(argThat(evt ->
                "organizer@example.com".equals(evt.getEmail())
                        && response.tempPassword().equals(evt.getTempPassword())));
    }

    @Test
    void testCreateOrganizerDuplicateEmail_ThrowsConflict() {
        AdminCreateOrganizerRequest request = new AdminCreateOrganizerRequest("user@example.com", "Công ty ABC");
        when(accountRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.createOrganizer(request));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void testRefreshSessionBinding_WebMaintainsNullBody() {
        RefreshToken tokenInDb = RefreshToken.builder()
                .id(UUID.randomUUID())
                .account(testAccount)
                .token("valid_refresh_token")
                .clientType(ClientType.WEB) // Nguồn gốc phiên là WEB
                .expiredAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid_refresh_token")).thenReturn(Optional.of(tokenInDb));
        when(jwtTokenProvider.createAccessToken(any())).thenReturn("new_access_token");
        when(jwtTokenProvider.createRefreshToken("user@example.com")).thenReturn("new_refresh_token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshTokenExpirationSeconds()).thenReturn(86400L);

        LoginResult result = authService.refresh("valid_refresh_token");

        assertNotNull(result);
        assertEquals(ClientType.WEB, result.clientType());
        // Session Binding: nguồn gốc là WEB ➔ body refreshToken vẫn luôn null
        assertNull(result.authResponse().refreshToken());
        assertEquals("new_refresh_token", result.rawRefreshToken());
        assertTrue(tokenInDb.isRevoked()); // Token cũ đã bị thu hồi (Rotation)
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void testChangePasswordSuccess_UpdatesHashAndRevokesAllSessions() {
        ChangePasswordRequest request = new ChangePasswordRequest("old_password", "new_password123", "new_password123");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("old_password", "hashed_password")).thenReturn(true);
        when(passwordEncoder.matches("new_password123", "hashed_password")).thenReturn(false);
        when(passwordEncoder.encode("new_password123")).thenReturn("new_hashed_password");

        authService.changePassword("user@example.com", request);

        assertEquals("new_hashed_password", testAccount.getPasswordHash());
        assertFalse(testAccount.isRequirePasswordChange());
        verify(accountRepository).save(testAccount);
        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
    }

    @Test
    void testChangePasswordWrongCurrentPassword_ThrowsInvalidCredentials() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrong_old", "new_password123", "new_password123");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("wrong_old", "hashed_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.changePassword("user@example.com", request));
        verify(accountRepository, never()).save(any(Account.class));
        verify(refreshTokenRepository, never()).revokeAllByAccountId(any());
    }

    @Test
    void testChangePasswordConfirmMismatch_ThrowsBadRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest("old_password", "new_password123", "typo_password");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("old_password", "hashed_password")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> authService.changePassword("user@example.com", request));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void testChangePasswordSameAsCurrent_ThrowsBadRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest("old_password", "old_password", "old_password");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.matches("old_password", "hashed_password")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> authService.changePassword("user@example.com", request));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void testChangePasswordForOrganizerWithTempPassword_ClearsRequirePasswordChangeFlag() {
        Account organizerAccount = Account.builder()
                .id(accountId)
                .email("organizer@example.com")
                .passwordHash("hashed_temp_password")
                .role(Role.ORGANIZER)
                .status(AccountStatus.ACTIVE)
                .requirePasswordChange(true)
                .build();
        ChangePasswordRequest request = new ChangePasswordRequest("temp_password", "new_password123", "new_password123");
        when(accountRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerAccount));
        when(passwordEncoder.matches("temp_password", "hashed_temp_password")).thenReturn(true);
        when(passwordEncoder.matches("new_password123", "hashed_temp_password")).thenReturn(false);
        when(passwordEncoder.encode("new_password123")).thenReturn("new_hashed_password");

        authService.changePassword("organizer@example.com", request);

        assertFalse(organizerAccount.isRequirePasswordChange());
    }

    @Test
    void testRefreshTokenReuseDetection_RevokesAllSessions() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .account(testAccount)
                .token("revoked_refresh_token")
                .clientType(ClientType.WEB)
                .expiredAt(Instant.now().plusSeconds(3600))
                .revoked(true) // Đã từng bị thu hồi!
                .build();

        when(refreshTokenRepository.findByToken("revoked_refresh_token")).thenReturn(Optional.of(revokedToken));

        assertThrows(InvalidTokenException.class, () -> authService.refresh("revoked_refresh_token"));
        // Xác minh toàn bộ session của tài khoản bị thu hồi để chống tấn công
        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
    }
}
