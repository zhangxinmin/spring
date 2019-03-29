package com.test.myv1.anno;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS.RUNTIME)
@Documented
public @interface Service {
    String value() default "";
}
