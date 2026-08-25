package com.ticketbooking.auth.service.impl;

import com.ticketbooking.auth.dto.request.AdminCreateOrganizerRequest;
import com.ticketbooking.auth.dto.request.ChangePasswordRequest;
import com.ticketbooking.auth.dto.request.LoginRequest;
import com.ticketbooking.auth.dto.request.RegisterRequest;
import com.ticketbooking.auth.dto.request.ResendVerificationRequest;
import com.ticketbooking.auth.dto.request.VerifyEmailRequest;
import com.ticketbooking.auth.dto.response.AdminCreateOrganizerResponse;
import com.ticketbooking.auth.dto.response.AuthResponse;
import com.ticketbooking.auth.dto.response.LoginResult;
import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.entity.RefreshToken;
import com.ticketbooking.auth.enums.AccountStatus;
import com.ticketbooking.auth.enums.ClientType;
import com.ticketbooking.auth.enums.Role;
import com.ticketbooking.auth.repository.AccountRepository;
import com.ticketbooking.auth.repository.RefreshTokenRepository;
import com.ticketbooking.auth.security.JwtTokenProvider;
import com.ticketbooking.auth.service.AuthService;
import com.ticketbooking.auth.service.OtpService;
import com.ticketbooking.auth.util.TempPasswordGenerator;
import com.ticketbooking.common.exception.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;

    public AuthServiceImpl(
            AccountRepository accountRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            OtpService otpService) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.otpService = otpService;
    }

    @Override
    public AuthResponse.UserInfo register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.email().trim())) {
            throw new ConflictException("Email " + request.email() + " đã được đăng ký trên hệ thống.");
        }

        // Endpoint công khai này CHỈ tạo tài khoản CUSTOMER. Organizer không tự đăng
        // ký — Admin cấp tài khoản trực tiếp qua createOrganizer(). Tài khoản mới
        // luôn ở trạng thái PENDING cho tới khi xác thực OTP (xem verifyEmail()).
        Account account = Account.builder()
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .status(AccountStatus.PENDING)
                .build();

        Account saved = accountRepository.save(account);
        otpService.issueOtp(saved.getId());

        return toUserInfo(saved);
    }

    @Override
    public LoginResult verifyEmail(VerifyEmailRequest request, ClientType clientType) {
        String email = request.email().trim().toLowerCase();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản ứng với email này."));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new ConflictException("Tài khoản đã được xác thực trước đó. Vui lòng đăng nhập.");
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new UnauthorizedException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ ban quản trị.");
        }

        otpService.verifyOtp(account.getId(), request.otp());

        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        return issueSession(account, clientType);
    }

    @Override
    public void resendVerification(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản ứng với email này."));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new ConflictException("Tài khoản đã được xác thực trước đó. Vui lòng đăng nhập.");
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new UnauthorizedException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ ban quản trị.");
        }

        otpService.issueOtp(account.getId());
    }

    @Override
    public LoginResult login(LoginRequest request, ClientType clientType) {
        String email = request.email().trim().toLowerCase();
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không chính xác."));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác.");
        }

        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new UnauthorizedException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ ban quản trị.");
        }

        // Sau khi bỏ luồng "Organizer tự đăng ký chờ duyệt", trạng thái PENDING chỉ
        // còn xảy ra với Customer chưa xác thực email (xem register()/verifyEmail()).
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException(
                    "Tài khoản chưa được xác thực email. Vui lòng kiểm tra hộp thư hoặc yêu cầu gửi lại mã xác thực.");
        }

        return issueSession(account, clientType);
    }

    @Override
    public LoginResult refresh(String rawRefreshToken) {
        jwtTokenProvider.checkValidToken(rawRefreshToken);

        RefreshToken savedToken = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không tồn tại hoặc đã bị thu hồi."));

        Account account = savedToken.getAccount();

        // Kiểm tra Token Reuse Detection (Nếu token đã bị revoke mà vẫn có người gửi
        // lên ➔ khả năng bị hack)
        if (savedToken.isRevoked()) {
            refreshTokenRepository.revokeAllByAccountId(account.getId());
            throw new InvalidTokenException(
                    "Cảnh báo bảo mật: Token đã được sử dụng trước đó. Toàn bộ phiên đăng nhập đã bị hủy.");
        }

        if (savedToken.isExpired()) {
            throw new InvalidTokenException("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Tài khoản của bạn không ở trạng thái hoạt động.");
        }

        // REFRESH TOKEN ROTATION (RTR): Thu hồi token cũ
        savedToken.setRevoked(true);
        refreshTokenRepository.save(savedToken);

        // Lưu token mới kế thừa ClientType ban đầu (Session Binding)
        ClientType boundClientType = savedToken.getClientType();
        return issueSession(account, boundClientType);
    }

    @Override
    public void logout(String email) {
        if (email != null && !email.isBlank()) {
            accountRepository.findByEmail(email)
                    .ifPresent(account -> refreshTokenRepository.revokeAllByAccountId(account.getId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUserProfile(String email) {
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Bạn chưa đăng nhập.");
        }
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản."));

        return toUserInfo(account);
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Bạn chưa đăng nhập.");
        }
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản."));

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không chính xác.");
        }

        if (!request.isConfirmMatched()) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp với mật khẩu mới.");
        }

        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Organizer đăng nhập lần đầu bằng mật khẩu tạm ➔ sau khi tự đổi, không còn
        // bị bắt buộc đổi mật khẩu nữa (xem createOrganizer(), FE §3.3c).
        account.setRequirePasswordChange(false);
        accountRepository.save(account);

        // Đổi mật khẩu xong ➔ thu hồi toàn bộ refresh token hiện có, buộc mọi phiên
        // đăng nhập khác (nếu có) phải đăng nhập lại bằng mật khẩu mới.
        refreshTokenRepository.revokeAllByAccountId(account.getId());
    }

    @Override
    public AdminCreateOrganizerResponse createOrganizer(AdminCreateOrganizerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByEmail(email)) {
            throw new ConflictException("Email " + request.email() + " đã tồn tại tài khoản khác.");
        }

        String tempPassword = TempPasswordGenerator.generate();

        // Admin đã thẩm định giấy phép tổ chức sự kiện ngoài hệ thống trước khi gọi
        // API này, nên tài khoản được tạo ACTIVE ngay — không qua PENDING/OTP.
        // requirePasswordChange = true bắt buộc đổi mật khẩu tạm ở lần đăng nhập đầu.
        Account account = Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(Role.ORGANIZER)
                .status(AccountStatus.ACTIVE)
                .requirePasswordChange(true)
                .build();

        Account saved = accountRepository.save(account);

        // ⏳ Gửi tempPassword qua email cho Organizer phụ thuộc Kafka +
        // notification-service (chưa triển khai). Tạm thời trả về trong response
        // (xem AdminCreateOrganizerResponse) để Admin tự chuyển giao qua kênh khác.
        return new AdminCreateOrganizerResponse(toUserInfo(saved), tempPassword);
    }

    private LoginResult issueSession(Account account, ClientType clientType) {
        AuthResponse.UserInfo userInfo = toUserInfo(account);

        String accessToken = jwtTokenProvider.createAccessToken(userInfo);
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getEmail());

        Instant expiredAt = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationSeconds());
        RefreshToken tokenEntity = RefreshToken.builder()
                .account(account)
                .token(refreshToken)
                .clientType(clientType)
                .expiredAt(expiredAt)
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);

        // Session Binding: Nếu là WEB thì giấu refreshToken trong JSON body (chỉ trả
        // cookie)
        String bodyRefreshToken = (clientType == ClientType.MOBILE) ? refreshToken : null;
        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                userInfo,
                bodyRefreshToken,
                account.isRequirePasswordChange());

        return new LoginResult(authResponse, refreshToken, clientType);
    }

    private AuthResponse.UserInfo toUserInfo(Account account) {
        return new AuthResponse.UserInfo(
                account.getId(),
                account.getEmail(),
                account.getRole().name(),
                account.getStatus().name());
    }
}
