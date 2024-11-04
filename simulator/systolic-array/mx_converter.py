import numpy as np
from mx_type import MXBFP
from read_cache import ReadCache


class MXBFPConverter:
    """
    This is a hardware simulator for converting a 2D FP array to MXBFP.
    The actual logic of the conversion is done in mx_type.py.
    This is more about the cycle and operations count.
    """

    def __init__(self, m_bitwidth, e_bitwidth, cache, vertical=False, cycle_only=False):
        self.find_max = 0
        self.subtracts = 0
        self.shifts = 0
        self.truncations = 0

        self.cycles = 0

        self.cycle_only = cycle_only

        self.m_bitwidth = m_bitwidth
        self.e_bitwidth = e_bitwidth

        self.cache = cache

        self.group_size = 16

        self.vertical = vertical  # Do grouping vertically.

    def convert_group_of_vals(self, vals, idx):
        """
        Given a group of values with length <= group size,
        create a MXBFP group, adding 0 padding if necessary.
        Each MXBFP group has 1 shared exponent, MX bits, sign bit, and mantissa bit.
        """
        # Find maximum of each group
        self.find_max += 1

        # Find max for every subgroup
        self.find_max += 8
        self.subtracts += 16
        self.shifts += 16
        self.truncations += 16

        ### Still need to check for cycle_only, otherwise creating MXBFP object still occurs.
        if not self.cycle_only:
            self.cache.insert(MXBFP(vals, m_bitwidth=self.m_bitwidth, e_bitwidth=self.e_bitwidth), idx)
        else:
            self.cache.insert(idx, idx) # Just insert any integer on hand

    def convert(self, vals: np.ndarray):
        """
        Main method to use to convert. This method calls convert_group_of_vals accordingly.
        """
        # We get a 2D numpy matrix.
        assert len(vals.shape) == 2, "input is not 2D."
        # assert vals.shape[0] * vals.shape[1] >= self.group_size, "too small"

        self.m, self.n = vals.shape

        idx = 0

        if not self.vertical:
            for y in range(self.m):
                for i in range(0, self.n, self.group_size):
                    xa = i
                    xb = min(i + self.group_size, self.n)
                    # log(1, "Converting %i-%i of row %i" % (xa, xb, y))
                    self.convert_group_of_vals(vals[y, xa:xb], idx)
                    idx += 1

        else:
            for x in range(self.n):
                for i in range(0, self.m, self.group_size):
                    ya = i
                    yb = min(i + self.group_size, self.m)
                    # log(1, "Converting %i-%i of column %i" % (ya, yb, x))
                    self.convert_group_of_vals(vals[ya:yb, x], idx)
                    idx += 1

    def get_stats(self):
        """
        Get information on the operations carried out.
        """
        return self.find_max, self.subtracts, self.shifts, self.truncations


if __name__ == "__main__":
    print("Testing converter")

    m = 16
    n = 256

    mat = np.random.random((m, n))

    ch = ReadCache(size=80 * 1024, m_bitwidth=7, bandwidth=204.8, freq=500)
    c = MXBFPConverter(m_bitwidth=7, e_bitwidth=8, cache=ch)

    c.convert(mat)

    assert ch.no_Nones()

    print(mat[0, 240:])
    print(ch.data[15].read_fp_all())
