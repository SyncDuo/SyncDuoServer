package com.syncduo.server.workflow.core.annotaion;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Node {
    String name();
    String description() default "";
    String group() default "default";
    String[] inputParams() default {""};
    String[] outputParams() default {""};
}
