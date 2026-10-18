package com.uplog.simulation;

import com.uplog.auth.CurrentUser;
import com.uplog.marketdata.feed.MarketFeed;
import com.uplog.marketdata.feed.MarketFeeds;
import com.uplog.simulation.api.EtfResponse;
import com.uplog.simulation.api.SimulationResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기능 A: 비로그인도 쓰는 공개 API. 파라미터는 문자열로 받아 직접 검증한다(오류 문구를 계약대로 내기 위해).
 *
 * <p>로그인했고 계좌가 연결돼 있으면 그 사용자 자신의 키로 실제 종목을 다루고, 아니면 샘플 종목만 다룬다.
 */
@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService service;
    private final ObjectProvider<MarketFeeds> feeds;

    public SimulationController(SimulationService service, ObjectProvider<MarketFeeds> feeds) {
        this.service = service;
        this.feeds = feeds;
    }

    @GetMapping("/simulations")
    public SimulationResponse simulate(
            Optional<CurrentUser> user,
            @RequestParam(required = false) String etfs,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) String dayOfMonth,
            @RequestParam(required = false) String amountType,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String currency) {
        SimulationQuery query =
                SimulationQuery.parse(etfs, period, frequency, dayOfWeek, dayOfMonth, amountType, amount, currency);
        return service.simulate(feedOf(user), query);
    }

    /** 종목 검색. {@code /api/etfs}는 v1 이름이고 ETF·주식을 함께 돌려준다. */
    @GetMapping({"/instruments", "/etfs"})
    public List<EtfResponse> instruments(
            Optional<CurrentUser> user,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String limit) {
        int parsedLimit = parseLimit(limit);
        return service.searchInstruments(feedOf(user), query, parsedLimit);
    }

    /** 요청자 자신의 시세 소스. 비로그인·미연결이면 empty. */
    private Optional<MarketFeed> feedOf(Optional<CurrentUser> user) {
        MarketFeeds available = feeds.getIfAvailable();
        if (user.isEmpty() || available == null) {
            return Optional.empty();
        }
        return available.forUser(user.get().id());
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return SimulationService.DEFAULT_ETF_LIMIT;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw SimulationException.invalid("limit은 1–" + SimulationService.MAX_ETF_LIMIT + " 사이의 정수여야 합니다.");
        }
    }
}
