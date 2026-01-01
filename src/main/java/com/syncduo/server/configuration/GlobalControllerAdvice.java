package com.syncduo.server.configuration;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.syncduo.server.exception.*;
import com.syncduo.server.model.api.global.FlowResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<FlowResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("controller failed. business logic failed. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<FlowResponse<Void>> handleResourceNotFountException(ResourceNotFoundException e) {
        log.warn("controller failed. resource not found. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(FileOperationException.class)
    public ResponseEntity<FlowResponse<Void>> handleFileOperationException(FileOperationException e) {
        log.warn("controller failed. file operation failed. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(JsonException.class)
    public ResponseEntity<FlowResponse<Void>> handleJsonException(JsonException e) {
        log.warn("controller failed. json process failed. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<FlowResponse<Void>> handleValidationException(ValidationException e) {
        log.warn("controller failed. validation failed. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(MybatisPlusException.class)
    public ResponseEntity<FlowResponse<Void>> handleDBException(MybatisPlusException e) {
        log.warn("controller failed. db error happen.", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(
                new DbException("db error.", e)
        );
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(SyncDuoException.class)
    public ResponseEntity<FlowResponse<Void>> handleSyncDuoException(SyncDuoException e) {
        log.warn("controller failed. SyncDuoException happen", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FlowResponse<Void>> handleGlobalException(Exception e) {
        log.warn("controller failed.", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(
                new SyncDuoException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString())
        );
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }
}
