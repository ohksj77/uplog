package com.uplog.portfolio;

import com.uplog.portfolio.api.PortfolioDtos.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 기능 B 오류를 계약의 {@code {code, message}}로 바꾼다. */
@RestControllerAdvice(assignableTypes = PortfolioController.class)
public class PortfolioExceptionHandler {

    @ExceptionHandler(PortfolioException.class)
    public ResponseEntity<ErrorResponse> handle(PortfolioException e) {
        return ResponseEntity.status(e.code().httpStatus()).body(new ErrorResponse(e.code().name(), e.getMessage()));
    }
}
