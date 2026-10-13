public class CharMatcherBenchmark4 {
    public static void main(String[] args) {
        System.out.println(isRegexPattern("c++"));
        System.out.println(isRegexPattern(".*bad.*"));
        System.out.println(isRegexPattern("spam"));
    }
    private static boolean isRegexPattern(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '+' || c == '^' || c == '$' || c == '[' || c == '(' || c == '\\' || c == '|' || c == '{') return true;
        }
        return false;
    }
}
