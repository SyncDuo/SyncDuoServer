package com.syncduo.server.model.api.editor;

import lombok.experimental.Accessors;

@Accessors(chain = true)
public record NodeAnnotationInfo(
        String name,
        String description,
        String group
) {
}
