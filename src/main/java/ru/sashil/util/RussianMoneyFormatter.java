package ru.sashil.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RussianMoneyFormatter {

    private static final String[] HUNDREDS = {
        "", "сто", "двести", "триста", "четыреста", "пятьсот",
        "шестьсот", "семьсот", "восемьсот", "девятьсот"
    };
    private static final String[] TENS = {
        "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
        "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    };
    private static final String[] FROM_TEN_TO_NINETEEN = {
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    };
    private static final String[] UNITS_MALE = {
        "", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    };
    private static final String[] UNITS_FEMALE = {
        "", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    };

    private RussianMoneyFormatter() {
    }

    public static String formatRublesAndKopecks(Double amount) {
        BigDecimal normalized = BigDecimal.valueOf(amount == null ? 0.0 : amount)
            .setScale(2, RoundingMode.HALF_UP);
        long rubles = normalized.longValue();
        int kopecks = normalized.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        return toWords(rubles) + " " + plural(rubles, "рубль", "рубля", "рублей")
            + " " + String.format("%02d", kopecks) + " "
            + plural(kopecks, "копейка", "копейки", "копеек");
    }

    private static String toWords(long value) {
        if (value == 0) {
            return "ноль";
        }

        StringBuilder builder = new StringBuilder();
        appendTriplet(builder, (int) (value / 1_000_000), "миллион", "миллиона", "миллионов", false);
        appendTriplet(builder, (int) ((value / 1_000) % 1_000), "тысяча", "тысячи", "тысяч", true);
        appendTriplet(builder, (int) (value % 1_000), "", "", "", false);

        return builder.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static void appendTriplet(StringBuilder builder, int triplet, String one, String few, String many, boolean feminine) {
        if (triplet == 0) {
            return;
        }

        int hundreds = triplet / 100;
        int tens = (triplet / 10) % 10;
        int units = triplet % 10;

        append(builder, HUNDREDS[hundreds]);

        if (tens == 1) {
            append(builder, FROM_TEN_TO_NINETEEN[units]);
        } else {
            append(builder, TENS[tens]);
            append(builder, feminine ? UNITS_FEMALE[units] : UNITS_MALE[units]);
        }

        if (!one.isEmpty()) {
            append(builder, plural(triplet, one, few, many));
        }
    }

    private static void append(StringBuilder builder, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(part);
    }

    private static String plural(long value, String one, String few, String many) {
        long mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        return switch ((int) (value % 10)) {
            case 1 -> one;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }
}
