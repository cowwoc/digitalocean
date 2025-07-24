package io.github.cowwoc.digitalocean.test.util;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.Locale;

/**
 * Test helper functions.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class Tests
{
	/**
	 * Returns the name of the class + method that invokes this method.
	 *
	 * @return the caller's name
	 */
	public static String getCallerName()
	{
		// https://stackoverflow.com/a/52335318/14731
		StackFrame caller = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).
			walk(frame -> frame.skip(1).findFirst().
				orElseThrow());
		return (caller.getDeclaringClass().getSimpleName() + "-" + caller.getMethodName()).
			toLowerCase(Locale.ROOT);
	}

	private Tests()
	{
	}
}