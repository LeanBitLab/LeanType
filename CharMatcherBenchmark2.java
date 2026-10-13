import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class CharMatcherBenchmark2 {
    public static void main(String[] args) {
        String text = "Hello world! This is a test. Can we make it faster?";
        Pattern NON_LETTER_REGEX = Pattern.compile("[^\\p{L}]+");

        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            String[] words = NON_LETTER_REGEX.split(text.toLowerCase());
        }
        long end = System.nanoTime();
        System.out.println("Regex split Time: " + (end - start) / 1000000.0 + " ms");

        start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            StringBuilder currentWord = new StringBuilder();
            for (int j = 0; j < text.length(); j++) {
                char c = text.charAt(j);
                if (Character.isLetter(c)) {
                    currentWord.append(Character.toLowerCase(c));
                } else if (currentWord.length() > 0) {
                    String word = currentWord.toString();
                    currentWord.setLength(0);
                }
            }
            if (currentWord.length() > 0) {
                String word = currentWord.toString();
            }
        }
        end = System.nanoTime();
        System.out.println("Loop StringBuilder Time: " + (end - start) / 1000000.0 + " ms");
    }
}
