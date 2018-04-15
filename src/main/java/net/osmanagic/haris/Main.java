package net.osmanagic.haris;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;

public class Main {
    static final String START_CMD = "start";
    static final String START_PREFIX = "from:";

    static final String STOP_CMD = "stop";
    static final String STOP_PREFIX = "to:";

    static final String SHOW_CMD = "show";

    static final String SHOW_WEEK_CMD = "show-week";

    static final String TODAY = "today";
    static final String YESTERDAY = "yesterday";

    static final String FILENAME = System.getProperty("user.home") + "/timetracker.txt";
    static final File file = new File(FILENAME);

    static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DateTimeFormatter DF = DateTimeFormatter.BASIC_ISO_DATE;

    static final TemporalField weekOfYear = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear();

    public static void main(String[] args) throws IOException {
        if (args.length != 1 && args.length != 2) {
            printHelp();
            System.exit(0);
        }

        ensureFileExists();

        switch (args[0]) {
            case START_CMD:
                start();
                break;
            case STOP_CMD:
                stop();
                show(null);
                break;
            case SHOW_CMD:
                final String dateString = args.length == 2 ? args[1] : null;
                show(dateString);
                break;
            case SHOW_WEEK_CMD:
                showWeek();
                break;
            default:
                System.out.println("Unknown command: " + args[0]);
        }
    }

    private static void stop() throws IOException {
        String previousWorkEnd = currentWorkEnd();
        if (previousWorkEnd != null) {
            System.out.println("Stop has been already called at " + previousWorkEnd);
        } else {
            write(STOP_PREFIX, now());
        }
    }

    private static void start() throws IOException {
        String workInProgressStart = currentWorkStart();
        if (workInProgressStart != null) {
            System.out.println("Start has been already called at " + workInProgressStart);
        } else {
            write(START_PREFIX, now());
        }
    }

    private static void write(String prefix, LocalDateTime dateTime) throws IOException {
        Files.write(
            file.toPath(),
            (prefix + toString(dateTime) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.APPEND
        );
    }

    private static void ensureFileExists() throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    private static String currentWorkEnd() throws IOException {
        List<String> lines = readAllLines();

        if (lines.isEmpty())
            return null;

        String last = lines.get(lines.size() - 1);
        return last.startsWith(STOP_PREFIX) ? last : null;
    }

    private static String currentWorkStart() throws IOException {
        List<String> lines = readAllLines();

        if (lines.isEmpty())
            return null;

        String last = lines.get(lines.size() - 1);
        return last.startsWith(START_PREFIX) ? last : null;
    }

    private static List<String> readAllLines() throws IOException {
        return Files.readAllLines(file.toPath()).stream()
            .filter(Objects::nonNull)
            .filter(line -> !"".equals(line.trim()))
            .collect(Collectors.toList());
    }

    private static void showWeek() throws IOException {
        int currentWeekNumber = getWeekOfYear(now());

        Duration duration = getTotal(_date -> getWeekOfYear(_date) == currentWeekNumber);
        print(duration);
    }

    private static int getWeekOfYear(LocalDateTime date) {
        return date.get(weekOfYear);
    }

    private static void show(String dateString) throws IOException {
        LocalDate date = parseDate(dateString != null ? dateString : TODAY);
        Duration duration = getTotal(_date -> _date.toLocalDate().equals(date));
        print(duration);
    }

    private static void print(Duration duration) {
        System.out.println(duration.toHours() + " hours " + duration.toMinutes() % 60 + " minutes");
    }

    private static Duration getTotal(Predicate<LocalDateTime> dateFilter) throws IOException {
        List<String> timeStrings = readAllLines();
        if (timeStrings.isEmpty())
            return Duration.ZERO;

        if (timeStrings.get(0).startsWith(STOP_PREFIX))
            throw new IllegalStateException("Time tracker file starts with a to: line.");

        if (timeStrings.size() % 2 == 1)
            timeStrings.add(toString(now()));

        Duration total = Duration.ZERO;
        LocalDateTime currentStart = null, currentEnd = null;
        for (String timeStringWithPrefix : timeStrings) {
            if (timeStringWithPrefix.startsWith(START_PREFIX)) {
                String timeString = timeStringWithPrefix.replaceFirst(START_PREFIX, "");
                if (!dateFilter.test(fromString(timeString))) {
                    continue;
                }
                currentStart = fromString(timeString);
            }

            if (timeStringWithPrefix.startsWith(STOP_PREFIX)) {
                currentEnd = fromString(timeStringWithPrefix.replaceFirst(STOP_PREFIX, ""));
                if (!dateFilter.test(currentEnd)) {
                    continue;
                }
                total = total.plus(Duration.between(currentStart, currentEnd));
            }
        }
        if (currentStart != null) {
            if (currentEnd == null || currentEnd.isBefore(currentStart)) {
                total = total.plus(Duration.between(currentStart, now()));
            }
        }
        return total;
    }

    private static LocalDate parseDate(String dateString) {
        LocalDate date;
        if (TODAY.equals(dateString)) {
            date = LocalDate.now();
        } else if (YESTERDAY.equals(dateString)) {
            date = LocalDate.now().minusDays(1);
        } else if (isDayOrdinal(dateString)) {
            date = LocalDate.now().with(DayOfWeek.of(intValue(dateString)));
        } else {
            date = LocalDate.parse(dateString, DF);
        }
        return date;
    }

    private static boolean isDayOrdinal(String dateString) {
        int ordinal = intValue(dateString);
        return 1 <= ordinal && ordinal <= 7;
    }

    private static int intValue(String dateString) {
        try {
            return Integer.valueOf(dateString);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static LocalDateTime fromString(String timeString) {
        return LocalDateTime.parse(timeString, DTF);
    }

    private static String toString(LocalDateTime time) {
        return time.format(DTF);
    }

    private static void printHelp() {
        System.out.println(
            "Available commands:\n" +
                "start, stop, show <date>.\n" +
                "<date> can be 'today', 'yesterday', number of day in week (e.g. show 2 for Tuesday) or a date in following format: 20161231"
        );
    }
}
