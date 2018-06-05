import java.io.*;

/**
 * Created by xdc on 15-12-23.
 */
//三进制编码
public class Ternary {
    public static void main(String[] args) {
        StringBuilder binary2ternary = new StringBuilder(100 * 1024);
        StringBuilder binary2ternary_bits_len = new StringBuilder(100 * 1024);
        try {
            FileOutputStream fout = new FileOutputStream("/home/xdc/binary2ternary");
            BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(fout));
            BufferedWriter bout2 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/home/xdc/binary2ternary_bits_len")));
            binary2ternary.append("public static int[] binary2ternary = {");
            binary2ternary_bits_len.append("public static byte[] binary2ternary_bits_len = {");
            int num = 1<<17;
            bout.write(String.valueOf(num));
            bout.newLine();
            bout2.write(String.valueOf(num));
            bout2.newLine();
            for (int i = 0; i < num; i++) {
                int left = i, n = 0;
                int result = 0;
                do {
                    int tmp = left % 3;
                    result |= tmp << n;
                    left /= 3;
                    n += 2;
                } while (left > 0);
                if ((i % 16) == 0) {
                    binary2ternary.append("\n");
                    binary2ternary_bits_len.append("\n");
                }
                binary2ternary.append(result).append(",");
                binary2ternary_bits_len.append(n).append(",");
                bout.write(String.valueOf(result));
                bout.newLine();
                bout2.write(String.valueOf(n));
                bout2.newLine();
            }
            bout.close();
            bout2.close();
            binary2ternary.setCharAt(binary2ternary.length() - 1, '}');
            binary2ternary.append(";");
            binary2ternary_bits_len.setCharAt(binary2ternary_bits_len.length() - 1, '}');
            binary2ternary_bits_len.append(";");
            System.out.println(binary2ternary);
            System.out.println(binary2ternary_bits_len);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
