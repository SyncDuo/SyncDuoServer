package com.syncduo.server.configuration;

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

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<FlowResponse<Void>> handleValidationException(ValidationException e) {
        log.warn("controller failed. validation failed. ", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(e);
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FlowResponse<Void>> handleGlobalException(Exception e) {
        log.warn("server throw unexpected exception.", e);
        FlowResponse<Void> flowResponse = FlowResponse.failed(
                new FlowException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString())
        );
        return ResponseEntity.status(flowResponse.getStatusCode()).body(flowResponse);
    }
}
