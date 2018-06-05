import java.io.*;
import java.util.Random;

/**
 * Created by xdc on 15-12-22.
 */
public class Array_compress {
    public static int limit_num;
    public static int[] binary2ternary;
    public static byte[] binary2ternary_bits_len;

    static {
        try {
            BufferedReader breader = new BufferedReader(new InputStreamReader(
                    Array_compress.class.getResourceAsStream("binary2ternary")));
            String line;
            if ((line = breader.readLine()) != null) {
                int num = Integer.parseInt(line);
                limit_num = num - 1;
                binary2ternary = new int[num];
                int i = 0;
                while ((line = breader.readLine()) != null) {
                    binary2ternary[i++] = Integer.parseInt(line);
                }
            }
            breader.close();
            breader = new BufferedReader(new InputStreamReader(
                    Array_compress.class.getResourceAsStream("binary2ternary_bits_len")));
            if ((line = breader.readLine()) != null) {
                int num = Integer.parseInt(line);
                binary2ternary_bits_len = new byte[num];
                int i = 0;
                while ((line = breader.readLine()) != null) {
                    binary2ternary_bits_len[i++] = (byte)Integer.parseInt(line);
                }
            }
            breader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int limit_num(){
        return limit_num;
    }

    public static long[] compress_8bits(long[] data, int bit_len) {
        int len = data.length / 8;

        if ((data.length & 0x1f) != 0) {
            len++;
        }
        long mask = (1L << bit_len) - 1;
        long[] output = new long[len];
        for (int i = 0, j = 0; i < data.length; j++) {
            for (int k = 0; k < 64 && i < data.length; i++) {
                for (int n = 0; n < 64; n += bit_len, k++) {
                    if ((data[i] & (mask << n)) != 0) {
                        output[j] |= 1L << k;
                    }
                }
            }
        }
        return output;
    }

    public static void compress_ranges(int[] data, long[] output) {
        int index = 0;
        int bit_index = 0;

        for (int i = 0; i < data.length && data[i] > 0; i++) {
            long tmp = binary2ternary[data[i] - 1];
            int bit_len = binary2ternary_bits_len[data[i] - 1];
            tmp |= 0x3 << bit_len;
            bit_len += 2;
            do {
                if (bit_index + bit_len <= 64) {
                    output[index] |= tmp << bit_index;
                    bit_index += bit_len;
                    break;
                } else if (64 - bit_index > 0) {//java会对移位数模64
                    output[index] |= tmp << bit_index;
                    tmp >>>= 64 - bit_index;
                    bit_len -= 64 - bit_index;
                    index++;
                    bit_index = 0;
                } else {
                    index++;
                    bit_index = 0;
                }
            } while (true);
        }
    }

    //返回值为增加的位数，如果小于0说明内存不足，取负后为需要增加的位数
    public static int compress_range( int data, long[] output, int from ){
        //System.out.println(from);
        long tmp = binary2ternary[data - 1];
        int bit_len = binary2ternary_bits_len[data - 1];
        tmp |= 0x3 << bit_len;
        bit_len += 2;
        int save_bit_len = bit_len;

        if( bit_len + from > output.length * 64 ){
            return -save_bit_len;
        }else{
            int index = from / 64;
            int bit_index = from & 0x3f;
            do{
                if (bit_index + bit_len <= 64) {
                    output[index] |= tmp << bit_index;
                    break;
                } else {
                    output[index] |= tmp << bit_index;
                    tmp >>>= 64 - bit_index;
                    bit_len -= 64 - bit_index;
                    index++;
                    bit_index = 0;
                }
            }while (true);
            return save_bit_len;
        }
    }

    public static void uncompress_ranges(long[] data, short[] output) {
        int out_put_index = 0;
        int cur_len = 0;
        int cur_base = 1;

        for (int i = 0; i < data.length && data[i] != 0; i++) {
            for (int k = 0; k < 64; k += 2) {
                int tmp = (int) ((data[i] >>> k) & 0x3);
                if (tmp != 0x3) {
                    cur_len += cur_base * tmp;
                    cur_base *= 3;
                } else {
                    output[out_put_index++] = (short) (cur_len + 1);
                    cur_len = 0;
                    cur_base = 1;
                }
            }
        }
    }

    public static void range_to_bits(int[] data, int start_bit, long[] output) {
        int index = 0;
        int bit_index = 0;
        long cur_bit = start_bit;

        for (int i = 0; i < data.length && data[i] > 0; i++) {
            for (int k = 0; k < data[i]; k++) {
                if (bit_index == 64) {
                    index++;
                    bit_index = 0;
                }
                output[index] |= cur_bit << bit_index;
                bit_index++;
            }
            cur_bit ^= 1;
        }
    }

    public static void bits_to_range(long[] data, int[] output) {
        int cur_len = 0;
        int cur_bit = (int) (data[0] & 0x1);
        int index = 0;

        for (long aData : data) {
            for (int k = 0; k < 64; k++) {
                if (cur_bit == ((aData >>> k) & 0x1)) {
                    cur_len++;
                } else {
                    output[index++] = (short) cur_len;
                    cur_len = 1;
                    cur_bit ^= 1;
                }
            }
        }
        if (cur_len != 0 && cur_bit != 0) {
            output[index] = (short) cur_len;
        }
    }

    public static int get_ids_reversed(long[] data, long from, long to, long[] output, int output_index) {
        int index = 0;

        for (long aData : data) {
            for (int k = 0; k < 64; k++) {
                if (1 == ((aData >>> k) & 0x1)
                        && index > to && index < from
                        && output_index < output.length) {
                    output[output_index++] = index;
                }
                index++;
                if (index >= from) {
                    return -(output_index + 1);
                } else if (output_index == output.length) {
                    return output_index;
                }
            }
        }
        return output_index;
    }

    public static int get_ids_reversed_drop(long[] data, long from, long to, long[] output, int output_index){
        int index = 0;

        for (long aData : data) {
            for (int k = 0; k < 64; k++) {
                if (1 == ((aData >>> k) & 0x1) ){
                    for( int i = 0; i < 8; i++ ) {
                        if ( index + i> to && index + i< from
                                && output_index < output.length) {
                            output[output_index++] = index+i;
                        }
                    }
                }
                index+=8;
                if(  index >= from ){
                        return -(output_index + 1);
                }else if( output_index == output.length ){
                    return output_index;
                }
            }
        }
        return output_index;
    }

    public static int get_ids( long[] data, long from, long to, long[] output, int output_index ){
        int index = data.length * 64 - 1;

        for( int i = data.length - 1; i >= 0; i-- ){
            long aData = data[i];
            for (int k = 63; k >= 0; k--) {
                if (1 == ((aData >>> k) & 0x1)
                        && index > to && index < from
                        && output_index < output.length) {
                    output[output_index++] = index;
                }
                index--;
                if( index <= to ){
                        return -(output_index + 1);
                }else if( output_index == output.length ) {
                    return output_index;
                }
            }
        }
        return output_index;
    }

    public static int get_ids_drop( long[] data, long from, long to, long[] output, int output_index ){
        int index = data.length * 64 *8 - 1;

        for( int i = data.length - 1; i >= 0; i-- ){
            long aData = data[i];
            for (int k = 63; k >= 0; k--) {
                if (1 == ((aData >>> k) & 0x1) ){
                    for( int h = 0; h < 8; h++ ) {
                        if (index - h> to && index -h < from
                                && output_index < output.length) {
                            output[output_index++] = index-h;
                        }
                    }
                }
                index-= 8;
                if(  index <= to ){
                        return -(output_index + 1);
                }else if( output_index == output.length ){
                    return output_index;
                }
            }
        }
        return output_index;
    }

    public static void main(String[] args) {
        Random random = new Random();

        /*
        for (int k = 0; k < 1024 * 100; k++) {
            long bits[] = new long[k / 100 + 1];
            short[] range = new short[bits.length * 64];
            long[] compress_range = new long[range.length];
            short[] uncompress_range = new short[range.length];
            long[] uncompress_bits = new long[bits.length];

            for (int i = 0; i < bits.length; i++) {
                bits[i] = random.nextLong();
            }

            // bits[0] = 0x1bL;
            bits_to_range(bits, range);
            compress_ranges(range, compress_range);
            uncompress_ranges(compress_range, uncompress_range);
            range_to_bits(uncompress_range, (int) (bits[0] & 0x1), uncompress_bits);

            for (int i = 0; i < bits.length; i++) {
                if (bits[i] != uncompress_bits[i]) {
                    System.out.println(String.format("%x:%x", bits[i], uncompress_bits[i]));
                    return;
                }
            }
        }
        */
        for (int k = 100000; k < 1280 * 100; k++) {
            long bits[] = new long[k / 100 + 1];
            int[] range = new int[bits.length * 64];
            long[] compress_range = new long[range.length];
            int[] uncompress_range = new int[range.length];
            long[] uncompress_bits = new long[bits.length];
            long[] ids = new long[bits.length * 64 + 1];
            long[] compare_ids = new long[bits.length * 64 + 1];
            int out_index;
            int compare_index;

            for (int i = 0; i < bits.length; i++) {
                bits[i] = random.nextLong();
            }

            //bits[0] = 0x11111111L;;
            bits[bits.length - 1] |= 1L << 63;
            int index = 0;
            Message_compress status_cache = new Message_compress(0);
            bits[0] |= 0x1;
            bits[0] ^= 0x1;
            for (int i = 0; i < bits.length; i++) {
                for (int j = 0; j < 64; j++, index++) {
                    if ((bits[i] & (1L << j)) != 0) {
                        status_cache.add(index);
                    }
                }
            }
            //          status_cache.build();
        /*    status_cache = new Status_cache(status_cache.from_id(), status_cache.next_index()
                        , (short) status_cache.num(), status_cache.compres_bit_len(), status_cache.to_bytes());
*/
            bits[0] |= 0x1;

            long from = random.nextInt(ids.length + 1) - 1;
            long to = random.nextInt(ids.length + 1) - 1;
            if (status_cache.is_in(to, from) != 0) {
                out_index = -1;
            } else {
                out_index = status_cache.get_ids(from, to, false, ids, 0);
            }
            if (!status_cache.is_drop()) {
                compare_index = get_ids(bits, from, to, compare_ids, 0);
            } else {
                long[] _8bits = compress_8bits(bits, status_cache.compres_bit_len());
                compare_index = get_ids_drop(_8bits, from, to, compare_ids, 0);
            }
            if (out_index != compare_index) {
                return;
            } else {
                if (out_index < 0) {
                    out_index = -out_index;
                    out_index--;
                }
                for (int i = 0; i < out_index; i++) {
                    if (ids[i] != compare_ids[i]) {
                        return;
                    }
                }
            }

            /*
            byte[] bytes1 = status_cache.to_bytes();
            byte[] bytes2 = new Status_cache(status_cache.from_id(),status_cache.next_index()
                    ,(short)status_cache.num(),bytes1).to_bytes();
            if( !Arrays.equals(bytes1,bytes2)  ){
                return;
            }
            */
            /*
            if( status_cache.is_drop() ){
                long[] _8bits = compress_8bits(bits,status_cache.compres_bit_len());
                bits_to_range(_8bits, range);
                compress_ranges(range, compress_range);
            }else{
                bits_to_range(bits, range);
                compress_ranges(range, compress_range);
            }
            //uncompress_ranges(compress_range, uncompress_range);
            //range_to_bits(uncompress_range, (int) (bits[0] & 0x1), uncompress_bits);

            for( int i = 0; i < compress_range.length || i < status_cache.data().length; i++ ){
                if( i < compress_range.length && i < status_cache.data().length ) {
                    if (compress_range[i] != status_cache.data()[i]) {
                        System.out.println(String.format("%x:%x", compress_range[i], status_cache.data()[i]));
                        return;
                    }
                }else if(  i < compress_range.length ){
                    if( compress_range[i] != 0 ){
                        System.out.println(String.format("%x", compress_range[i]));
                        return;
                    }
                }else{
                    if( compress_range[i] != 0 ){
                        System.out.println(String.format("%x", status_cache.data()[i]));
                        return;
                    }
                }
            }
            */
            /*
           for (int i = 0; i < bits.length; i++) {
                if (bits[i] != uncompress_bits[i]) {
                    System.out.println(String.format("%x:%x", bits[i], uncompress_bits[i]));
                    return;
                }
            }
            */
        }
        System.out.println("success");
    }
}
