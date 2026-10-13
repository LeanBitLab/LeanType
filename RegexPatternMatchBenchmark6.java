import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class RegexPatternMatchBenchmark6 {
    public static void main(String[] args) {
        String patternStr = "test_pattern.*";
        long start = System.nanoTime();
        for(int i=0; i<100000; i++) {
            Pattern.compile(patternStr);
        }
        long end = System.nanoTime();
        System.out.println("Pattern compile time: " + (end - start) / 1000000.0 + " ms");
    }
}
