package com.syncduo.server.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@Data
@EqualsAndHashCode(callSuper = false)
public class SyncDuoException extends RuntimeException {

    private HttpStatus status;

    public SyncDuoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public SyncDuoException(HttpStatus status, Throwable cause) {
        super(cause);
        this.status = status;
    }

    public SyncDuoException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public SyncDuoException(String message) {
        super(message);
    }

    public SyncDuoException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getSyncDuoMessage() {
        StringBuilder sb = new StringBuilder();
        buildMessageChain(this, sb, 0);
        return sb.toString();
    }

    // 递归构建完整的异常消息链
    private static void buildMessageChain(Throwable throwable, StringBuilder sb, int depth) {
        // 终止条件
        if (throwable == null || depth > 20) return;
        // <exception name> : <exception message> -> <next>
        sb.append("%s : %s -> ".formatted(
                throwable.getClass().getSimpleName(),
                throwable instanceof SyncDuoException ? throwable.getMessage() : throwable.toString()));
        // 递归处理cause
        buildMessageChain(throwable.getCause(), sb, depth + 1);
    }

    @Override
    public String toString() {
        return getSyncDuoMessage();
    }
}
