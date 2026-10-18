package com.uplog.portfolio;

import com.uplog.portfolio.api.PortfolioDtos.CostsResponse;
import com.uplog.portfolio.api.PortfolioDtos.DayResponse;
import com.uplog.portfolio.api.PortfolioDtos.DividendsResponse;
import com.uplog.portfolio.api.PortfolioDtos.FxResponse;
import com.uplog.portfolio.api.PortfolioDtos.HabitsResponse;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDetailResponse;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDto;
import com.uplog.portfolio.api.PortfolioDtos.HoldingPeriodsResponse;
import com.uplog.portfolio.api.PortfolioDtos.LogResponse;
import com.uplog.portfolio.api.PortfolioDtos.MonthlyResponse;
import com.uplog.portfolio.api.PortfolioDtos.SalesResponse;
import com.uplog.portfolio.api.PortfolioDtos.ShadowResponse;
import com.uplog.portfolio.api.PortfolioDtos.SummaryResponse;
import com.uplog.portfolio.api.PortfolioDtos.UnderwaterResponse;
import com.uplog.portfolio.api.PortfolioDtos.WaterfallResponse;
import com.uplog.auth.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기능 B: 내 계좌 뷰(docs/api/portfolio.md). 계좌는 요청자로 정한다({@link PortfolioAccounts}: 비로그인 = 데모 계좌).
 * 파라미터는 문자열로 받아 직접 검증한다(오류 문구를 계약대로 내기 위해). 파라미터 오류를 계좌 없음보다 먼저 본다.
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService service;
    private final PortfolioAccounts accounts;

    public PortfolioController(PortfolioService service, PortfolioAccounts accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(Optional<CurrentUser> user) {
        return service.summary(accounts.resolve(user));
    }

    @GetMapping("/log")
    public LogResponse log(@RequestParam(required = false) String year, Optional<CurrentUser> user) {
        return service.log(accounts.resolve(user), year);
    }

    @GetMapping("/holdings")
    public List<HoldingDto> holdings(Optional<CurrentUser> user) {
        return service.holdings(accounts.resolve(user));
    }

    @GetMapping("/holdings/{code}")
    public HoldingDetailResponse holding(@PathVariable String code, Optional<CurrentUser> user) {
        var parsed = PortfolioParams.code(code);
        return service.holding(accounts.resolve(user), parsed);
    }

    @GetMapping("/days/{date}")
    public DayResponse day(@PathVariable String date, Optional<CurrentUser> user) {
        var parsed = PortfolioParams.date(date);
        return service.day(accounts.resolve(user), parsed);
    }

    @GetMapping("/insights/sales")
    public SalesResponse sales(@RequestParam(required = false) String horizon, Optional<CurrentUser> user) {
        var parsed = PortfolioParams.horizon(horizon);
        return service.sales(accounts.resolve(user), parsed);
    }

    @GetMapping("/insights/shadow")
    public ShadowResponse shadow(@RequestParam(required = false) String benchmark, Optional<CurrentUser> user) {
        var parsed = PortfolioParams.benchmark(benchmark);
        return service.shadow(accounts.resolve(user), parsed);
    }

    @GetMapping("/insights/waterfall")
    public WaterfallResponse waterfall(@RequestParam(required = false) String period, Optional<CurrentUser> user) {
        var parsed = PortfolioParams.period(period);
        return service.waterfall(accounts.resolve(user), parsed);
    }

    @GetMapping("/insights/holding-periods")
    public HoldingPeriodsResponse holdingPeriods(Optional<CurrentUser> user) {
        return service.holdingPeriods(accounts.resolve(user));
    }

    @GetMapping("/insights/fx")
    public FxResponse fx(Optional<CurrentUser> user) {
        return service.fx(accounts.resolve(user));
    }

    @GetMapping("/insights/underwater")
    public UnderwaterResponse underwater(Optional<CurrentUser> user) {
        return service.underwater(accounts.resolve(user));
    }

    @GetMapping("/insights/monthly")
    public MonthlyResponse monthly(Optional<CurrentUser> user) {
        return service.monthly(accounts.resolve(user));
    }

    @GetMapping("/insights/habits")
    public HabitsResponse habits(Optional<CurrentUser> user) {
        return service.habits(accounts.resolve(user));
    }

    @GetMapping("/insights/dividends")
    public DividendsResponse dividends(Optional<CurrentUser> user) {
        return service.dividends(accounts.resolve(user));
    }

    @GetMapping("/insights/costs")
    public CostsResponse costs(@RequestParam(required = false) String year, Optional<CurrentUser> user) {
        return service.costs(accounts.resolve(user), year);
    }
}
