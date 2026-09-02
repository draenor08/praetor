import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String s = in.readLine().trim();
        int count = 0;
        for (char c : s.toCharArray()) {
            if ("aeiou".indexOf(c) >= 0) count++;
        }
        System.out.println(count);
    }
}
