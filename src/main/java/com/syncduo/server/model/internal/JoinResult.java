package com.syncduo.server.model.internal;

import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

@Data
public class JoinResult<E1, E2> {

    private List<E1> leftOuterResult = new ArrayList<>();

    // inner result 每一个元素都是一个 pair, pair 里面 left 是来自 left list, right 来自 right list
    private List<ImmutablePair<E1, E2>> innerResult = new ArrayList<>();

    private List<E2> rightOuterResult = new ArrayList<>();

    public void addToInnerResult(E1 leftListElement, E2 rightListElement) {
        this.innerResult.add(new ImmutablePair<>(leftListElement, rightListElement));
    }
}
