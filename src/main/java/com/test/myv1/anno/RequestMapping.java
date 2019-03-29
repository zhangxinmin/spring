package com.test.myv1.anno;

import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.CLASS.RUNTIME)
@Documented
public @interface RequestMapping {
    String value() default "";
}
