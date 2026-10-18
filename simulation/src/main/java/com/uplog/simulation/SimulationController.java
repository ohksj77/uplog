package com.uplog.simulation;

import com.uplog.simulation.api.EtfResponse;
import com.uplog.simulation.api.SimulationResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기능 A: 비로그인 공개 API. 파라미터는 문자열로 받아 직접 검증한다(오류 문구를 계약대로 내기 위해). */
@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @GetMapping("/simulations")
    public SimulationResponse simulate(
            @RequestParam(required = false) String etfs,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) String dayOfMonth,
            @RequestParam(required = false) String amountType,
            @RequestParam(required = false) String amount) {
        return service.simulate(SimulationQuery.parse(etfs, period, frequency, dayOfWeek, dayOfMonth, amountType, amount));
    }

    @GetMapping("/etfs")
    public List<EtfResponse> etfs(
            @RequestParam(required = false) String query, @RequestParam(required = false) String limit) {
        return service.searchEtfs(query, parseLimit(limit));
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
