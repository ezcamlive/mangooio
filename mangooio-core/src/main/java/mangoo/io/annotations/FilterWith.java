package mangoo.io.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import mangoo.io.interfaces.MangooControllerFilter;

/**
 *
 * @author svenkubiak
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FilterWith {
    Class<? extends MangooControllerFilter>[] value();
}