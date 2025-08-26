package top.contins.authservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用对象转换工具类
 * 提供对象属性复制、类型转换、JSON转换等功能
 * 
 * @author contins
 * @since 2025-08-17
 */
@Component
public class ObjectConvertUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对象属性复制 - 使用Spring BeanUtils
     * 
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 复制后的目标对象
     */
    public static <T> T copyProperties(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象属性复制失败", e);
        }
    }

    /**
     * 对象属性复制 - 忽略指定属性
     * 
     * @param source           源对象
     * @param targetClass      目标类型
     * @param ignoreProperties 忽略的属性名
     * @param <T>              目标类型泛型
     * @return 复制后的目标对象
     */
    public static <T> T copyProperties(Object source, Class<T> targetClass, String... ignoreProperties) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target, ignoreProperties);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象属性复制失败", e);
        }
    }

    /**
     * 集合对象属性复制
     * 
     * @param sourceList  源对象集合
     * @param targetClass 目标类型
     * @param <S>         源类型泛型
     * @param <T>         目标类型泛型
     * @return 复制后的目标对象集合
     */
    public static <S, T> List<T> copyList(List<S> sourceList, Class<T> targetClass) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        return sourceList.stream()
                .map(source -> copyProperties(source, targetClass))
                .collect(Collectors.toList());
    }

    /**
     * 集合对象属性复制 - 使用自定义转换函数
     * 
     * @param sourceList 源对象集合
     * @param converter  转换函数
     * @param <S>        源类型泛型
     * @param <T>        目标类型泛型
     * @return 转换后的目标对象集合
     */
    public static <S, T> List<T> convertList(List<S> sourceList, Function<S, T> converter) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return new ArrayList<>();
        }
        return sourceList.stream()
                .map(converter)
                .collect(Collectors.toList());
    }

    /**
     * 对象转Map
     * 
     * @param obj 待转换对象
     * @return Map对象
     */
    public static Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                map.put(field.getName(), value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("对象转Map失败", e);
            }
        }
        return map;
    }

    /**
     * Map转对象
     * 
     * @param map         源Map
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 转换后的对象
     */
    public static <T> T mapToObject(Map<String, Object> map, Class<T> targetClass) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        try {
            T obj = targetClass.getDeclaredConstructor().newInstance();
            Field[] fields = targetClass.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object value = map.get(field.getName());
                if (value != null) {
                    // 类型转换
                    Object convertedValue = convertValue(value, field.getType());
                    field.set(obj, convertedValue);
                }
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("Map转对象失败", e);
        }
    }

    /**
     * JSON字符串转对象
     * 
     * @param json        JSON字符串
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 转换后的对象
     */
    public static <T> T jsonToObject(String json, Class<T> targetClass) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON转对象失败", e);
        }
    }

    /**
     * JSON字符串转集合
     * 
     * @param json        JSON字符串
     * @param targetClass 目标类型
     * @param <T>         目标类型泛型
     * @return 转换后的集合
     */
    public static <T> List<T> jsonToList(String json, Class<T> targetClass) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, targetClass));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON转集合失败", e);
        }
    }

    /**
     * 值类型转换
     * 
     * @param value      原始值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        String strValue = value.toString();

        try {
            // 基本类型转换
            if (targetType == String.class) {
                return strValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.valueOf(strValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.valueOf(strValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.valueOf(strValue);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.valueOf(strValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.valueOf(strValue);
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(strValue);
            } else if (targetType == Date.class) {
                return parseDate(strValue);
            } else if (targetType == LocalDate.class) {
                return LocalDate.parse(strValue);
            } else if (targetType == LocalDateTime.class) {
                return LocalDateTime.parse(strValue);
            }
        } catch (Exception e) {
            throw new RuntimeException("类型转换失败: " + value + " -> " + targetType.getSimpleName(), e);
        }

        return value;
    }

    /**
     * 日期字符串解析
     * 支持多种日期格式
     * 
     * @param dateStr 日期字符串
     * @return Date对象
     */
    private static Date parseDate(String dateStr) {
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd",
                "dd/MM/yyyy",
                "dd-MM-yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {
                // 继续尝试下一个格式
            }
        }

        throw new RuntimeException("无法解析日期格式: " + dateStr);
    }

    /**
     * 判断对象是否为空
     * 
     * @param obj 待判断对象
     * @return 是否为空
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }

        if (obj instanceof String) {
            return ((String) obj).trim().isEmpty();
        }

        if (obj instanceof Collection) {
            return ((Collection<?>) obj).isEmpty();
        }

        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }

        if (obj.getClass().isArray()) {
            return ((Object[]) obj).length == 0;
        }

        return false;
    }

    /**
     * 获取对象的所有非空字段值
     * 
     * @param obj 源对象
     * @return 非空字段Map
     */
    public static Map<String, Object> getNonEmptyFields(Object obj) {
        Map<String, Object> allFields = objectToMap(obj);
        return allFields.entrySet().stream()
                .filter(entry -> !isEmpty(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
    }
}