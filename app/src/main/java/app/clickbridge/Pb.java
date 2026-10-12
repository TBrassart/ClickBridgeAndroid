package app.clickbridge;

import java.util.HashMap;
import java.util.Map;

/** Mini décodeur protobuf : ne garde que les champs entiers (varint). */
public final class Pb {
    private Pb() {}

    public static Map<Integer, Long> parseVarints(byte[] buf, int start) {
        Map<Integer, Long> out = new HashMap<>();
        int[] pos = {start};
        try {
            while (pos[0] < buf.length) {
                long tag = varint(buf, pos);
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (wire == 0) {
                    out.put(field, varint(buf, pos));
                } else if (wire == 2) {
                    long n = varint(buf, pos);
                    pos[0] += (int) n;
                } else if (wire == 5) {
                    pos[0] += 4;
                } else if (wire == 1) {
                    pos[0] += 8;
                } else {
                    break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // trame tronquée : on garde ce qui a été lu
        }
        return out;
    }

    private static long varint(byte[] buf, int[] pos) {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = buf[pos[0]++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
    }
}
