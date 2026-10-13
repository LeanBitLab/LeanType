public class CharMatcherBenchmark5 {
    public static void main(String[] args) {
        System.out.println(isRegexPatternOld("c++"));
        System.out.println(isRegexPatternOld(".*bad.*"));
        System.out.println(isRegexPatternOld("spam"));
    }
    private static boolean isRegexPatternOld(String pattern) {
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
