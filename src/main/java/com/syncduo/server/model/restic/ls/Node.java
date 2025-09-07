package com.syncduo.server.model.restic.ls;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

@Data
public class Node {

    @JsonProperty("message_type")
    private String messageType;  // Always "node"

    @JsonProperty("struct_type")
    @Deprecated
    private String structType;   // Always "node" (deprecated)

    private String name;         // Node name

    private String type;         // Node type, dir or file

    private String path;         // Node path

    private long uid;            // UID of node (uint32, stored in Long)

    private long gid;            // GID of node (uint32, stored in Long)

    private BigInteger size;           // Size in bytes (uint64, use BigInteger)

    private long mode;         // Node mode (os.FileMode equivalent, store as String or int). like chmod xxxx

    private String permissions;  // Node mode as string, like chown xxxx

    private Instant atime; // Node access time (time.Time)

    private Instant mtime; // Node modification time (time.Time)

    private Instant ctime; // Node creation time (time.Time)

    private BigInteger inode; // Inode number of node (uint64, use BigInteger)

    public static String getCondition() {
        return "node";
    }
}
