import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by xdc on 16-1-19.
 */
/*
    ......|<-last_blank->|<-last_range->|
    |                                   |
    v                                   v
from_id                          from_id+next_index
*/
public class Message_compress {
    public static final int MAX_COMPRESS_BITS = 1024*8;//每块最多存储bit数

    private long from_id;//起始id
    private int num;//id数量
    private int next_index;//下一个连续id=from_id+next_index
    private int last_blank;//当前未编码的间隔大小
    private int last_range;//当前未编码的连续id数量
    private short compress_index;//compress_data的下一个bit的index
    private long[] compress_data;//编码数据
    private byte compress_bit_len;//连续的compress_bit_len压缩成一位
    private boolean is_build;//是否全部打包好

    public Message_compress( long from_id ){
        this.from_id = from_id;
        compress_bit_len = 0;
        is_build = false;
        num = next_index = 1;
        last_blank = 0;
        last_range = 1;
        compress_index = 0;
        compress_data = new long[MAX_COMPRESS_BITS / 64 + 1];
    }

    //input为小端
    public Message_compress(long from_id, int next_index, int num, byte compress_bit_len, byte[] input) {
        this.from_id = from_id;
        this.next_index = next_index;
        this.num = num;
        this.compress_bit_len = compress_bit_len;
        this.compress_data = new long[(input.length >>> 3) + ((input.length & 0x7) != 0 ? 1 : 0)];
        int k = 0;
        int i;
        for (i = 0; i + 8 <= input.length; ) {
            compress_data[k] |= input[i++] & 0xffL;
            compress_data[k] |= (input[i++] & 0xffL) << 8;
            compress_data[k] |= (input[i++] & 0xffL) << 16;
            compress_data[k] |= (input[i++] & 0xffL) << 24;
            compress_data[k] |= (input[i++] & 0xffL) << 32;
            compress_data[k] |= (input[i++] & 0xffL) << 40;
            compress_data[k] |= (input[i++] & 0xffL) << 48;
            compress_data[k] |= (input[i++] & 0xffL) << 56;
            k++;
        }
        for (int h = 0; i < input.length; h += 8) {
            compress_data[k] |= (input[i++] & 0xffL) << h;
        }
        compress_index = (short) (input.length * 8);
        is_build = true;
    }

    public Message_compress(long from_id, long flag, byte[] input) {
        this(from_id, (int) (flag & 0xfffffL)
                , (int) ((flag >>> 20) & 0xfffffL), (byte) ((flag >>> 40) & 0xff), input);
    }

    //|0-19|next_index |20-39| num |40-47| is_drop
    public long to_flag() {
        return (next_index & 0xfffffL) | ((num & 0xfffffL) << 20) | ((compress_bit_len & 0xffL) << 40);
    }

    public static long parse_next_index(long flag) {
        return flag & 0xfffffL;
    }

    //>0 比这里的都大 =0 包含其中 <0 比这里的都小
    public int is_in(long to, long from) {
        if (to >= (from_id + next_index - 1)) {
            return 1;
        } else if (from <= from_id) {
            return -1;
        } else {
            return 0;
        }
    }

    public boolean is_outbound(long id) {
        return id >= from_id + Array_compress.limit_num();
    }

    public int num(){
        return num;
    }

    public boolean is_build(){
        return is_build;
    }

    public boolean is_drop(){
        return compress_bit_len > 0;
    }

    public byte compres_bit_len() {
        return compress_bit_len;
    }

    public long[] data(){
        return compress_data;
    }

    public long from_id() {
        return from_id;
    }

    public int next_index() {
        return next_index;
    }

    public int bit_index() {
        return compress_index;
    }


    public int add(long id) {
        if (id < from_id + next_index) {
            return 0;
        } else if (is_outbound(id)
                || compress_index >= MAX_COMPRESS_BITS) {
            return -1;
        }else if( !is_build ){
            int new_blank = (int) (id - from_id - next_index);
            while (!is_drop()) {
                if (new_blank == 0) {
                    last_range++;
                    next_index++;
                    num++;
                } else {
                    compress_range();
                    last_blank = new_blank;
                    last_range = 1;
                    next_index = (int) (id - from_id + 1);
                    num++;
                }
                return 1;
            }
            final int base = compress_bit_len & 0xff;
            final int mask = base - 1;
            int _tmp = (int) (id - from_id);
            int _begin = next_index / base + ((next_index & mask) != 0 ? 1 : 0);
            int _end = _tmp / base;
            _tmp++;
            if (_end > _begin ) {
                compress_range();
                last_range = 1;
                last_blank = (_end - _begin);
            } else {
                _end = _tmp / base + ((_tmp & mask) != 0 ? 1 : 0);
                last_range += (_end - _begin);
            }
            next_index = _tmp;
            num++;
            return 1;
        }else{
            return 0;
        }
    }

    private void compress_range(int range) {
        int tmp = Array_compress.compress_range(range,compress_data,compress_index);
        if( tmp > 0 ){
            compress_index += tmp;
        }else{
            compress_data = Arrays.copyOf(compress_data, compress_data.length + 1);
            compress_index += Array_compress.compress_range(range,compress_data,compress_index);
        }
    }

    private void compress_range(){
        if (last_blank > 0) {
            compress_range(last_blank);
        }
        compress_range(last_range);
        last_blank = last_range = 0;
    }

    public void build() {
        if (!is_build) {
            compress_range();
            is_build = true;
        }
    }

    private void compressed_ranges_2_8bits(){
        int output_bit_index = 0;
        int cur_len = 0;
        int cur_base = 1;
        int range = 0;
        int blank = 0;
        int cur_bit = 1;
        long[] input = compress_data;

        //compress_data = new long[4];
        compress_index = 0;
        final int base = compress_bit_len & 0xff;
        final int mask = base - 1;
        for (int i = 0; i < input.length && input[i] != 0; i++) {
            for (int k = 0; k < 64; k += 2) {
                int tmp = (int) ((input[i] >>> k) & 0x3);
                input[i] ^= ((long) tmp) << k;
                if (tmp != 0x3) {
                    cur_len += cur_base * tmp;
                    cur_base *= 3;
                } else {
                    cur_len++;
                    int _tmp = output_bit_index + cur_len;
                    int _begin = output_bit_index / base + ((output_bit_index & mask) != 0 ? 1 : 0);
                    int _end = _tmp / base;
                    if( cur_bit == 1
                            || _end <= _begin ){
                        _end += (_tmp & mask) != 0 ? 1 : 0;
                        range += _end - _begin;
                    }else if( _end > _begin ){
                        if( blank > 0 ){
                            compress_range(blank);
                        }
                        compress_range(range);
                        blank = _end - _begin;
                        range = (_tmp & mask) != 0 ? 1 : 0;
                    }
                    output_bit_index += cur_len;
                    cur_len = 0;
                    cur_base = 1;
                    cur_bit ^= 1;
                }
            }
        }
        last_range = range;
        last_blank = blank;
    }

    //>=0 代表接下来的output_index且还可继续读，<0 代表已经读完，取负后减一为接下来的output_index
    public int get_ids(long from, long to, boolean is_reversed, long[] output, int output_index) {
        int cur_len = 0;
        long cur_id;
        int cur_bit;
        final long limit = from_id + next_index;

        final int base = compress_bit_len & 0xff;
        final int mask = base - 1;
        if( is_reversed ){
            int cur_base = 1;
            cur_bit = 1;
            cur_id = from_id;
            for (int i = 0; i < compress_data.length && compress_data[i] != 0; i++) {
                for (int k = 0; k < 64; k += 2) {
                    int tmp = (int) ((compress_data[i] >>> k) & 0x3);
                    if (tmp != 0x3) {
                        cur_len += cur_base * tmp;
                        cur_base *= 3;
                    } else {
                        cur_len++;
                        if (is_drop()) {
                            cur_len *= base;
                        }
                        if( cur_bit == 1 ){
                            if( cur_id + cur_len > to + 1 ){
                                long begin = Long.max(to,cur_id-1);
                                long end = Long.min(from,cur_id+cur_len);
                                end = Long.min(end, begin + output.length - output_index + 1);
                                end = Long.min(end,limit);
                                for( long h = begin + 1; h < end; h++ ){
                                    output[output_index++] = h;
                                }
                                if( end == from ){
                                    return -(output_index + 1);
                                }else if( output_index == output.length ){
                                    return output_index;
                                }
                            }
                        }
                        cur_id += cur_len;
                        cur_bit ^= 1;
                        cur_len = 0;
                        cur_base = 1;
                    }
                }
            }
            cur_len = last_blank;
            if (is_drop()) {
                cur_len *= base;
            }
            cur_id += cur_len;
            cur_len = last_range;
            if (is_drop()) {
                cur_len *= base;
            }
            if( cur_len > 0 && cur_id + cur_len > to + 1 ){
                long begin = Long.max(to,cur_id-1);
                long end = Long.min(from,cur_id+cur_len);
                end = Long.min(end,begin+output.length-output_index+1);
                end = Long.min(end,limit);
                for( long h = begin + 1; h < end; h++ ){
                    output[output_index++] = h;
                }
                if( end == from ){
                    return -(output_index + 1);
                }else if( output_index == output.length ){
                    return output_index;
                }
            }
        }else{
            cur_bit = 0;
            cur_id = limit;
            cur_len = last_range;
            if (is_drop()) {
                if ((next_index & mask) != 0) {
                    cur_id = from_id + (next_index & (~mask)) + base;
                }
                cur_len *= base;
            }
            if( cur_len > 0 && cur_id - cur_len < from ){
                long end = Long.min(from,cur_id);
                end = Long.min(end,limit);
                long begin = Long.max(to,cur_id-cur_len-1);
                begin = Long.max(begin,end - ( output.length - output_index)-1);
                for( long h = end - 1; h > begin && output_index < output.length; h-- ){
                    output[output_index++] = h;
                }
                if( begin == to ){
                    return -(output_index + 1);
                }else if( output_index == output.length  ){
                    return output_index;
                }
            }
            cur_id -= cur_len;
            cur_len = last_blank - 1;//补偿
            if (is_drop()) {
                cur_len *= base;
            }
            cur_id -= cur_len;
            cur_len = 0;
            for( int i = compress_data.length - 1; i >= 0; i-- ){
                for( int k = 62; k >= 0; k -=2 ){
                    int tmp = (int) ((compress_data[i] >>> k) & 0x3);
                    if (tmp != 0x3) {
                        cur_len *= 3;
                        cur_len += tmp;
                    } else {
                        cur_len++;
                        if (is_drop()) {
                            cur_len *= base;
                        }
                        if( cur_bit == 1) {
                            if( cur_len > 0 && cur_id - cur_len < from ){
                                long end = Long.min(from, cur_id);
                                end = Long.min(end,limit);
                                long begin = Long.max(to, cur_id - cur_len - 1);
                                begin = Long.max(begin,end - ( output.length - output_index)-1);
                                for( long h = end - 1; h > begin && output_index < output.length; h-- ){
                                    output[output_index++] = h;
                                }
                                if( begin == to ){
                                    return -(output_index + 1);
                                }else if( output_index == output.length  ){
                                    return output_index;
                                }
                            }
                        }
                        cur_id -= cur_len;
                        cur_bit ^= 1;
                        cur_len = 0;
                    }
                }
            }
            if( cur_bit == 1 ){
                cur_len++;
                if (is_drop()) {
                    cur_len *= base;
                }
                if( cur_len > 0 && cur_id - cur_len < from ){
                    long end = Long.min(from,cur_id);
                    end = Long.min(end,limit);
                    long begin = Long.max(to,cur_id-cur_len-1);
                    begin = Long.max(begin,end - ( output.length - output_index)-1);
                    for( long h = end - 1; h > begin && output_index < output.length; h-- ){
                        output[output_index++] = h;
                    }
                    if( begin == to ){
                        return -(output_index + 1);
                    }else if( output_index == output.length  ){
                        return output_index;
                    }
                }
            }
        }
        return output_index;
    }

    //小端
    public byte[] to_bytes() {
        int len = (compress_index >> 3) + ((compress_index & 0x7) != 0 ? 1 : 0);
        byte[] output = new byte[len];
        int k = 0;
        int i;
        for (i = 0; i < compress_index / 64; i++) {
            output[k++] = (byte) (compress_data[i] & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 8) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 16) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 24) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 32) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 40) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 48) & 0xff);
            output[k++] = (byte) ((compress_data[i] >>> 56) & 0xff);
        }
        int n = compress_index & 0x3f;
        n = (n >> 3) + ((n & 0x7) != 0 ? 1 : 0);
        n <<= 3;
        for (int h = 0; h < n; h += 8) {
            output[k++] = (byte) ((compress_data[i] >>> h) & 0xff);
        }
        return output;
    }

}
