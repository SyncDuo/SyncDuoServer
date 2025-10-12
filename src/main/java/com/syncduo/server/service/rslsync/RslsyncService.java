package com.syncduo.server.service.rslsync;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.model.rslsync.folder.FolderInfoResponse;
import com.syncduo.server.model.rslsync.global.RslsyncResponse;
import com.syncduo.server.model.rslsync.settings.FolderStoragePath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RslsyncService {

    private final RestClient rslsyncRestClient;

    public RslsyncService(RestClient rslsyncRestClient) {
        this.rslsyncRestClient = rslsyncRestClient;
    }

    public RslsyncResponse<FolderStoragePath> getFolderStoragePath() {
        return this.get("getfoldersstoragepath", FolderStoragePath.class);
    }

    public RslsyncResponse<FolderInfoResponse> getSyncFolderInfo() {
        return this.get("getsyncfolders", FolderInfoResponse.class);
    }

    private  <Res> RslsyncResponse<Res> get(String action, Class<Res> clazz) {
        RslsyncResponse<String> tokenResponse = this.getToken();
        if (StringUtils.isBlank(tokenResponse.getData())) {
            throw new BusinessException("getToken failed. token is null");
        }
        return this.handleClientPostResponse(
                this.rslsyncRestClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/")
                                .queryParam("token", tokenResponse.getData())
                                .queryParam("action", action)
                                .queryParam("t", Instant.now().getEpochSecond())
                                .build())
                        // 设置 cookie 保持 session
                        .header(HttpHeaders.COOKIE, tokenResponse.getCookie().toArray(new String[]{})),
                clazz,
                false
        );
    }

    private RslsyncResponse<String> getToken() {
        RslsyncResponse<String> htmlTokenResponse = this.handleClientPostResponse(
                this.rslsyncRestClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/token.html")
                                .queryParam("t", Instant.now().getEpochSecond())
                                .build())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE),
                String.class,
                true
        );
        if (!htmlTokenResponse.isSuccess()) {
            throw htmlTokenResponse.getBusinessException();
        }
        // 提取 token
        String htmlToken = htmlTokenResponse.getData();
        // html token 形如 <html><div id='token' style='display:none;'>token</div></html>
        int tokenStart = htmlToken.indexOf(">", htmlToken.indexOf("id='token'")) + 1;
        int tokenEnd = htmlToken.indexOf("</div>", tokenStart);
        String token = htmlToken.substring(tokenStart, tokenEnd);
        // 将原始的 html 置换为 token
        htmlTokenResponse.setData(token);
        return htmlTokenResponse;
    }

    private <T> RslsyncResponse<T> handleClientPostResponse(
            RestClient.RequestHeadersSpec<?> requestBodySpec,
            Class<T> dataType,
            boolean getCookie) {
        try {
            return requestBodySpec.exchange((httpRequest, httpResponse) -> {
                HttpStatusCode statusCode = httpResponse.getStatusCode();
                List<String> cookieList = new ArrayList<>();
                if (statusCode.is2xxSuccessful()) {
                    if (getCookie) {
                        cookieList = httpResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
                    }
                    return RslsyncResponse.success(
                            statusCode.value(),
                            httpResponse.bodyTo(dataType),
                            cookieList
                    );
                } else {
                    // 处理错误http响应（非2xx）
                    return RslsyncResponse.error(statusCode.value(), httpResponse.getStatusText());
                }
            });
        } catch (Exception e) {
            // 处理其他异常
            return RslsyncResponse.error(e);
        }
    }
}
