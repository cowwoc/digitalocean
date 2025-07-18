package io.github.cowwoc.digitalocean.core.internal.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String helper functions.
 */
public final class Strings
{
	/**
	 * The {@code hour:minute:second} representation of a DateTime.
	 */
	public static final DateTimeFormatter HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("H:mm:ss");
	/**
	 * The {@code hour:minute} representation of a DateTime.
	 */
	public static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("H:mm");
	/**
	 * The regex pattern of a <a href="https://stackoverflow.com/a/6640851/14731">UUID</a>.
	 */
	public static final Pattern UUID = Pattern.compile(
		"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
	private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() ->
	{
		DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
		DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();

		symbols.setGroupingSeparator('_');
		formatter.setDecimalFormatSymbols(symbols);
		return formatter;
	});

	/**
	 * Returns the string representation of the number with a visual separator every 3 digits.
	 *
	 * @param value a number
	 * @return a string representation of the number with a visual separator every 3 digits
	 */
	public static String format(long value)
	{
		return FORMATTER.get().format(value);
	}

	private Strings()
	{
	}
}