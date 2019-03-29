package com.test.myv1.anno;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS.RUNTIME)
@Documented
public @interface Autowired {
    String value() default "";
}
