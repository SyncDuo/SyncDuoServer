package com.syncduo.server.model.api.filesystem;


import lombok.Data;

import java.util.List;

@Data
public class FileSystemResponse {

    private Integer code;

    private String message;

    private String hostName;

    private List<Folder> folderList;

    private FileSystemResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static FileSystemResponse onSuccess() {
        return new FileSystemResponse(200, "success");
    }

    public static FileSystemResponse onSuccess(String message) {
        return new FileSystemResponse(200, message);
    }

    public static FileSystemResponse onSuccess(String message, List<Folder> folderList) {
        FileSystemResponse response = new FileSystemResponse(200, message);
        response.setFolderList(folderList);
        return response;
    }

    public static FileSystemResponse onSuccess(String message, String hostName) {
        FileSystemResponse response = new FileSystemResponse(200, message);
        response.setHostName(hostName);
        return response;
    }

    public static FileSystemResponse onError(String message) {
        return new FileSystemResponse(500, message);
    }
}
