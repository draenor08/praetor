import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(in.readLine().trim());
        int coins = 0;
        for (int c : new int[] {25, 10, 5, 1}) {
            coins += n / c;
            n %= c;
        }
        System.out.println(coins);
    }
}
