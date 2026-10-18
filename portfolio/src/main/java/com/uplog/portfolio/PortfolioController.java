package com.uplog.portfolio;

import com.uplog.portfolio.api.PortfolioDtos.DayResponse;
import com.uplog.portfolio.api.PortfolioDtos.FxResponse;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDetailResponse;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDto;
import com.uplog.portfolio.api.PortfolioDtos.HoldingPeriodsResponse;
import com.uplog.portfolio.api.PortfolioDtos.LogResponse;
import com.uplog.portfolio.api.PortfolioDtos.SalesResponse;
import com.uplog.portfolio.api.PortfolioDtos.ShadowResponse;
import com.uplog.portfolio.api.PortfolioDtos.SummaryResponse;
import com.uplog.portfolio.api.PortfolioDtos.UnderwaterResponse;
import com.uplog.portfolio.api.PortfolioDtos.WaterfallResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기능 B: 내 계좌 뷰(docs/api/portfolio.md). 파라미터는 문자열로 받아 직접 검증한다(오류 문구를 계약대로 내기 위해). */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/log")
    public LogResponse log(@RequestParam(required = false) String year) {
        return service.log(year);
    }

    @GetMapping("/holdings")
    public List<HoldingDto> holdings() {
        return service.holdings();
    }

    @GetMapping("/holdings/{code}")
    public HoldingDetailResponse holding(@PathVariable String code) {
        return service.holding(PortfolioParams.code(code));
    }

    @GetMapping("/days/{date}")
    public DayResponse day(@PathVariable String date) {
        return service.day(PortfolioParams.date(date));
    }

    @GetMapping("/insights/sales")
    public SalesResponse sales(@RequestParam(required = false) String horizon) {
        return service.sales(PortfolioParams.horizon(horizon));
    }

    @GetMapping("/insights/shadow")
    public ShadowResponse shadow(@RequestParam(required = false) String benchmark) {
        return service.shadow(PortfolioParams.benchmark(benchmark));
    }

    @GetMapping("/insights/waterfall")
    public WaterfallResponse waterfall(@RequestParam(required = false) String period) {
        return service.waterfall(PortfolioParams.period(period));
    }

    @GetMapping("/insights/holding-periods")
    public HoldingPeriodsResponse holdingPeriods() {
        return service.holdingPeriods();
    }

    @GetMapping("/insights/fx")
    public FxResponse fx() {
        return service.fx();
    }

    @GetMapping("/insights/underwater")
    public UnderwaterResponse underwater() {
        return service.underwater();
    }
}
