import numpy as np
import struct


def float_to_bin(num):
    # Given an FP32 value, change it to a string of 1s and 0s.
    return bin(struct.unpack("!I", struct.pack("!f", num))[0])[2:].zfill(32)


class MXBFP:
    """
    Creates an MXBFP group.
    Takes mantissa bitwidth, exponent bitwidth as parameter,
    and a list of 16 FP values as input.
    (Which means, group size is fixed as 16. Subgroup size is fixed as 2.)
    """

    def __init__(self, vals, m_bitwidth, e_bitwidth=8):
        self.m_bitwidth = m_bitwidth
        self.e_bitwidth = e_bitwidth

        self.group_size = 16

        # Make the 16 FP32 values into bitstrings, for easier manipulation.
        myfunc_vec = np.vectorize(float_to_bin)
        vals = myfunc_vec(vals)

        # Add zero padding to vals.
        if len(vals) < self.group_size:
            remaining = np.repeat("0" * len(vals[0]), self.group_size - len(vals))
            vals = np.append(vals, remaining)

        # Find the global shared exponent.
        e_bits = list(map(lambda x: x[1 : 1 + self.e_bitwidth], vals))

        max_es = max(e_bits)

        # Now, determine the new mantissas and the MX bit.
        # Subgroup size is fixed as 2.
        new_mantissas = []

        # Iterate over 2 elements at a time.
        for val1, val2 in zip(vals[::2], vals[1::2]):
            e1 = val1[1 : 1 + self.e_bitwidth]
            e2 = val2[1 : 1 + self.e_bitwidth]

            max_e = max(e1, e2)

            # Is the max exponent of the subgroup equal to the global shared exponent?
            if max_e == max_es:
                mx = 0
            else:
                mx = 1

            # Shifting algorithm:
            # If mx = 0, shift by (shared_exp - my_exp) and add implicit 1 if necessary.
            # If mx = 1, shift by (shared_exp - my_exp - 1) and add implicit 1 if necessary.

            diff1 = int(max_es, 2) - int(e1, 2) - mx
            diff2 = int(max_es, 2) - int(e2, 2) - mx

            # Should there be implicit 1?
            if int(e1, 2) == 0:
                new_m1 = (val1[0] + "0" * diff1 + val1[1 + self.e_bitwidth :])[
                    : 1 + self.m_bitwidth
                ]
            else:
                new_m1 = (val1[0] + "0" * diff1 + "1" + val1[1 + self.e_bitwidth :])[
                    : 1 + self.m_bitwidth
                ]

            if int(e2, 2) == 0:
                new_m2 = (val2[0] + "0" * diff2 + val2[1 + self.e_bitwidth :])[
                    : 1 + self.m_bitwidth
                ]
            else:
                new_m2 = (val2[0] + "0" * diff2 + "1" + val2[1 + self.e_bitwidth :])[
                    : 1 + self.m_bitwidth
                ]

            new_mantissas.append((mx, (new_m1, new_m2)))

        self.data = (max_es, new_mantissas)

    def __str__(self) -> str:
        return str(self.data)

    def __len__(self):
        return len(self.data)

    def __mul__(self, other):
        """
        Define the * operation, so that we don't need to change too much in systolic array.
        It is the inner product.
        """
        if isinstance(other, MXBFP):
            assert len(self) == len(other), "length mismatch"
            assert len(self) > 0, "no values"

            s = 0
            for i in range(16):
                s += self.read_fp(i) * other.read_fp(i)
            return s

        elif other == None or other == 0:
            return 0

    def read(self, i):
        """
        Given index, return bitstring (as a 3-tuple of exp, mx bit, sign bit and mantissa).
        To get this as an actual FP32 value, use read_fp(i) method instead.
        """
        assert i < len(self.data[1]) * 2, "index out of bounds."

        exp = self.data[0]
        mx = self.data[1][i // 2][0]
        s_m = self.data[1][i // 2][1][i % 2]

        return exp, mx, s_m

    def read_fp(self, i):
        """
        Returns the FP value, given the index.
        """
        e, mx, s_m = self.read(i)
        s = s_m[0]
        m = s_m[1:]

        bias = 2 ** (self.e_bitwidth - 1) - 1

        # int(m, 2) / 2**(len(m)-1) gives the fractional value in decimal.
        # We multiply this by 2**(exp-bias).
        result = int(m, 2) * 2 ** (int(e, 2) - bias - len(m) - mx + 1)

        if s == "0":  # Positive
            return result
        else:
            return -result

    def read_fp_all(self):
        """
        Convert this MXBFP group to FP32.
        """
        l = []

        for i in range(16):
            l.append(self.read_fp(i))

        return l


if __name__ == "__main__":
    print("Conversion test")
    import random

    d = [random.random() for _ in range(16)]
    g = MXBFP(d, m_bitwidth=7)

    print(g.data)

    for i in range(16):
        print(i, g.read(i), g.read_fp(i), g.read_fp(i) - d[i])

    d2 = [random.random() for _ in range(16)]
    g2 = MXBFP(d2, m_bitwidth=2)

    print(g * g2)

    s = 0
    for i in range(16):
        s += d[i] * d2[i]

    print(s)
