import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String s = in.readLine().trim();
        String r = new StringBuilder(s).reverse().toString();
        System.out.println(s.equals(r) ? "YES" : "NO");
    }
}
