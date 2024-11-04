import numpy as np
from sys import argv

class OutputBFPConv:
    """
    Simulator for when taking outputs out of the systolic array.
    """

    def __init__(self, shape, cycles_for_group_conv) -> None:
        """
        shape is given as a 2-tuple.
        Specify cycles for group conversion here.
        We assume pipelining is available.
        """

        self.m, self.n = shape
        self.cycles_for_group_conv = cycles_for_group_conv

    def calculate(self):
        """
        We have 0 as calculation incomplete, 1 as complete,
        and 2 as output sent to converter.
        3 is "zero padding" for group.
        """
        matrix = np.zeros((self.m, self.n), dtype=int)

        # Add padding if necessary.
        if self.m % 16 != 0:
            real_m = (self.m // 16 + 1) * 16
            matrix = np.pad(
                matrix,
                ((16 - (self.m % 16), 0), (0, 0)),
                mode="constant",
                constant_values=3,
            )
        else:
            real_m = self.m

        if self.n % 16 != 0:
            real_n = (self.n // 16 + 1) * 16
            matrix = np.pad(
                matrix,
                ((0, 0), (0, 16 - (self.n % 16))),
                mode="constant",
                constant_values=3,
            )
        else:
            real_n = self.n

        matrix[-1, 0] = 1
        print(matrix)

        cycles_for_completion = self.m + self.n - 1
        convert_added_cycles = 0

        i = 1
        while not np.all(matrix > 0):
            # If there are still zeros, turn the next diagonal along to 1s.
            if np.any(matrix == 0):
                row = self.m - i - 1
                col = 0
                while row < self.m and col < self.n:
                    if matrix[row, col] == 0:
                        matrix[row, col] = 1
                    row += 1
                    col += 1
            i += 1
            # Now, if we find 16 contiguous positive values, then we turn them into 2.
            # But we can only change one group at a time.

            horizontal_convs = 0
            for y in range(real_m - 1, real_m - self.m - 1, -1):
                for x in range(0, real_n, 16):
                    if (
                        np.all(matrix[y, x : x + 16] > 0)
                        and not np.all(matrix[y, x : x + 16] == 3)
                        and not np.all(matrix[y, x : x + 16] == 2)
                    ):
                        horizontal_convs += 1
                        matrix[y, x : x + 16] = 2

            vertical_convs = 0
            for x in range(self.n):
                for y in range(0, real_m, 16):
                    if (
                        np.all(matrix[y : y + 16, x] > 0)
                        and not np.all(matrix[y : y + 16, x] == 3)
                        and not np.all(matrix[y : y + 16, x] == 2)
                    ):
                        vertical_convs += 1
                        matrix[y : y + 16, x] = 2

            convs = max(horizontal_convs, vertical_convs)
            # print(convs)
            if convs == 0:
                convert_added_cycles -= 1
                if convert_added_cycles < 0:
                    convert_added_cycles = 0
            else:
                cycles_to_go = convs * self.cycles_for_group_conv
                if cycles_to_go > convert_added_cycles:
                    convert_added_cycles = cycles_to_go
                else:
                    convert_added_cycles -= 1
                    if convert_added_cycles < 0:
                        convert_added_cycles = 0

            # print(matrix)
            # print()

        return i - cycles_for_completion + convert_added_cycles


if __name__ == "__main__":
    try:
        m = int(argv[1])
        n = int(argv[2])
    except:
        print("Not enough argv values. Using default of 16 x 16.")
        m = 16
        n = 16

    c = OutputBFPConv((m, n), 2)
    print(c.calculate())
