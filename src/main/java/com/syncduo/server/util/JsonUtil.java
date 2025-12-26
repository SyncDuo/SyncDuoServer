package com.syncduo.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncduo.server.exception.JsonException;
import com.syncduo.server.exception.ValidationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // jackson to handle field to Instant
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Optional: Use ISO-8601 instead of timestamp
    private static final TypeFactory typeFactory = objectMapper.getTypeFactory();

    public static <T> T convertValue(Object value, TypeReference<T> typeRef) {
        return objectMapper.convertValue(value, typeRef);
    }

    public static boolean validateType(TypeReference<?> typeRef, Object value) {
        if (value == null) {
            return false; // 或根据需求调整
        }

        try {
            JavaType targetType = typeFactory.constructType(typeRef.getType());
            return validateRecursive(targetType, value, new HashSet<>());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateRecursive(JavaType targetType, Object value, Set<Object> visited) {
        if (visited.contains(value)) {
            return true; // 循环引用，跳过
        }
        visited.add(value);

        try {
            Class<?> targetClass = targetType.getRawClass();
            Class<?> actualClass = value.getClass();

            // 1. 严格类型检查：必须完全匹配
            if (!targetClass.equals(actualClass)) {
                // 特殊处理：基本类型和包装类型视为相同
                if (!isPrimitiveOrWrapperMatch(targetClass, actualClass)) {
                    return false;
                }
            }

            // 2. 如果是简单类型，验证通过
            if (isSimpleType(targetClass)) {
                return true;
            }

            // 3. 如果是List/Collection
            if (List.class.isAssignableFrom(targetClass) || Collection.class.isAssignableFrom(targetClass)) {
                return validateList(targetType, (Collection<?>) value, visited);
            }

            // 4. 如果是Map
            if (Map.class.isAssignableFrom(targetClass)) {
                return validateMap(targetType, (Map<?, ?>) value, visited);
            }

            // 5. 如果是数组
            if (targetClass.isArray()) {
                return validateArray(targetType, value, visited);
            }

            // 6. 如果是POJO，检查字段
            return validatePojo(targetType, value, visited);

        } finally {
            visited.remove(value);
        }
    }

    private static boolean validateList(JavaType targetType, Collection<?> collection, Set<Object> visited) {
        // 获取List的元素类型
        JavaType elementType = targetType.getContentType();

        for (Object element : collection) {
            if (element == null) {
                if (elementType.isPrimitive()) {
                    return false; // 原始类型不能为null
                }
                continue;
            }

            if (!validateRecursive(elementType, element, visited)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateMap(JavaType targetType, Map<?, ?> map, Set<Object> visited) {
        JavaType keyType = targetType.getKeyType();
        JavaType valueType = targetType.getContentType();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // 检查key
            if (entry.getKey() != null && !validateRecursive(keyType, entry.getKey(), visited)) {
                return false;
            }

            // 检查value
            if (entry.getValue() == null) {
                if (valueType.isPrimitive()) {
                    return false;
                }
            } else if (!validateRecursive(valueType, entry.getValue(), visited)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateArray(JavaType targetType, Object array, Set<Object> visited) {
        int length = java.lang.reflect.Array.getLength(array);

        // 数组的元素类型
        JavaType elementType = typeFactory.constructType(array.getClass().getComponentType());

        for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(array, i);
            if (element != null && !validateRecursive(elementType, element, visited)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validatePojo(JavaType targetType, Object value, Set<Object> visited) {
        Class<?> clazz = value.getClass();
        // 获取所有字段（包括父类）
        for (Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            try {
                Object fieldValue = field.get(value);
                if (fieldValue == null) {
                    continue; // null值跳过
                }
                // 获取字段的类型（考虑泛型）
                JavaType fieldType = typeFactory.constructType(field.getGenericType());

                if (!validateRecursive(fieldType, fieldValue, visited)) {
                    return false;
                }

            } catch (Exception e) {
                // 字段访问失败，跳过
                continue;
            }
        }
        return true;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    public static List<String> getResticJsonLinesByMsgType(String json, String msgType) {
        ArrayList<String> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(json))) {
            String line;
            while ((line = br.readLine()) != null && !line.isBlank()) {
                // Parse to a tree to inspect message_type quickly
                JsonNode root = objectMapper.readTree(line);
                String type = root.path("message_type").asText();
                if (type.equals(msgType)) {
                    result.add(objectMapper.writeValueAsString(root));
                }
            }
            return result;
        } catch (IOException e) {
            throw new JsonException("getResticJsonLineByMsgType failed. " +
                    "commandLineOutput is %s".formatted(json),
                    e);
        }
    }

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

    public static <T> T readLastLine(String commandLineOutput, Class<T> clazz) throws JsonException {
        if (StringUtils.isAnyBlank(commandLineOutput)) {
            return null;
        }
        String lastLine = null;
        try (BufferedReader br = new BufferedReader(new StringReader(commandLineOutput))) {
            String line;
            while ((line = br.readLine()) != null && !line.isBlank()) {
                line = line.trim();
                if (ObjectUtils.isNotEmpty(line)) {
                    lastLine = line;
                }
            }
        } catch (IOException e) {
            throw new JsonException("readLastLine failed. " +
                    "commandLineOutput is %s".formatted(commandLineOutput),
                    e);
        }
        try {
            return StringUtils.isEmpty(lastLine) ? null : objectMapper.readValue(lastLine, clazz);
        } catch (JsonProcessingException e) {
            throw new JsonException(("readLastLine failed. " +
                    "commandLineOutput is %s").formatted(commandLineOutput),
                    e);
        }
    }

    public static <T> T parseResticJsonDocument(
            String commandLineOutput,
            Class<T> clazz
    ) throws ValidationException, JsonException {
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

    public static <T> T deserializeObjectToPojo(Object source, Class<T> clazz) throws JsonException {
        try {
            return objectMapper.convertValue(source, clazz);
        } catch (IllegalArgumentException e) {
            throw new JsonException("deserializeObjectToPojo failed. source is %s".formatted(source), e);
        }
    }

    public static List<String> deserializeStringToList(String jsonString) throws JsonException {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new JsonException("deserializeStringToList failed. jsonString is %s".formatted(jsonString), e);
        }
    }

    public static <T> List<T> deserToList(String jsonString, Class<T> elementType) {
        try {
            return objectMapper.readValue(
                    jsonString,
                    typeFactory.constructCollectionType(List.class, elementType)
            );
        } catch (JsonProcessingException e) {
            throw new JsonException("deserToList failed", e);
        }
    }

    public static String serializeListToString(List<String> list) throws JsonException {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new JsonException("serializeListToString failed. list is %s".formatted(list), e);
        }
    }

    public static String serializeToString(Object object) throws JsonException {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JsonException("serializeToString failed. object is %s".formatted(object), e);
        }
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Integer.class || clazz == int.class ||
                clazz == Long.class || clazz == long.class ||
                clazz == Double.class || clazz == double.class ||
                clazz == Float.class || clazz == float.class ||
                clazz == Boolean.class || clazz == boolean.class ||
                clazz == Character.class || clazz == char.class ||
                clazz == Byte.class || clazz == byte.class ||
                clazz == Short.class || clazz == short.class ||
                clazz.isEnum() ||
                clazz == java.util.Date.class ||
                clazz == java.math.BigDecimal.class ||
                clazz == java.math.BigInteger.class ||
                clazz == java.util.UUID.class;
    }

    private static boolean isPrimitiveOrWrapperMatch(Class<?> c1, Class<?> c2) {
        if (c1 == int.class && c2 == Integer.class) return true;
        if (c1 == Integer.class && c2 == int.class) return true;
        if (c1 == long.class && c2 == Long.class) return true;
        if (c1 == Long.class && c2 == long.class) return true;
        if (c1 == double.class && c2 == Double.class) return true;
        if (c1 == Double.class && c2 == double.class) return true;
        if (c1 == float.class && c2 == Float.class) return true;
        if (c1 == Float.class && c2 == float.class) return true;
        if (c1 == boolean.class && c2 == Boolean.class) return true;
        if (c1 == Boolean.class && c2 == boolean.class) return true;
        if (c1 == char.class && c2 == Character.class) return true;
        if (c1 == Character.class && c2 == char.class) return true;
        if (c1 == byte.class && c2 == Byte.class) return true;
        if (c1 == Byte.class && c2 == byte.class) return true;
        if (c1 == short.class && c2 == Short.class) return true;
        return c1 == Short.class && c2 == short.class;
    }
}
