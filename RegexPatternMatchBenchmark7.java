import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class RegexPatternMatchBenchmark7 {
    public static void main(String[] args) {
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            Pattern.compile("([^aeiouyAEIOUY\\d\\s_]{2,})\\1+", Pattern.CASE_INSENSITIVE);
            Pattern.compile("(.)\\1{4,}|(.{2,8}?)\\2{3,}", Pattern.CASE_INSENSITIVE);
        }
        long end = System.nanoTime();
        System.out.println("Time: " + (end - start) / 1000000.0 + " ms");
    }
}
