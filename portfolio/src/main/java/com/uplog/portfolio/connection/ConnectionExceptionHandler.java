package com.uplog.portfolio.connection;

import com.uplog.portfolio.connection.ConnectionDtos.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 연결 API 오류를 {@code {code, message}}로 바꾼다. TOSS_IP_NOT_ALLOWED는 최상위에 {@code egressIps}를 붙인다.
 * 요청 본문을 읽지 못한 경우에도 본문(키)을 메시지에 넣지 않는다.
 */
@RestControllerAdvice(assignableTypes = ConnectionController.class)
public class ConnectionExceptionHandler {

    private final EgressProperties egress;

    public ConnectionExceptionHandler(EgressProperties egress) {
        this.egress = egress;
    }

    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<ErrorResponse> handle(ConnectionException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.code().httpStatus());
        if (e.retryAfter() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.retryAfter().toSeconds())));
        }
        return builder.body(new ErrorResponse(e.code().name(), e.getMessage(),
                e.code() == ConnectionErrorCode.TOSS_IP_NOT_ALLOWED ? egress.egressIps() : null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ConnectionErrorCode.INVALID_PARAMETER.name(), "요청 본문을 JSON으로 보내 주세요.", null));
    }
}
