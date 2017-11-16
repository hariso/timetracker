package net.osmanagic.haris;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;

public class Main {
    static final String START_CMD = "start";
    static final String START_PREFIX = "from:";

    static final String STOP_CMD = "stop";
    static final String STOP_PREFIX = "to:";

    static final String SHOW_CMD = "show";
    static final String TODAY = "today";
    static final String YESTERDAY = "yesterday";

    static final String FILENAME = System.getProperty("user.home") + "/timetracker.txt";
    static final File file = new File(FILENAME);

    static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DateTimeFormatter DF = DateTimeFormatter.BASIC_ISO_DATE;

    public static void main(String[] args) throws IOException {
        if (args.length != 1 && args.length != 2) {
            printHelp();
            System.exit(0);
        }

        ensureFileExists();

        if (START_CMD.equals(args[0])) {
            start();
        }

        if (STOP_CMD.equals(args[0])) {
            stop();
        }

        if (SHOW_CMD.equals(args[0])) {
            final String dateString = args.length == 2 ? args[1] :  null;
            show(dateString);
        }
    }

    private static void show(String dateString) throws IOException {
        Duration duration = getTotal(dateString != null ? dateString : TODAY);
        System.out.println(duration.toHours() % 24 + " hours " + duration.toMinutes() % 60 + " minutes");
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

    private static Duration getTotal(String dateString) throws IOException {
        LocalDate date = parseDate(dateString);

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
                if (!fromString(timeString).toLocalDate().equals(date)) {
                    continue;
                }
                currentStart = fromString(timeString);
            }

            if (timeStringWithPrefix.startsWith(STOP_PREFIX)) {
                currentEnd = fromString(timeStringWithPrefix.replaceFirst(STOP_PREFIX, ""));
                if (!currentEnd.toLocalDate().equals(date)) {
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
        } else {
            date = LocalDate.parse(dateString, DF);
        }
        return date;
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
            "<date> can be 'today', 'yesterday' or a date in following format: 20161231"
        );
    }
}
