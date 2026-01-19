package io.lumina.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class DateUtil {

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认日期格式
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 默认时间格式
     */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 格式化日期时间
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return formatDateTime(dateTime, DEFAULT_DATETIME_FORMAT);
    }

    /**
     * 格式化日期时间（自定义格式）
     */
    public static String formatDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 获取当前日期时间字符串
     */
    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATETIME_FORMAT));
    }

    /**
     * 获取当前日期字符串
     */
    public static String today() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
    }
}
