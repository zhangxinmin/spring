package com.test.myv1.anno;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS.RUNTIME)
@Documented
public @interface Param {
    String value() default "";
}
