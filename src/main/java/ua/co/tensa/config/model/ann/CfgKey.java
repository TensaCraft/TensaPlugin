package ua.co.tensa.config.model.ann;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be bound to a YAML path.
 * The field's initial value acts as default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CfgKey {
    String value();
    String comment() default ""; // optional inline comment to write on first create
}

