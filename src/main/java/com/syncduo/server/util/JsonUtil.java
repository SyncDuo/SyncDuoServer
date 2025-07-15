package com.syncduo.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncduo.server.exception.SyncDuoException;

import java.util.List;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object obj) throws SyncDuoException {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new SyncDuoException("toJson failed. obj is %s".formatted(obj), e);
        }
    }

    public static List<String> deserializeStringToList(String jsonString) throws SyncDuoException {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new SyncDuoException("deserializeStringToList failed. jsonString is %s".formatted(jsonString), e);
        }
    }

    public static String serializeListToString(List<String> list) throws SyncDuoException {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new SyncDuoException("serializeListToString failed. list is %s".formatted(list), e);
        }
    }
}
