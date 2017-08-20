package lombok.experimental;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
public @interface CopyConstructor {

	public static enum Depth {
		EXCLUDE, REFERENCE, ONE
	}
	
	Depth depth() default Depth.REFERENCE;

	@Target(FIELD)
	@Retention(SOURCE)
	public @interface Exclude {}

	@Target(FIELD)
	@Retention(SOURCE)
	public @interface Reference {}

	@Target(FIELD)
	@Retention(SOURCE)
	public @interface Copy {

		/**
		 * Tells lombok which class to use to create a copy of the annotated field. For a field of type {@code T}, the given
		 * class invokes a {@code static T copy(T field)} method passing the field as the argument. If no such method exists a
		 * compilation error is thrown.
		 */
		Class<?> usingClass();
	}
}