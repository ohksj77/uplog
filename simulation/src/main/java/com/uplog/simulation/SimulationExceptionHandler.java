package com.uplog.simulation;

import com.uplog.marketdata.feed.MarketFeedException;
import com.uplog.simulation.api.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 기능 A 오류를 계약의 {@code {code, message}}로 바꾼다. */
@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulationExceptionHandler.class);

    @ExceptionHandler(SimulationException.class)
    public ResponseEntity<ErrorResponse> handle(SimulationException e) {
        return ResponseEntity.status(e.code().httpStatus()).body(new ErrorResponse(e.code().name(), e.getMessage()));
    }

    /** 요청자의 키로 시세를 받지 못함. 샘플로 대신하지 않고 사유를 알린다. */
    @ExceptionHandler(MarketFeedException.class)
    public ResponseEntity<ErrorResponse> handle(MarketFeedException e) {
        log.warn("요청자 키로 시세를 받지 못함: {}", e.reason());
        ErrorCode code = ErrorCode.MARKET_DATA_UNAVAILABLE;
        return ResponseEntity.status(code.httpStatus()).body(new ErrorResponse(code.name(), e.getMessage()));
    }
}
