package com.syncduo.server.workflow.node.restic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SnapshotItem {
    /**
     * 节点名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * 节点类型：dir（目录）或 file（文件）
     */
    @JsonProperty("type")
    private String type;

    /**
     * 完整路径
     */
    @JsonProperty("path")
    private String path;

    /**
     * 用户ID
     */
    @JsonProperty("uid")
    private Integer uid;

    /**
     * 组ID
     */
    @JsonProperty("gid")
    private Integer gid;

    /**
     * 文件模式（八进制表示）
     */
    @JsonProperty("mode")
    private Long mode;

    /**
     * 权限字符串（如：drwxr-xr-x）
     */
    @JsonProperty("permissions")
    private String permissions;

    /**
     * 修改时间
     */
    @JsonProperty("mtime")
    private OffsetDateTime mtime;

    /**
     * 访问时间
     */
    @JsonProperty("atime")
    private OffsetDateTime atime;

    /**
     * 创建/状态改变时间
     */
    @JsonProperty("ctime")
    private OffsetDateTime ctime;

    /**
     * inode 编号
     */
    @JsonProperty("inode")
    private Long inode;

    /**
     * 消息类型：node
     */
    @JsonProperty("message_type")
    private String messageType;

    /**
     * 结构类型：node
     */
    @JsonProperty("struct_type")
    private String structType;

    /**
     * 判断是否为目录
     * @return true 如果是目录，false 如果是文件
     */
    public boolean isDirectory() {
        return "dir".equalsIgnoreCase(type);
    }

    /**
     * 判断是否为文件
     * @return true 如果是文件，false 如果是目录
     */
    public boolean isFile() {
        return "file".equalsIgnoreCase(type);
    }

    /**
     * 获取文件/目录的父目录路径
     * @return 父目录路径，如果是根目录则返回 null
     */
    public String getParentPath() {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == 0) {
            return "/";
        } else if (lastSlash > 0) {
            return path.substring(0, lastSlash);
        }
        return null;
    }

    /**
     * 从权限字符串中解析权限位
     * @return 权限位字符串（如：rwxr-xr-x）
     */
    public String getPermissionBits() {
        if (permissions == null || permissions.length() < 10) {
            return null;
        }
        // 权限字符串格式：drwxr-xr-x，我们需要后9位
        return permissions.substring(1);
    }

    /**
     * 获取文件类型标识符
     * @return 文件类型字符（d=目录，-=文件，l=链接等）
     */
    public Character getFileTypeChar() {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        return permissions.charAt(0);
    }

    /**
     * 将模式转换为八进制字符串
     * @return 八进制表示的权限（如：0755）
     */
    public String getModeOctal() {
        if (mode == null) {
            return null;
        }
        // 转换为八进制，并确保有4位
        return String.format("%04o", mode);
    }
}
