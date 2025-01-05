package com.syncduo.server.model.dto.http.filesystem;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FileSystemResponse {

    private Integer code;

    private String message;

    @JsonProperty("folder_list")
    private List<Folder> folderList;

    private FileSystemResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static FileSystemResponse onSuccess(String message) {
        return new FileSystemResponse(200, message);
    }

    public static FileSystemResponse onSuccess(String message, List<Folder> folderList) {
        FileSystemResponse response = new FileSystemResponse(200, message);
        response.setFolderList(folderList);
        return response;
    }

    public static FileSystemResponse onError(String message) {
        return new FileSystemResponse(500, message);
    }
}
