package com.syncduo.server.workflow.node.restic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigInteger;
import java.time.OffsetDateTime;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SnapshotNode {
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
     * 大小
     */
    @JsonProperty("size")
    private BigInteger size; // 文件夹没有这个 field

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
    private BigInteger inode;

    /**
     * 消息类型：node
     */
    @JsonProperty("message_type")
    private String messageType;

    /**
     * 结构类型：node
     */
    @JsonProperty("struct_type")
    @Deprecated
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
}
