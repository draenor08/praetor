import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        long r = Long.parseLong(in.readLine().trim());
        System.out.printf("%.8f%n", Math.PI * r * r);
    }
}
