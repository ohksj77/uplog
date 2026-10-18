package com.uplog.portfolio;

import com.uplog.localuser.CurrentUser;
import com.uplog.portfolio.PortfolioRepository.Account;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 요청자 → 계좌. 토스를 연결한 사용자는 자기 ACTIVE 계좌, 아직 연결하지 않았으면(또는 해제했으면) 데모 계좌(샘플).
 * 데모 계좌도 없으면 {@code ACCOUNT_NOT_CONNECTED}.
 */
@Component
public class PortfolioAccounts {

    private final PortfolioRepository repository;

    public PortfolioAccounts(PortfolioRepository repository) {
        this.repository = repository;
    }

    public Account resolve(Optional<CurrentUser> user) {
        Optional<Account> account = user.flatMap(u -> repository.activeAccountOf(u.id())).or(repository::demoAccount);
        return account.orElseThrow(() -> new PortfolioException(
                PortfolioErrorCode.ACCOUNT_NOT_CONNECTED, "연결된 계좌가 없습니다. 증권 계좌를 먼저 연결해 주세요."));
    }
}
