package com.ticketbooking.auth.security;

import com.ticketbooking.auth.entity.Account;
import com.ticketbooking.auth.enums.AccountStatus;
import com.ticketbooking.auth.repository.AccountRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsCustom implements UserDetailsService {

    private final AccountRepository accountRepository;

    public UserDetailsCustom(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại với email: " + username));

        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new LockedException("Tài khoản đã bị khóa.");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Tài khoản chưa được kích hoạt hoặc đang chờ duyệt.");
        }

        return UserPrincipal.fromAccount(account);
    }
}
