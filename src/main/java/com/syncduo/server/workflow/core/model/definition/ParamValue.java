package com.syncduo.server.workflow.core.model.definition;

import com.syncduo.server.workflow.core.enums.ParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParamValue {

    private Object value;

    private ParamSourceType paramSourceType; // param 来源
}
