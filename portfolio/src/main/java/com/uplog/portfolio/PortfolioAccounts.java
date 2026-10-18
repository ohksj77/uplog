package com.uplog.portfolio;

import com.uplog.auth.CurrentUser;
import com.uplog.portfolio.PortfolioRepository.Account;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 요청자 → 계좌. 비로그인은 데모 계좌, 로그인은 자기 ACTIVE 계좌. 없으면 {@code ACCOUNT_NOT_CONNECTED}. */
@Component
public class PortfolioAccounts {

    private final PortfolioRepository repository;

    public PortfolioAccounts(PortfolioRepository repository) {
        this.repository = repository;
    }

    public Account resolve(Optional<CurrentUser> user) {
        Optional<Account> account = user.isPresent()
                ? repository.activeAccountOf(user.get().id())
                : repository.demoAccount();
        return account.orElseThrow(() -> new PortfolioException(
                PortfolioErrorCode.ACCOUNT_NOT_CONNECTED, "연결된 계좌가 없습니다. 증권 계좌를 먼저 연결해 주세요."));
    }
}
