package com.uplog.portfolio.connection;

import com.uplog.auth.CurrentUser;
import com.uplog.portfolio.connection.ConnectionDtos.ChooseAccountRequest;
import com.uplog.portfolio.connection.ConnectionDtos.ConnectRequest;
import com.uplog.portfolio.connection.ConnectionDtos.ConnectResponse;
import com.uplog.portfolio.connection.ConnectionDtos.ConnectionStatusResponse;
import com.uplog.portfolio.connection.ConnectionDtos.MeResponse;
import com.uplog.portfolio.connection.ConnectionDtos.SetupResponse;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보와 토스 계좌 연결 API(docs/design/account-connection.md "API 계약"). {@code /api/me} 말고는 로그인이 필요하다. */
@RestController
public class ConnectionController {

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    /** 비로그인도 200(user = null). */
    @GetMapping("/api/me")
    public MeResponse me(Optional<CurrentUser> user) {
        return service.me(user.map(CurrentUser::id));
    }

    @GetMapping("/api/connections/toss/setup")
    public SetupResponse setup(CurrentUser user) {
        return service.setup();
    }

    @PostMapping("/api/connections/toss")
    public ResponseEntity<ConnectResponse> connect(CurrentUser user, @RequestBody(required = false) ConnectRequest request) {
        ConnectRequest r = request == null ? new ConnectRequest(null, null) : request;
        return created(service.connect(user.id(), r.clientId(), r.clientSecret()));
    }

    @PostMapping("/api/connections/toss/account")
    public ResponseEntity<ConnectResponse> chooseAccount(
            CurrentUser user, @RequestBody(required = false) ChooseAccountRequest request) {
        ChooseAccountRequest r = request == null ? new ChooseAccountRequest(null, null, null) : request;
        return created(service.chooseAccount(user.id(), r.clientId(), r.clientSecret(), r.accountSeq()));
    }

    @GetMapping("/api/connections/toss")
    public ConnectionStatusResponse status(CurrentUser user) {
        return service.status(user.id());
    }

    @PostMapping("/api/connections/toss/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sync(CurrentUser user) {
        service.sync(user.id());
    }

    @DeleteMapping("/api/connections/toss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(CurrentUser user, @RequestParam(required = false) String deleteData) {
        boolean delete;
        if (deleteData == null || deleteData.equals("false")) {
            delete = false;
        } else if (deleteData.equals("true")) {
            delete = true;
        } else {
            throw new ConnectionException(ConnectionErrorCode.INVALID_PARAMETER, "deleteData는 true 또는 false입니다.");
        }
        service.disconnect(user.id(), delete);
    }

    /** 바로 연결되면 201, 계좌를 골라야 하면 200. */
    private static ResponseEntity<ConnectResponse> created(ConnectResponse response) {
        HttpStatus status = "BACKFILLING".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
