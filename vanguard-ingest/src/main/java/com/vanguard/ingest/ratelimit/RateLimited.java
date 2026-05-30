package com.vanguard.ingest.ratelimit;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {
    String key() default "";
    int capacity() default 60;
    int refillPerMinute() default 60;
}
