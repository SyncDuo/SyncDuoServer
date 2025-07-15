package com.syncduo.server.model.api.systemconfig;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateSystemConfigRequest {

    private Map<String, String> systemConfigMap;
}
