package com.syncduo.server.model.rslsync.folder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FolderInfo {
    @JsonProperty("canencrypt")
    private Boolean canEncrypt;

    @JsonProperty("date_added")
    private Long dateAdded;

    @JsonProperty("folderid")
    private String folderId;

    @JsonProperty("ismanaged")
    private Boolean isManaged;

    @JsonProperty("name")
    private String name;

    @JsonProperty("readonlysecret")
    private String readonlySecret;

    @JsonProperty("secret")
    private String secret;

    @JsonProperty("secrettype")
    private Integer secretType;

    @JsonProperty("synclevel")
    private Integer syncLevel;

    // 下面的字段是 folder connected 之后才有的

    @JsonProperty("path")
    private String path;

    @JsonProperty
    private List<Peers> peers;

    @Data
    static class Peers {
        @JsonProperty("isonline")
        private boolean isOnline;

        private String name;
    }
}
