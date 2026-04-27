public class test_emoji {
    public static void main(String[] args) {
        String smiley = "\uD83D\uDE00";
        for (int i = 0; i < smiley.length(); i++) {
            int codeUnit = (int)smiley.charAt(i);
            System.out.println(String.format("%06x", codeUnit));
        }
    }
}
