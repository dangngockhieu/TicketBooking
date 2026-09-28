package com.ticketbooking.auth.config;

import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.enums.AccountStatus;
import com.ticketbooking.auth.enums.Role;
import com.ticketbooking.auth.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountBootstrap(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both bootstrap admin email and password must be configured.");
        }

        if (accountRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Bootstrap admin email is already used by a non-admin account.");
        }

        accountRepository.save(Account.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());
    }
}