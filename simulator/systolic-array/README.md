 # bfp-systolic-array-sw-sim.code
Simulator for MXBFP conversion and GEMM operations on systolic array.

## Prerequisites
Please install `python` and `numpy`.
Also, if it does not exist, please create the directory `results/gemm`.

## Running a single simulation
Run `python main.py <m> <k> <n> <bitwidth> <rows> <cols>`, where you want to simulate multiplying `m x k` x `k x n` matrix with MXBFP bitwidth of `bitwidth` on a systolic array of size `rows x cols`.

If you set the `WRITE` parameter in `main.py` as `True`, the results will be stored in `results/gemm`. If you make changes to the structure, make sure to delete the contents of the directory.

## Simulating a DNN model run
First, create two files in the `dims/` directory: `task.txt` and `task_unique.txt`. 

`task.txt` represents each layer of a DNN network. It should contain lines of m, k, and n, separated by tabs.

`task_unique.txt` should contain only the unique lines of task.txt. Due to convoy effect, it is advisable to place the smaller GEMM dimensions first.

After creating these files, run `./scripts/get_stats.sh task bitwidth cols`. This will simulate all given dimensions, for systolic array size of `{1 ... cols} x cols`.

Once that is completed, run `python get_stats_one.py task bitwidth cols`. This will combine the results of the layer-by-layer cycle simulations.
The output format is `(total cycles, stalled cycles, calculation cycles, MAC ops counts, util percentage)`.

---

For example, to simulate `resnet18_infer_b1`, you would do:
1. Create `resnet18_infer_b1.txt` and `resnet18_infer_b1_unique.txt` (we already provide the dimensions in the `dims/` directory).
2. Run `./scripts/get_stats.sh resnet18_infer_b1 7 8` to simulate bitwidth 7 (MX9) with systolic array sizes 1x8 to 8x8. You will see the `results/gemm` directory populated with lots of results.
3. Run `python get_stats_one.py resnet18_infer_b1 7 8` to get the result. `results/` should now have a file called `resnet18_infer_b1_8_7.out`.

## Combining results for emulator
Use `get_stats_all.py`. For example, if you want all the results for `resnet18_infer_b1` with systolic array size `{1 ... 8} x 8`, you would need 
- `resnet18_infer_b1_8_2.out`
- `resnet18_infer_b1_8_4.out`
- `resnet18_infer_b1_8_7.out`
files before running it.

Then run `python get_stats_all.py resnet18_infer_b1`. This will create a csv file in the `results` directory.

Note that for the results used in the paper (16x16 systolic array), you should edit `get_stats_all.py` file lines 7 and 8 so that `r` and `c` take values of 16.