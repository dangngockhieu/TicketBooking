package com.ticketbooking.auth.security;

import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.enums.AccountStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final AccountStatus status;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(
            UUID id,
            String email,
            String password,
            AccountStatus status,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.status = status;
        this.authorities = authorities;
    }

    public static UserPrincipal fromAccount(Account account) {
        return new UserPrincipal(
                account.getId(),
                account.getEmail(),
                account.getPasswordHash(),
                account.getStatus(),
                List.of(new SimpleGrantedAuthority(account.getRole().withPrefix()))
        );
    }

    public UUID getId() {
        return id;
    }

    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != AccountStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }
}
