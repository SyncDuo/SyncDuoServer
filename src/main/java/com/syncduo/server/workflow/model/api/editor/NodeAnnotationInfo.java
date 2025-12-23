package com.syncduo.server.workflow.model.api.editor;

import com.syncduo.server.workflow.core.annotaion.Node;
import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public record NodeAnnotationInfo (
        String name,
        String description,
        String group
) { }
