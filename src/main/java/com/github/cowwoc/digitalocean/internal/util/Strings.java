package com.github.cowwoc.digitalocean.internal.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * String helper functions.
 */
public final class Strings
{
	public static final DateTimeFormatter HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("H:mm:ss");
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
	 * @param value a number
	 * @return a string representation of the number with a visual separator every 3 digits
	 */
	public static String format(long value)
	{
		return FORMATTER.get().format(value);
	}

	/**
	 * @param collection a list of numbers
	 * @return a string representation of the list, with each number containing a visual separator every three
	 * 	digits
	 */
	public static List<String> format(Collection<Long> collection)
	{
		return collection.stream().map(Strings::format).toList();
	}

	private Strings()
	{
	}
}