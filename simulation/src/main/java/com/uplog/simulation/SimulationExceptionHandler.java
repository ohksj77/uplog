package com.uplog.simulation;

import com.uplog.simulation.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 기능 A 오류를 계약의 {@code {code, message}}로 바꾼다. */
@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationExceptionHandler {

    @ExceptionHandler(SimulationException.class)
    public ResponseEntity<ErrorResponse> handle(SimulationException e) {
        return ResponseEntity.status(e.code().httpStatus()).body(new ErrorResponse(e.code().name(), e.getMessage()));
    }
}
