package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.internal.MatchResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class MatchUtil {

    public static <E1, E2, E3, T> MatchResult<E1, E2> match(
            List<E1> leftList,
            List<E2> rightList,
            List<E3> lookupList,
            Function<E1, T> leftListKeyFunction,
            Function<E2, T> rightListKeyFunction,
            Function<E3, T> lookupListLeftKeyFunction,
            Function<E3, T> lookupListRightKeyFunction) throws SyncDuoException {
        if (CollectionUtils.isEmpty(lookupList)) {
            return new MatchResult<>();
        }
        // 检查参数, 返回不是 null, 说明 left/right list 有一个或者全为空
        MatchResult<E1, E2> tmp = checkParamDiffType(leftList, rightList);
        if (tmp != null) {
            return tmp;
        }
        MatchResult<E1, E2> result = new MatchResult<>();
        // 创建 map
        HashMap<T, E1> mapFromLeftList = new HashMap<>(leftList.size());
        for (E1 e : leftList) {
            mapFromLeftList.put(leftListKeyFunction.apply(e), e);
        }
        HashMap<T, E2> mapFromRightList = new HashMap<>(rightList.size());
        for (E2 e : rightList) {
            mapFromRightList.put(rightListKeyFunction.apply(e), e);
        }
        // 遍历 lookupList, 填充 matchResult
        for (E3 lookupItem : lookupList) {
            // 获取匹配的左键和右键
            T leftKey = lookupListLeftKeyFunction.apply(lookupItem);
            T rightKey = lookupListRightKeyFunction.apply(lookupItem);
            if (ObjectUtils.anyNull(leftKey, rightKey)) {
                continue;
            }
            // 根据左右键获取item
            E1 leftItem = mapFromLeftList.get(leftKey);
            E2 rightItem = mapFromRightList.get(rightKey);
            if (ObjectUtils.anyNull(leftItem, rightItem)) {
                continue;
            }
            // 从 map 中去除item, 并添加到结果
            mapFromLeftList.remove(leftKey);
            mapFromRightList.remove(rightKey);
            result.addToMatch(leftItem, rightItem);
        }
        // 填充 leftDisMatchResult
        result.setLeftMisMatchResult(mapFromLeftList.values().stream().toList());
        // 填充 rightDisMatchResult
        result.setRightMisMatchResult(mapFromRightList.values().stream().toList());
        return result;
    }

    private static <E> MatchResult<E, E> checkParam(List<E> leftList, List<E> rightList) {
        MatchResult<E, E> result = new MatchResult<>();
        // edge case handle
        if (CollectionUtils.isEmpty(leftList) && CollectionUtils.isEmpty(rightList)) {
            return result;
        }
        if (CollectionUtils.isEmpty(leftList)) {
            result.setRightMisMatchResult(rightList);
            return result;
        }
        if (CollectionUtils.isEmpty(rightList)) {
            result.setLeftMisMatchResult(leftList);
            return result;
        }
        return null;
    }

    private static <E1, E2> MatchResult<E1, E2> checkParamDiffType(List<E1> leftList, List<E2> rightList) {
        MatchResult<E1, E2> result = new MatchResult<>();
        // edge case handle
        if (CollectionUtils.isEmpty(leftList) && CollectionUtils.isEmpty(rightList)) {
            return result;
        }
        if (CollectionUtils.isEmpty(leftList)) {
            result.setRightMisMatchResult(rightList);
            return result;
        }
        if (CollectionUtils.isEmpty(rightList)) {
            result.setLeftMisMatchResult(leftList);
            return result;
        }
        return null;
    }
}
