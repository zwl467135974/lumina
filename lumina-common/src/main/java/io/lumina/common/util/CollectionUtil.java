package io.lumina.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集合工具类
 *
 * @author Lumina Team
 * @since 1.0.0
 */
public class CollectionUtil {

    /**
     * 判断集合是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否不为空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断 Map 是否为空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否不为空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 获取第一个元素
     */
    public static <T> T getFirst(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 获取集合大小
     */
    public static int size(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * 判断集合是否包含指定元素
     */
    public static <T> boolean contains(Collection<T> collection, T element) {
        return collection != null && collection.contains(element);
    }

    /**
     * 数组转列表
     */
    @SafeVarargs
    public static <T> List<T> toList(T... elements) {
        return List.of(elements);
    }

    /**
     * 数组转集合
     */
    @SafeVarargs
    public static <T> Set<T> toSet(T... elements) {
        return Set.of(elements);
    }
}
