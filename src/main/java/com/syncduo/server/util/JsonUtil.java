package com.syncduo.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncduo.server.exception.JsonException;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.exception.ValidationException;
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
            Class<T> clazz) throws ValidationException, JsonException {
        List<T> result = parseResticJsonLines(stdout, condition, clazz);
        if (CollectionUtils.isEmpty(result)) {
            return null;
        }
        return result.get(0);
    }

    public static <T> List<T> parseResticJsonLines(
            String commandLineOutput,
            String condition,
            Class<T> clazz) throws ValidationException, JsonException {
        if (ObjectUtils.isEmpty(clazz)) {
            throw new ValidationException("parseLine failed. clazz is null.");
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
            throw new JsonException("parseResticJsonLine failed. " +
                    "commandLineOutput is %s".formatted(commandLineOutput),
                    e);
        }
    }

    public static <T> T parseResticJsonDocument(
            String commandLineOutput,
            Class<T> clazz
    ) throws ValidationException, JsonException {
        if (ObjectUtils.isEmpty(clazz)) {
            throw new ValidationException("parseResticJsonDocument failed. clazz is null.");
        }
        if (StringUtils.isBlank(commandLineOutput)) {
            throw new ValidationException("parseResticJsonDocument failed. commandLineOutput is null.");
        }
        try {
            return objectMapper.readValue(commandLineOutput, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonException("parseResticJsonDocument failed. " +
                    "commandLineOutput is %s".formatted(commandLineOutput),
                    e);
        }
    }

    public static List<String> deserializeStringToList(String jsonString) throws JsonException {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new JsonException("deserializeStringToList failed. jsonString is %s".formatted(jsonString), e);
        }
    }

    public static String serializeListToString(List<String> list) throws JsonException {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new JsonException("serializeListToString failed. list is %s".formatted(list), e);
        }
    }
}
