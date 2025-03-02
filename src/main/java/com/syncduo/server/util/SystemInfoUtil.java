package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

@Slf4j
public class SystemInfoUtil {

    public static String getHostName() throws SyncDuoException {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName();
        } catch (Exception e) {
            throw new SyncDuoException("getHostName failed.");
        }
    }
}
