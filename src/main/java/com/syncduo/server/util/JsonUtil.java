package com.syncduo.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncduo.server.exception.SyncDuoException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // jackson to handle field to Instant
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Optional: Use ISO-8601 instead of timestamp

    public static <T> T parseResticJsonLine(
            String stdout,
            String condition,
            Class<T> clazz) throws SyncDuoException {
        List<T> result = parseResticJsonLines(stdout, condition, clazz);
        if (CollectionUtils.isEmpty(result)) {
            return null;
        }
        return result.get(0);
    }

    public static <T> List<T> parseResticJsonLines(
            String commandLineOutput,
            String condition,
            Class<T> clazz) throws SyncDuoException {
        if (ObjectUtils.isEmpty(clazz)) {
            throw new SyncDuoException("parseLine failed. clazz is null.");
        }
        if (StringUtils.isAnyBlank(commandLineOutput, condition)) {
            return null;
        }
        List<T> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(commandLineOutput))) {
            String line;
            while ((line = br.readLine()) != null && !line.isBlank()) {
                // Parse to a tree to inspect message_type quickly
                JsonNode root = objectMapper.readTree(line);
                String type = root.path("message_type").asText();
                if (condition.equals(type)) {
                    result.add(objectMapper.treeToValue(root, clazz));
                }
            }
            return result;
        } catch (IOException e) {
            throw new SyncDuoException("parseResticJsonLine failed. " +
                    "commandLineOutput is %s".formatted(commandLineOutput),
                    e);
        }
    }

    public static <T> T parseResticJsonDocument(
            String commandLineOutput,
            Class<T> clazz
    ) throws SyncDuoException {
        if (ObjectUtils.isEmpty(clazz)) {
            throw new SyncDuoException("parseResticJsonDocument failed. clazz is null.");
        }
        if (StringUtils.isBlank(commandLineOutput)) {
            throw new SyncDuoException("parseResticJsonDocument failed. commandLineOutput is null.");
        }
        try {
            return objectMapper.readValue(commandLineOutput, clazz);
        } catch (JsonProcessingException e) {
            throw new SyncDuoException("parseResticJsonDocument failed. " +
                    "commandLineOutput is %s".formatted(commandLineOutput),
                    e);
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
