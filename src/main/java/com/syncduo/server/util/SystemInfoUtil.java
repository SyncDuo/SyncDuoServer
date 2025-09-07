package com.syncduo.server.util;

import com.syncduo.server.exception.ResourceNotFoundException;
import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class SystemInfoUtil {

    public static String getHostName() throws ResourceNotFoundException {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new ResourceNotFoundException("getHostName failed.", e);
        }
    }
}
