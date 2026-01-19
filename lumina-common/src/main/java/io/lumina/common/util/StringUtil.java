package io.lumina.common.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 字符串工具类
 *
 * <p>基于 Apache Commons Lang3 的字符串工具扩展。
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class StringUtil extends StringUtils {

    /**
     * 判断字符串是否为空（包括 null、空字符串、空白字符）
     */
    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * 判断字符串是否为空（包括 null、空字符串）
     */
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return StringUtils.isNotEmpty(str);
    }

    /**
     * 字符串截取（避免中文乱码）
     */
    public static String truncate(String str, int maxLength) {
        if (isBlank(str) || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * 驼峰转下划线
     */
    public static String camelToUnderscore(String camelCase) {
        if (isBlank(camelCase)) {
            return "";
        }
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 下划线转驼峰
     */
    public static String underscoreToCamel(String underscore) {
        if (isBlank(underscore)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] parts = underscore.split("_");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (isBlank(part)) {
                continue;
            }
            if (i == 0) {
                result.append(part.toLowerCase());
            } else {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
}
