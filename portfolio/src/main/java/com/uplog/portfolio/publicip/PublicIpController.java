package com.uplog.portfolio.publicip;

import com.uplog.portfolio.connection.ConnectionDtos;
import com.uplog.portfolio.connection.ConnectionDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/desktop/public-ip} → {@code {"ip":"1.2.3.4"}}. 연결 화면의 허용 IP 단계가 누를 때만 부른다. */
@RestController
public class PublicIpController {

    private static final Logger log = LoggerFactory.getLogger(PublicIpController.class);

    public record PublicIpResponse(String ip) {}

    private final PublicIpService service;

    public PublicIpController(PublicIpService service) {
        this.service = service;
    }

    @GetMapping(ConnectionDtos.PUBLIC_IP_CHECK)
    public PublicIpResponse publicIp() {
        return new PublicIpResponse(service.current());
    }

    @ExceptionHandler(PublicIpUnavailableException.class)
    public ResponseEntity<ErrorResponse> unavailable(PublicIpUnavailableException e) {
        log.info("공인 IP 확인 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse("PUBLIC_IP_UNAVAILABLE",
                "공인 IP를 확인하지 못했어요. 인터넷 연결을 확인하고 다시 눌러 주세요."));
    }
}
