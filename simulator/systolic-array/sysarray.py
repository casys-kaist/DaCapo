import numpy as np
from read_cache import ReadCache
from math import ceil
from mx_converter import MXBFPConverter


def log(n, msg):
    print(msg)


class SystolicArray:
    """
    An array of fMACs.
    """

    def __init__(
        self,
        mode,
        lhs_cache: ReadCache,
        rhs_cache: ReadCache,
        size=(2, 2),
        cycles_per_mac=1,
        cycle_only=False,
    ) -> None:
        """
        Specify mode of the systolic array: one from
        "Forward_O", "Backward_dA", "Backward_dW".
        Also, specify size of systolic array as tuple.
        """
        assert mode in {
            "Forward_O",
            "Backward_dA",
            "Backward_dW",
        }, "mode should be specified properly"
        self.mode = mode

        self.cycles = 0
        self.stalled_cycles = 0
        self.calc_cycles = 0
        self.total_util_sum = 0
        self.last_stalled_cycle = 0

        # Set this to True if you don't care about the actual values.
        self.cycle_only = cycle_only

        self.sysarr_row, self.sysarr_col = size

        self.cycles_per_mac = cycles_per_mac

        self.lhs_cache = lhs_cache
        self.rhs_cache = rhs_cache

        self.cache_miss = 0

        # Create the fMAC array
        self.fmac_array = [
            [
                fMAC(mode, w=0, c=cycles_per_mac, cycle_only=self.cycle_only)
                for i in range(self.sysarr_col)
            ]
            for j in range(self.sysarr_row)
        ]

        if self.cycle_only:
            self.cached_cycles = {}

        # Flag for Backward_dW termination condition
        if self.mode == "Backward_dW":
            self.first = True

    def __str__(self) -> str:
        return "\n".join(str(x) for x in self.fmac_array)

    def reset(self):
        self.first = True
        for iy in range(self.sysarr_row):
            for ix in range(self.sysarr_col):
                self.fmac_array[iy][ix].reset()

    def prep_lhs(self, mat):
        """
        Skew the left matrix appropriately (what will enter from the bottom).
        Used in Forward_O and Backward_dW.
        """
        assert isinstance(mat, np.ndarray), "not a numpy array"
        assert len(mat.shape) == 2, "not a 2D array"
        assert self.mode in {
            "Forward_O",
            "Backward_dW",
        }, "cannot be used in Backward_dA mode."

        # We use -1 as non-existent group ID, for skewing.
        mat_skewed = np.full((self.sysarr_col - 1 + mat.shape[0], self.sysarr_col), -1)
        for i in range(self.sysarr_col):
            mat_skewed[i : i + mat.shape[0], i] = mat[:, i]

        return mat_skewed

    def prep_rhs(self, mat):
        """
        Skew the right matrix appropriately (what will enter from the left.)
        Used in Backward_dA and Backward_dW.
        """
        assert isinstance(mat, np.ndarray), "not a numpy array"
        assert len(mat.shape) == 2, "not a 2D array"
        assert self.mode in {
            "Backward_dA",
            "Backward_dW",
        }, "cannot be used in Forward_O mode."

        # We use -1 as non-existent group ID, for skewing.
        mat_skewed = np.full((self.sysarr_row, self.sysarr_row - 1 + mat.shape[1]), -1)
        for i in range(self.sysarr_row):
            mat_skewed[i, i : i + mat.shape[1]] = mat[i, :]

        return mat_skewed

    def queues_full(self):
        """
        Determine if any queue of any fMAC in the array is full.
        If it is, we shouldn't read off the next value.
        """
        for x in range(self.sysarr_col):
            for y in range(self.sysarr_row):
                current_fmac = self.fmac_array[y][x]
                if current_fmac.queue_full():
                    return True
        return False

    def prepare_inputs(self, vals, vals2=None):
        """
        Given the matrices, this will do the preprocessing of the data,
        such as skewing, etc.
        """
        # assert vals.shape == (self.size, self.size), "You should probably do tiling!"

        # if isinstance(vals2, np.ndarray):
        #     assert vals2.shape == (self.size, self.size), "You should probably do tiling!"

        # Skew inputs
        if self.mode == "Forward_O":
            self.vals_skewed = self.prep_lhs(vals)

        elif self.mode == "Backward_dA":
            self.vals_skewed = self.prep_rhs(vals[::-1, ::-1].T)

        elif self.mode == "Backward_dW":
            self.vals1_skewed = self.prep_lhs(vals.T)
            self.vals2_skewed = self.prep_rhs(vals2[::-1, ::-1].T)

    def set_weights(self, weights):
        """
        Set weight values in the fMAC array for Forward_O and Backward_dA.
        """
        assert self.mode in {
            "Forward_O",
            "Backward_dA",
        }, "cannot be used in Backward Pass dW."
        assert isinstance(weights, np.ndarray), "not a numpy array"
        assert len(weights.shape) == 2, "not a 2D array"

        if self.mode == "Forward_O":
            weights = np.rot90(weights)
        elif self.mode == "Backward_dA":
            weights = np.flipud(weights)

        for iy, ix in np.ndindex(weights.shape):
            self.fmac_array[iy][ix].weight = weights[iy, ix]

    def reset_results(self):
        """
        Clear the accumulator in every fMAC.
        """
        for iy in range(self.sysarr_row):
            for ix in range(self.sysarr_col):
                self.fmac_array[iy][ix].reset_result()

    def progress_f(self):
        """
        Do a one-cycle progression when in Forward_O mode.
        Please don't use this directly, unless you really have to.
        Instead, use the multiply() method, which calls this implicitly.
        """
        assert self.mode == "Forward_O"
        self.cycles += 1

        # Get the values from neighboring fMAC or memory, then send to next unit.
        # x should be in reverse order.
        for x in range(self.size - 1, -1, -1):
            for y in range(self.size):
                current_fmac = self.fmac_array[y][x]

                # Consider the cases where fMAC is at the edge.
                try:
                    bottom_fmac = self.fmac_array[y + 1][x]
                    bottom_val = bottom_fmac.send()[0]
                except:
                    try:
                        bottom_val = self.vals_skewed[0][x]
                    except:
                        bottom_val = None

                if (x - 1) >= 0:  # Negative indices wrap around
                    left_fmac = self.fmac_array[y][x - 1]
                    left_val = left_fmac.send()[1]
                else:
                    left_val = 0

                current_fmac.get(bottom_val, left_val)
                # print(y, x, current_fmac)

        # Delete first row of vals_skewed
        try:
            self.vals_skewed = np.delete(self.vals_skewed, 0, 0)
        except:
            # Used all rows
            pass

        # print(self.fmac_array)
        for y in range(self.size):
            for x in range(self.size):
                self.fmac_array[y][x].do_calc()

        # print(self.fmac_array)

        # Read out the result from the rightmost fMACs.
        res = np.zeros((self.size, 1))
        for y in range(self.size):
            res[y] = self.fmac_array[y][-1].send()[1]

        # print(res)

        if np.all(np.isnan(res)):
            return
        else:
            res = np.nan_to_num(res, nan=0)
            self.result_skewed = np.c_[res, self.result_skewed]

    def progress_bdA(self):
        """
        Do a one-cycle progression when in Backward_dA mode.
        Please don't use this directly, unless you really have to.
        Instead, use the multiply() method, which calls this implicitly."""
        assert self.mode == "Backward_dA"
        self.cycles += 1

        # Get the values from neighboring fMAC or memory, then send to next unit.
        # x needs to be in reverse order.
        for x in range(self.size - 1, -1, -1):
            for y in range(self.size):
                current_fmac = self.fmac_array[y][x]

                # Consider the cases where fMAC is at the edge.
                try:
                    bottom_fmac = self.fmac_array[y + 1][x]
                    bottom_val = bottom_fmac.send()[0]
                except:
                    bottom_val = 0

                if (x - 1) >= 0:  # Negative indices wrap around
                    left_fmac = self.fmac_array[y][x - 1]
                    left_val = left_fmac.send()[1]
                else:
                    try:
                        left_val = self.vals_skewed[y][-1]
                    except:
                        # Used up all values
                        left_val = None

                current_fmac.get(bottom_val, left_val)

        # Delete last column of vals_skewed
        try:
            self.vals_skewed = np.delete(self.vals_skewed, -1, 1)
        except:
            # Used all rows
            pass

        for y in range(self.size):
            for x in range(self.size):
                self.fmac_array[y][x].do_calc()

        # Placeholder for result that will be prepended to self.result_skewed
        res = np.zeros(self.size)

        for x in range(self.size):
            res[x] = self.fmac_array[0][x].send()[0]

        # print(res)

        if np.all(np.isnan(res)):
            return
        else:
            res = np.nan_to_num(res, nan=0)
            self.result_skewed = np.vstack([self.result_skewed, res])

    def progress_bdW(self):
        """
        Do a one-cycle progression when in Backward_dW mode.
        Please don't use this directly, unless you really have to.
        Instead, use the multiply() method, which calls this implicitly.
        """
        assert self.mode == "Backward_dW"

        # Get the values from neighboring fMAC or cache, then send to next unit.
        # x needs to be in reverse order.

        # Read out the next set of values if queue still has space.
        if not self.queues_full():
            miss_lhs = 0
            miss_rhs = 0
            for x in range(self.sysarr_col - 1, -1, -1):
                for y in range(self.sysarr_row):
                    current_fmac = self.fmac_array[y][x]

                    # Consider the cases where fMAC is at the edge.
                    try:
                        bottom_fmac = self.fmac_array[y + 1][x]
                        bottom_val = bottom_fmac.send()[0]
                    except:
                        try:
                            bottom_val = self.vals1_skewed[0][x]
                            miss, bottom_val = self.lhs_cache.read_group(bottom_val)

                            if miss:
                                miss_lhs += 1
                        except:
                            bottom_val = None

                    if (x - 1) >= 0:  # Negative indices wrap around
                        left_fmac = self.fmac_array[y][x - 1]
                        left_val = left_fmac.send()[1]
                    else:
                        try:
                            left_val = self.vals2_skewed[y][-1]
                            miss, left_val = self.rhs_cache.read_group(left_val)

                            if miss:
                                miss_rhs += 1
                        except:
                            # Used up all values
                            left_val = None

                    current_fmac.get(bottom_val, left_val)

            self.cache_miss += max(miss_lhs, miss_rhs)

            # assert self.cache_miss < 2, "two cache misses! You might want to make your buffer larger.."

            # Determine how many cycles since last cache miss, and how many cycles we are at right now.
            if max(miss_lhs, miss_rhs) > 0:
                # print("stall", miss_lhs, miss_rhs)
                if (
                    self.last_stalled_cycle + self.lhs_cache.cycles_for_half_full
                    > self.cycles
                ):
                    # print("bottleneck")
                    self.stalled_cycles += (
                        self.last_stalled_cycle
                        + self.lhs_cache.cycles_for_half_full
                        - self.cycles
                    )
                    self.cycles = (
                        self.last_stalled_cycle + self.lhs_cache.cycles_for_half_full
                    )
                    self.last_stalled_cycle = self.cycles

            # Delete last column and row of vals
            if self.vals1_skewed.shape[0] > 0:
                self.vals1_skewed = np.delete(self.vals1_skewed, 0, 0)

            if self.vals2_skewed.shape[1] > 0:
                self.vals2_skewed = np.delete(self.vals2_skewed, -1, 1)

        # Now, one cycle of calculation.
        self.cycles += 1
        self.calc_cycles += 1

        # util_matrix = np.zeros((self.sysarr_row, self.sysarr_col))

        for y in range(self.sysarr_row):
            for x in range(self.sysarr_col):
                if self.fmac_array[y][x].do_calc():
                    self.total_util_sum += 1
                    # util_matrix[y, x] = 1

        # print(util_matrix)
        # print()

        self.first = False

    def multiply(self, lhs_shape, rhs_shape):
        """
        The core. This is what is used to multiply.
        Uses self.vals_skewed and weights set by set_weights.
        Returns a numpy array of FP values.
        Calls progress_{f/bdA/bdW}() on every cycle.
        """

        # Check dimensions
        assert lhs_shape[1] == rhs_shape[0], "matrix dimensions don't match"

        m, k = lhs_shape
        k, n = rhs_shape

        # Create groupings.
        lhs = np.arange(0, m * ceil(k / 16), 1).reshape(int(m), ceil(int(k) / 16))
        rhs = np.arange(0, n * ceil(k / 16), 1).reshape(
            ceil(int(k) / 16), int(n), order="F"
        )

        if self.mode == "Backward_dW":
            # Check if matrix dims match. Otherwise, we need to add padding.
            if lhs.shape[0] % self.sysarr_col != 0:
                # Add padding on y direction of value -1.
                print("Adding LHS padding.")
                lhs = np.pad(
                    lhs,
                    ((0, self.sysarr_col - (lhs.shape[0] % self.sysarr_col)), (0, 0)),
                    mode="constant",
                    constant_values=-1,
                )
            if rhs.shape[1] % self.sysarr_row != 0:
                # Add padding on x direction.
                print("Adding RHS padding.")
                rhs = np.pad(
                    rhs,
                    ((0, 0), (0, self.sysarr_row - (rhs.shape[1] % self.sysarr_row))),
                    mode="constant",
                    constant_values=-1,
                )

            # Create placeholder
            result = np.zeros((lhs.shape[0], rhs.shape[1]))

            # Tiling
            for i in range(lhs.shape[0] // self.sysarr_col):
                for j in range(rhs.shape[1] // self.sysarr_row):
                    # print(i, j)
                    # 0. Reset all incremented values to 0.
                    self.reset()

                    # 1. Create the tile sections.
                    tile_l = lhs[self.sysarr_col * i : self.sysarr_col * (i + 1), :]
                    tile_r = rhs[:, self.sysarr_row * j : self.sysarr_row * (j + 1)]

                    # 2. Skew the inputs and put them in properly (using prepare_inputs)
                    self.prepare_inputs(tile_l, tile_r)

                    # 3. Calculate the number of cycles for systolic array.
                    # Use cached result if possible.
                    if self.cycle_only:
                        actual_lhs, actual_rhs = tile_l, tile_r

                        # Check if there is a -1, because this means padding.
                        if np.any(tile_l < 0):
                            actual_lhs = np.delete(tile_l, np.where(np.all(tile_l < 0, axis=1))[0], axis=0)
                        
                        if np.any(tile_r < 0):
                            actual_rhs = np.delete(tile_r, np.where(np.all(tile_r < 0, axis=0))[0], axis=1)

                        try:
                            calc_cycles_per_tile, util_per_tile = self.cached_cycles[(actual_lhs.shape, actual_rhs.shape)]

                            # Assume that we don't get any more read stalls
                            self.cycles += calc_cycles_per_tile
                            self.calc_cycles += calc_cycles_per_tile
                            self.total_util_sum += util_per_tile

                            continue
                        
                        except:
                            pass
                    
                    # How do we determine finished state? We check the total util.
                    util_this_tile = self.total_util_sum
                    calc_cycles_this_tile = self.calc_cycles

                    while True:
                        old_util = self.total_util_sum
                        self.progress_bdW()

                        if self.total_util_sum == old_util:
                            break

                    # Note that this overshoots the calculation by 1 cycle.
                    # So we need to fix this.
                    self.cycles -= 1
                    self.calc_cycles -= 1

                    if self.cycle_only:
                        self.cached_cycles[(actual_lhs.shape, actual_rhs.shape)] = (self.calc_cycles - calc_cycles_this_tile, self.total_util_sum - util_this_tile)

                    # Get the results if we actually want them.
                    if not self.cycle_only:
                        result_section = np.zeros((self.sysarr_row, self.sysarr_col))

                        for y in range(self.sysarr_row):
                            for x in range(self.sysarr_col):
                                result_section[y][x] = self.fmac_array[y][x].give_result()

                        result_section = np.rot90(result_section, 3)

                        # 4. Place the result at the appropriate index.
                        result[
                            i * self.sysarr_col : (i + 1) * self.sysarr_col,
                            j * self.sysarr_row : (j + 1) * self.sysarr_row,
                        ] = result_section

            return result

    def unskew(self, mat, dim):
        """
        Method to unskew the result matrix.
        The skewed shape depends on the cycle count:
        there is a staircase every (cycles_per_mac).
        """
        c = self.cycles_per_mac
        res = np.zeros(dim)

        if self.mode == "Forward_O":
            # i is a staircase counter.
            if c > self.size:
                i = 1
            else:
                i = ceil(self.size / c)

            for n in range(self.size, 0, -c):
                s = n - c  # s : start idx
                if s < 0:
                    s = 0

                i -= 1

                res[s:n, :] = mat[s:n, i : i + dim[1]]
                log(3, res)

        elif self.mode == "Backward_dA":
            i = -1
            for n in range(0, self.size, c):
                s = n + c  # s : start idx
                if s > self.size:
                    s = self.size

                i += 1
                res[:, n:s] = mat[self.size + i : self.size + i + dim[0], n:s]

        return res

    def get_stats(self):
        """
        Return cycle count.
        """
        return self.cycles, self.stalled_cycles, self.calc_cycles, self.total_util_sum


class fMAC:
    """
    A single fMAC.
    """

    def __init__(self, mode, w=0, c=1, cycle_only=False) -> None:
        """
        Initialize single fMAC. You should probably use SystolicArray.__init__,
        which implicitly calls this.
        """
        assert mode in {
            "Forward_O",
            "Backward_dA",
            "Backward_dW",
        }, "mode should be specified properly"
        self.mode = mode

        self.cycles_per_mac = c
        self.cycles = 0

        self.calc_start_cycle = 0
        self.cycle_only = cycle_only

        self.operands = (None, None)

        # Need to differentiate between actual 0 that should be passed and placeholder.
        if self.mode == "Forward_O":
            self.bottom = None
            self.left = 0
        elif self.mode == "Backward_dA":
            self.bottom = 0
            self.left = None

        if self.mode in {"Forward_O", "Backward_dA"}:
            self.result = None
            self.weight = w
            self.queue = []

        # Output stationary.
        else:
            self.result = 0
            self.bottom = None
            self.left = None
            self.queue_l = []
            self.queue_b = []

            self.max_queue_len = 16

    def __str__(self) -> str:
        if self.mode in {"Forward_O", "Backward_dA"}:
            return f"{self.mode} w:{self.weight} r:{self.result} b:{self.bottom} l:{self.left} {self.queue}"
        else:
            return f"r:{self.result} b:{self.bottom} l:{self.left} {self.queue_b} {self.queue_l}"

    def __repr__(self) -> str:
        return self.__str__()

    def reset(self):
        if self.mode in {"Forward_O", "Backward_dA"}:
            self.__init__(mode=self.mode, w=self.weight, c=self.cycles_per_mac)
        else:
            self.__init__(
                mode=self.mode, c=self.cycles_per_mac, cycle_only=self.cycle_only
            )

    def queue_empty(self):
        if self.mode in {"Forward_O", "Backward_dA"}:
            return len(self.queue) == 0
        else:
            assert len(self.queue_b) == len(self.queue_l), "queue lengths mismatch"
            return len(self.queue_b) == 0

    def queue_full(self):
        if self.mode in {"Forward_O", "Backward_dA"}:
            return len(self.queue) >= self.max_queue_len
        else:
            assert len(self.queue_b) == len(self.queue_l), "queue lengths mismatch"
            return len(self.queue_b) >= self.max_queue_len

    def reset_result(self):
        self.result = None

    def enqueue(self, val, val2=None):
        assert val != None, "are you sure you want to enqueue None?"
        assert not self.queue_full(), "queue is full!"

        if self.mode in {"Forward_O", "Backward_dA"}:
            self.queue.append(val)
        else:
            assert val2 != None, "are you sure you want to enqueue None?"
            self.queue_b.append(val)
            self.queue_l.append(val2)

    def dequeue(self):
        if self.mode in {"Forward_O", "Backward_dA"}:
            if len(self.queue) > 0:
                return self.queue.pop(0)
            else:
                return None
        else:
            assert len(self.queue_l) == len(self.queue_b), "queue lengths mismatch"

            if len(self.queue_b) > 0 and len(self.queue_l) > 0:
                return self.queue_b.pop(0), self.queue_l.pop(0)
            else:
                return None, None

    def send(self):
        """
        Send the appropriate values up and to the right.
        """
        if self.mode == "Forward_O":
            send_up = self.bottom

            # We need to hold the values from the left to match with the pipelining
            if (self.cycles + 1) % self.cycles_per_mac == 0:
                send_right = self.result
            else:
                send_right = None

        elif self.mode == "Backward_dA":
            send_right = self.left

            if (self.cycles + 1) % self.cycles_per_mac == 0:
                send_up = self.result
            else:
                send_up = None

        elif self.mode == "Backward_dW":
            send_up = self.bottom
            send_right = self.left

        return send_up, send_right

    def get(self, b, l):
        """
        Get the appropriate value from the bottom and from the left.
        """

        if self.mode == "Forward_O":
            if l != None:
                self.left = l
            else:
                self.left = 0

            if b != None:
                self.bottom = b
                self.enqueue(b)
            else:
                self.bottom = None

        elif self.mode == "Backward_dA":
            if b != None:
                self.bottom = b
            else:
                self.bottom = 0

            if l != None:
                self.left = l
                self.enqueue(l)
            else:
                self.left = None

        elif self.mode == "Backward_dW":
            if l != None and b != None:
                self.bottom = b
                self.left = l

                self.enqueue(b, l)
            else:
                self.bottom = None
                self.left = None

    def do_calc(self):
        """
        Do the appropriate multiply-accumulate calculation.
        Note that we can use the same * operation,
        since mx_type.MXBFP defines __mul__().
        """

        if self.mode == "Forward_O":
            b = self.dequeue()
            if b == None:
                self.result = None
                return

            elif self.left == None:
                return

            else:
                self.result = self.left + b * self.weight

        elif self.mode == "Backward_dA":
            l = self.dequeue()
            if l == None:
                self.result = None
                return

            elif self.bottom == None:
                return

            else:
                self.result = l * self.weight + self.bottom

        elif self.mode == "Backward_dW":
            # Fix: On self.dequeue() that does not give None, wait cycles_per_mac cycles, with util as True.
            # When the cycles are done, return the result. On next cycle, dequeue.

            # Let's say we start at cycle 0, and mac operation takes 3 cycles.
            # Then we need to return True (util) on cycles 0, 1, 2,
            # and return the result at cycle 2.

            if self.cycles_per_mac == 1:
                b, l = self.dequeue()
                self.cycles += 1
                if b != None and l != None:
                    if not self.cycle_only:
                        self.result += l * b
                    return True
                else:
                    return False

            else:
                if self.operands == (None, None):
                    self.operands = self.dequeue()

                    if self.operands == (None, None):
                        self.cycles += 1
                        return False
                    else:
                        self.calc_start_cycle = self.cycles
                        self.cycles += 1
                        return True
                else:
                    cycles_elapsed = self.cycles - self.calc_start_cycle + 1

                    if cycles_elapsed < self.cycles_per_mac:
                        self.cycles += 1
                        return True

                    elif cycles_elapsed == self.cycles_per_mac:
                        if not self.cycle_only:
                            self.result += self.operands[0] * self.operands[1]
                        self.cycles += 1
                        self.operands = (None, None)
                        return True

                    else:
                        raise AssertionError("this code block should not be entered!")

    def give_result(self):
        """
        Return the accumulated value. Should only be used in Backward_dW mode.
        (although we can't assert, since that's an attribute in SystolicArray, not here)
        """
        return self.result


if __name__ == "__main__":
    log(0, "Testing systolic array")

    # act_t = np.array([[1, 5], [4, 2]])
    # grad_o = np.array([[3, 4], [1, 2]])

    m = 64
    k = 256
    n = 64

    act_t = np.random.normal(0, 1, size=(m, k))
    grad_o = np.random.normal(0, 1, size=(k, n))

    lhs_cache = ReadCache(size=80 * 1024, m_bitwidth=7, bandwidth=204.8, freq=500)
    lhs_conv = MXBFPConverter(m_bitwidth=7, e_bitwidth=8, cache=lhs_cache)
    lhs_conv.convert(act_t)
    assert lhs_cache.no_Nones()

    rhs_cache = ReadCache(size=80 * 1024, m_bitwidth=7, bandwidth=204.8, freq=500)
    rhs_conv = MXBFPConverter(
        m_bitwidth=7, e_bitwidth=8, cache=rhs_cache, vertical=True
    )
    rhs_conv.convert(grad_o)
    assert rhs_cache.no_Nones()

    sArray3 = SystolicArray("Backward_dW", lhs_cache, rhs_cache, (16, 16), 2)

    print(act_t)
    print(grad_o)
    print()

    result = sArray3.multiply((m, k), (k, n))
    log(1, result)

    true_result = act_t @ grad_o
    log(1, true_result)
    log(0, result - true_result)

    log(0, sArray3.get_stats())
