package com.syncduo.server.configuration;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {
    @ExceptionHandler(SyncDuoException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleSyncDuoException(SyncDuoException e) {
        log.warn(e.getMessage(), e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }
}
