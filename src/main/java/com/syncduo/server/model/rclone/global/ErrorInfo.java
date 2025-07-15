package com.syncduo.server.model.rclone.global;

import lombok.Data;

@Data
public class ErrorInfo { // 是 Rclone 全局的错误响应
    private String error;

    // input parameter
    private Object input;

    // http code
    private int status;

    private String path;
}
