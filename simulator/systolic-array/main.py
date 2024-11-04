from sysarray import SystolicArray
from read_cache import ReadCache
from mx_converter import MXBFPConverter
from output_cycle_calc import OutputBFPConv
import numpy as np
from sys import argv, exit
from time import time
from os.path import isfile

# This file provides an example on how to run the software simulator.
# Bear in mind that this calculates all the values.
# If you are just interested in the metrics, you should turn on the CYCLE_ONLY option.

# Run this file in the following manner:
# python example.py m k n bitwidth rows cols
# where we simulate (m x k) x (k x n), with mantissa bitwidths of bitwidth,
# with systolic array dimension of (rows x cols).

# The parameters obtained from argv are ones that change frequently for simulation.
# Others, such as size of cache, bandwidth, frequency, are defined below.
# Exponent bitwidth is hard-coded as 8.

FREQ = 500  # in MHz
BW = 204.8  # in GB/s
SIZE = 160 * 1024  # in bytes, for both.
OUT_CONV_CYC = 16  # cycles needed to convert a group of values. Assume pipelining available.

WRITE = True        # Write results to results/gemm/.
CYCLE_ONLY = True  # Set to True if you just want cycle counts (significantly faster for large matrices).


def simulate(m, k, n, bitwidth, rows, cols):
    start = time()
    # 1. Create two matrics, mxk and kxn.
    lhs = np.random.normal(0, 1, size=(m, k))
    rhs = np.random.normal(0, 1, size=(k, n))

    # 2. Create the caches, and convert.
    lhs_cache = ReadCache(size=SIZE, m_bitwidth=bitwidth, bandwidth=BW, freq=FREQ, cycle_only=CYCLE_ONLY)
    lhs_conv = MXBFPConverter(m_bitwidth=bitwidth, e_bitwidth=8, cache=lhs_cache, cycle_only=CYCLE_ONLY)
    lhs_conv.convert(lhs)
    assert lhs_cache.no_Nones()

    # Make sure that the right-hand side matrix has vertical=True!
    rhs_cache = ReadCache(size=SIZE, m_bitwidth=bitwidth, bandwidth=BW, freq=FREQ, cycle_only=CYCLE_ONLY)
    rhs_conv = MXBFPConverter(
        m_bitwidth=bitwidth, e_bitwidth=8, cache=rhs_cache, vertical=True, cycle_only=CYCLE_ONLY)
    rhs_conv.convert(rhs)
    assert rhs_cache.no_Nones()

    # Note that if you want to just work with dimensions,
    # Divide k by 16, create cache objects, and feed the dimensions to the systolic array.

    # Before we create the systolic array, determine cycles per mac.
    if bitwidth == 7:
        cycles_per_mac = 16
    elif bitwidth == 4:
        cycles_per_mac = 4
    elif bitwidth == 2:
        cycles_per_mac = 1
    else:
        raise AssertionError("please manually set cycles per mac.")

    # 3. Create the systolic array.
    sArray = SystolicArray(
        "Backward_dW",
        lhs_cache,
        rhs_cache,
        (rows, cols),
        cycles_per_mac=cycles_per_mac,
        cycle_only=CYCLE_ONLY,
    )

    # 4. Multiply.
    result = sArray.multiply((m, k), (k, n))

    # 4-1. If you want to verify the results, find the differences.
    
    # If dimensions are different, that means that result has padding.
    # So remove it, and make sure all padding values are 0.
    # true_result = lhs @ rhs

    # if true_result.shape != result.shape:
    #     # print("resizing")
    #     assert np.all(result[m:, :] == 0)
    #     assert np.all(result[:, n:] == 0)

    #     result = result[:m, :n]

    # print(true_result)
    # print(result)

    # print((true_result - result) / result)

    # 5. We take the outputs when the groups are finished.
    # Let us calculate how many additional cycles we need for output conversion.
    # output_conv = OutputBFPConv((m, n), OUT_CONV_CYC)
    # added_cycles = output_conv.calculate()

    # Output is in the form of (total cycles, read stall cycles, calc cycles, util sum)
    cyc, stall, calc, util = sArray.get_stats()
    added_cycles = 0

    elapsed = time() - start

    if WRITE:
        args = (m, k, n, bitwidth, rows, cols)
        fname = "results/gemm/" + "-".join(str(x) for x in args) + ".out"
        f = open(fname, 'w')
        f.write(str(cyc + added_cycles))
        f.write('\t')
        f.write(str(stall))
        f.write('\t')
        f.write(str(calc))
        f.write('\t')
        f.write(str(added_cycles))
        f.write('\t')
        f.write(str(util))
        f.write('\n')
        f.write(str(elapsed))
        f.close()

    return (cyc + added_cycles, stall, calc, added_cycles, util)


if __name__ == "__main__":
    try:
        m = int(argv[1])
        k = int(argv[2])
        n = int(argv[3])
        bitwidth = int(argv[4])
        rows = int(argv[5])
        cols = int(argv[6])
    except:
        print("Not enough argv values. Using defaults... ")

        m = 1
        k = 512
        n = 4
        bitwidth = 7
        rows = 32
        cols = 32
    
    if WRITE:
        args = (m, k, n, bitwidth, rows, cols)
        fname = "results/gemm/" + "-".join(str(x) for x in args) + ".out"

        if isfile(fname):
            exit()

    print(simulate(m, k, n, bitwidth, rows, cols))
