public class RegexPatternMatchBenchmark {
    public static void main(String[] args) {
        String[] words = {"hello", "testing", "StStStSt", "ChChCheck", "aaaab", "normalword", "good"};
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            for (String w : words) {
                isRegexPattern(w);
            }
        }
        long end = System.nanoTime();
        System.out.println("Time: " + (end - start) / 1000000.0 + " ms");
    }
    private static boolean isRegexPattern(String pattern) {
        return pattern.contains(".*")
            || pattern.contains(".+")
            || pattern.startsWith("^")
            || pattern.endsWith("$")
            || pattern.contains("[")
            || pattern.contains("(")
            || pattern.contains("\\")
            || pattern.contains("|")
            || pattern.contains("{");
    }
}
