package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.internal.JoinResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class JoinUtil {

    public static <E1, E2, T> JoinResult<E1, E2> allJoin(
            List<E1> leftList,
            List<E2> rightList,
            Function<E1, T> function1,
            Function<E2, T> function2) throws SyncDuoException {
        // 检查参数, 返回不是 null, 说明 left/right list 有一个或者全为空
        JoinResult<E1, E2> tmp = checkParamDiffType(leftList, rightList);
        if (tmp != null) {
            return tmp;
        }
        JoinResult<E1, E2> result = new JoinResult<>();
        // 创建 map
        HashMap<T, E1> mapFromLeftList = new HashMap<>(leftList.size());
        for (E1 e : leftList) {
            mapFromLeftList.put(function1.apply(e), e);
        }
        HashMap<T, E2> mapFromRightList = new HashMap<>(rightList.size());
        for (E2 e : rightList) {
            mapFromRightList.put(function2.apply(e), e);
        }
        // 遍历 mapFromLeftList, 填充 leftOuterList 和 innerJoinList
        mapFromLeftList.forEach((k, v) -> {
            if (mapFromRightList.containsKey(k)) {
                result.addToInnerResult(v, mapFromRightList.get(k));
            } else {
                result.getLeftOuterResult().add(v);
            }
        });
        // 遍历 mapFromRightList, 填充 rightOuterList
        mapFromRightList.forEach((k, v) -> {
            if (!mapFromLeftList.containsKey(k)) {
                result.getRightOuterResult().add(v);
            }
        });
        return result;
    }

    public static <E, T> JoinResult<E, E> allJoin(
            List<E> leftList,
            List<E> rightList,
            Function<E, T> function) throws SyncDuoException {
        // 检查参数, 返回不是 null, 说明 left/right list 有一个或者全为空
        JoinResult<E, E> tmp = checkParam(leftList, rightList);
        if (tmp != null) {
            return tmp;
        }
        JoinResult<E, E> result = new JoinResult<>();
        // 创建 map
        HashMap<T, E> mapFromLeftList = new HashMap<>(leftList.size());
        for (E e : leftList) {
            mapFromLeftList.put(function.apply(e), e);
        }
        HashMap<T, E> mapFromRightList = new HashMap<>(rightList.size());
        for (E e : rightList) {
            mapFromRightList.put(function.apply(e), e);
        }
        // 遍历 mapFromLeftList, 填充 leftOuterList 和 innerJoinList
        mapFromLeftList.forEach((k, v) -> {
            if (mapFromRightList.containsKey(k)) {
                result.addToInnerResult(v, mapFromRightList.get(k));
            } else {
                result.getLeftOuterResult().add(v);
            }
        });
        // 遍历 mapFromRightList, 填充 rightOuterList
        mapFromRightList.forEach((k, v) -> {
            if (!mapFromLeftList.containsKey(k)) {
                result.getRightOuterResult().add(v);
            }
        });
        return result;
    }

    private static <E> JoinResult<E, E> checkParam(List<E> leftList, List<E> rightList) {
        JoinResult<E, E> result = new JoinResult<>();
        // edge case handle
        if (CollectionUtils.isEmpty(leftList) && CollectionUtils.isEmpty(rightList)) {
            return result;
        }
        if (CollectionUtils.isEmpty(leftList)) {
            result.setRightOuterResult(rightList);
            return result;
        }
        if (CollectionUtils.isEmpty(rightList)) {
            result.setLeftOuterResult(leftList);
            return result;
        }
        return null;
    }

    private static <E1, E2> JoinResult<E1, E2> checkParamDiffType(List<E1> leftList, List<E2> rightList) {
        JoinResult<E1, E2> result = new JoinResult<>();
        // edge case handle
        if (CollectionUtils.isEmpty(leftList) && CollectionUtils.isEmpty(rightList)) {
            return result;
        }
        if (CollectionUtils.isEmpty(leftList)) {
            result.setRightOuterResult(rightList);
            return result;
        }
        if (CollectionUtils.isEmpty(rightList)) {
            result.setLeftOuterResult(leftList);
            return result;
        }
        return null;
    }
}
