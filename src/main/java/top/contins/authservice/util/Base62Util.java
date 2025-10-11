package top.contins.authservice.util;

public class Base62Util {
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long id) {
        if (id == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARS.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }

    public static long decode(String str) {
        long result = 0;
        for (int i = 0; i < str.length(); i++) {
            result = result * 62 + CHARS.indexOf(str.charAt(i));
        }
        return result;
    }
}