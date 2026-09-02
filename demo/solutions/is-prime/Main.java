import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        long n = Long.parseLong(in.readLine().trim());
        boolean prime = n >= 2;
        for (long d = 2; d * d <= n; d++) {
            if (n % d == 0) { prime = false; break; }
        }
        System.out.println(prime ? "YES" : "NO");
    }
}
