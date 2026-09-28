package com.ticketbooking.auth.config;

import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.enums.AccountStatus;
import com.ticketbooking.auth.enums.Role;
import com.ticketbooking.auth.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountBootstrapTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void createsActiveAdminWithEncodedPasswordWhenNoAdminExists() {
        when(accountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(accountRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded-password");
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                accountRepository, passwordEncoder, " Admin@Example.com ", "strong-password");

        bootstrap.run(applicationArguments);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertEquals("admin@example.com", savedAccount.getEmail());
        assertEquals("encoded-password", savedAccount.getPasswordHash());
        assertEquals(Role.ADMIN, savedAccount.getRole());
        assertEquals(AccountStatus.ACTIVE, savedAccount.getStatus());
    }

    @Test
    void skipsBootstrapWhenAdminAlreadyExists() {
        when(accountRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                accountRepository, passwordEncoder, "admin@example.com", "strong-password");

        bootstrap.run(applicationArguments);

        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejectsPartiallyConfiguredCredentials() {
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                accountRepository, passwordEncoder, "admin@example.com", " ");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(applicationArguments));

        verifyNoInteractions(accountRepository, passwordEncoder);
    }
}