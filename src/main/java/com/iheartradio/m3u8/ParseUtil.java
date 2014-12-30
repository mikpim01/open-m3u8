package com.iheartradio.m3u8;

import com.iheartradio.m3u8.data.Resolution;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

final class ParseUtil {
    public static int parseInt(String string, String tag) throws ParseException {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException exception) {
            throw new ParseException(ParseExceptionType.NOT_JAVA_INTEGER, tag);
        }
    }

    public static float parseFloat(String string, String tag) throws ParseException {
        try {
            return Float.parseFloat(string);
        } catch (NumberFormatException exception) {
            throw new ParseException(ParseExceptionType.NOT_JAVA_FLOAT, tag);
        }
    }

    public static List<Byte> parseHexadecimal(String hexString, String tag) throws ParseException {
        final List<Byte> bytes = new ArrayList<Byte>();
        final Matcher matcher = Constants.HEXADECIMAL_PATTERN.matcher(hexString.toUpperCase(Locale.US));

        if (matcher.matches()) {
            String valueString = matcher.group(1);

            for (char c : valueString.toCharArray()) {
                bytes.add(hexCharToByte(c));
            }

            return bytes;
        } else {
            throw new ParseException(ParseExceptionType.INVALID_HEXADECIMAL_STRING, tag);
        }
    }

    private static byte hexCharToByte(char hex) {
        if (hex >= 'A') {
            return (byte) ((hex & 0xF) + 9);
        } else {
            return (byte) (hex & 0xF);
        }
    }

    public static boolean parseYesNo(String yesNoString, String tag) throws ParseException {
        if (yesNoString.equals("YES")) {
            return true;
        } else if (yesNoString.equals("NO")) {
            return false;
        } else {
            throw new ParseException(ParseExceptionType.NOT_YES_OR_NO, tag);
        }
    }

    public static Resolution parseResolution(String resolutionString, String tag) throws ParseException {
        Matcher matcher = Constants.RESOLUTION_PATTERN.matcher(resolutionString);

        if (!matcher.matches()) {
            throw new ParseException(ParseExceptionType.INVALID_RESOLUTION_FORMAT, tag);
        }

        return new Resolution(parseInt(matcher.group(1), tag), parseInt(matcher.group(2), tag));
    }

    public static String parseQuotedString(String quotedString, String tag) throws ParseException {
        final StringBuilder builder = new StringBuilder();

        boolean isEscaping = false;
        int quotesFound = 0;

        for (int i = 0; i < quotedString.length(); ++i) {
            final char c = quotedString.charAt(i);

            if (i == 0 && c != '"') {
                if (isWhitespace(c)) {
                    throw new ParseException(ParseExceptionType.ILLEGAL_WHITESPACE, tag);
                } else {
                    throw new ParseException(ParseExceptionType.INVALID_QUOTED_STRING, tag);
                }
            } else if (quotesFound == 2) {
                if (isWhitespace(c)) {
                    throw new ParseException(ParseExceptionType.ILLEGAL_WHITESPACE, tag);
                } else {
                    throw new ParseException(ParseExceptionType.INVALID_QUOTED_STRING, tag);
                }
            } else if (i == quotedString.length() - 1) {
                if (c != '"' || isEscaping) {
                    throw new ParseException(ParseExceptionType.UNCLOSED_QUOTED_STRING, tag);
                }
            } else {
                if (isEscaping) {
                    builder.append(c);
                    isEscaping = false;
                } else {
                    if (c == '\\') {
                        isEscaping = true;
                    } else if (c == '"') {
                        ++quotesFound;
                    } else {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.toString();
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    public static String decodeUrl(String encodedUrl, Encoding encoding) throws ParseException {
        try {
            return URLDecoder.decode(encodedUrl.replace("+", "%2B"), encoding.value);
        } catch (UnsupportedEncodingException exception) {
            throw new ParseException(ParseExceptionType.INTERNAL_ERROR);
        }
    }

    public static List<Attribute> parseAttributeList(String line, String tag) throws ParseException {
        final List<Attribute> attributes = new ArrayList<Attribute>();
        final Set<String> attributeNames = new HashSet<String>();

        for (String string : splitAttributeList(line, tag)) {
            final int separator = string.indexOf(Constants.ATTRIBUTE_SEPARATOR);
            final int quote = string.indexOf("\"");

            if (separator == -1 || (quote != -1 && quote < separator)) {
                throw new ParseException(ParseExceptionType.MISSING_ATTRIBUTE_SEPARATOR, tag);
            } else {
                final String name = string.substring(0, separator);
                final String value = string.substring(separator + 1);

                if (name.isEmpty()) {
                    throw new ParseException(ParseExceptionType.MISSING_ATTRIBUTE_NAME, tag);
                }

                if (value.isEmpty()) {
                    throw new ParseException(ParseExceptionType.MISSING_ATTRIBUTE_VALUE, tag);
                }

                if (!attributeNames.add(name)) {
                    throw new ParseException(ParseExceptionType.MULTIPLE_ATTRIBUTE_NAME_INSTANCES, tag + ":" + name);
                }

                attributes.add(new Attribute(name, value));
            }
        }

        return attributes;
    }

    public static List<String> splitAttributeList(String line, String tag) throws ParseException {
        final List<Integer> splitIndices = new ArrayList<Integer>();
        final List<String> attributes = new ArrayList<String>();

        int startIndex = line.indexOf(Constants.EXT_TAG_END) + 1;
        boolean isQuotedString = false;
        boolean isEscaping = false;

        for (int i = startIndex; i < line.length(); i++) {
            if (isQuotedString) {
                if (isEscaping) {
                    isEscaping = false;
                } else {
                    char c = line.charAt(i);

                    if (c == '\\') {
                        isEscaping = true;
                    } else if (c == '"') {
                        isQuotedString = false;
                    }
                }
            } else {
                char c = line.charAt(i);

                if (c == ',') {
                    splitIndices.add(i);
                } else if (c == '"') {
                    isQuotedString = true;
                }
            }
        }

        if (isQuotedString) {
            throw new ParseException(ParseExceptionType.UNCLOSED_QUOTED_STRING, tag);
        }

        for (Integer splitIndex : splitIndices) {
            attributes.add(line.substring(startIndex, splitIndex));
            startIndex = splitIndex + 1;
        }

        attributes.add(line.substring(startIndex));
        return attributes;
    }
}