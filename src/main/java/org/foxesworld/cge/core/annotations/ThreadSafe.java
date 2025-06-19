package org.foxesworld.cge.core.annotations;

import java.lang.annotation.*;

/**
 * Indicates that a class is thread-safe, meaning it can be used concurrently
 * by multiple threads without external synchronization or coordination.
 *
 * <p>A class marked as {@code @ThreadSafe} should ensure that all its public methods
 * and fields are safe for concurrent access. This might be achieved through:
 * <ul>
 *   <li>Immutability</li>
 *   <li>Internal synchronization</li>
 *   <li>Thread-confined state</li>
 *   <li>Thread-local variables</li>
 *   <li>Volatile fields</li>
 *   <li>Atomic operations</li>
 * </ul>
 *
 * <p>This annotation is applied at compile-time and doesn't enforce thread safety at runtime.
 * It serves as documentation and may be used by static analysis tools.
 *
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface ThreadSafe {
    /**
     * Optional description explaining how thread safety is achieved.
     *
     * @return explanation of thread safety mechanisms used
     */
    String value() default "";
}