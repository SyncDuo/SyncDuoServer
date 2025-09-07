package com.syncduo.server.configuration;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.syncduo.server.exception.*;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("controller failed. business logic failed. ", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleResourceNotFountException(ResourceNotFoundException e) {
        log.warn("controller failed. resource not found. ", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(FileOperationException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleFileOperationException(FileOperationException e) {
        log.warn("controller failed. file operation failed. ", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(JsonException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleJsonException(JsonException e) {
        log.warn("controller failed. json process failed. ", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleValidationException(ValidationException e) {
        log.warn("controller failed. validation failed. ", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(MybatisPlusException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleDBException(MybatisPlusException e) {
        log.warn("controller failed. db error happen.", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(
                new DbException("db error.", e)
        );
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(SyncDuoException.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleSyncDuoException(SyncDuoException e) {
        log.warn("controller failed. SyncDuoException happen", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(e);
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SyncDuoHttpResponse<Void>> handleGlobalException(Exception e) {
        log.warn("controller failed.", e);
        SyncDuoHttpResponse<Void> syncDuoHttpResponse = SyncDuoHttpResponse.fail(
                new SyncDuoException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString())
        );
        return ResponseEntity.status(syncDuoHttpResponse.getStatusCode()).body(syncDuoHttpResponse);
    }
}
