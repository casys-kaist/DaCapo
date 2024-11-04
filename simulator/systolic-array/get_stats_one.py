from sys import argv

# This file provides an example on how to get statistics from the simulator,
# based on given GEMM dimensions.
# Bear in mind that this calculates all the values.
# If you are just interested in the metrics, you should turn on the cycles_only option.

# Before running this file, you need to create 2 txt files in the dims/ directory.
# dims/jobname.txt should contain a list of (m, k, n) values, separated by tabs,
# one line for every layer.
# dims/jobname_unique.txt should contain a list of (m, k, n) values, separated by tabs,
# regardless of order. However, (m, k, n)'s should be unique.

# Run this file in the following manner:
# python get_stats.py task_name bitwidth r c
# And it will calculate the results for the task with the desired bitwidth,
# for all systolic array dimensions of r x c.

if __name__ == "__main__":
    try:
        jobtype = argv[1]
        bitwidth = int(argv[2])
        c = int(argv[3])
    except:
        raise AssertionError("not enough argv values. Please read the instructions. ")

    try:
        f = open(f"dims/{jobtype}_unique.txt")
    except:
        raise AssertionError("dimension reading failed. Please read the instructions. ")

    outfile = open(f"results/{jobtype}_{c}_{bitwidth}.out", "w")

    result_dict = {}

    for r in range(1, c + 1):
        skip = False
        total = [0, 0, 0, 0, 0]

        f.seek(0)

        for l in f:
            m, k, n = l.strip().split()

            args = (int(m), int(k), int(n), int(bitwidth), int(r), int(c))

            try:
                filename = "results/gemm/" + "-".join(str(x) for x in args) + ".out"
                cached = open(filename)
                results = cached.readline().strip().split("\t")
                results = [int(x) for x in results]
                result_dict[args] = results
            except:
                print ("no result for", args)
                skip = True

        if skip:
            outfile.write("\n")
            continue

        f1 = open(f"dims/{jobtype}.txt")

        for l in f1:
            m, k, n = l.strip().split()

            args = (int(m), int(k), int(n), int(bitwidth), int(r), int(c))
            results = result_dict[args]
            total = [x + y for x, y in zip(total, results)]

        # r -> util
        total.pop(3)
        total.append(str(int(total[3]) / (r * int(total[2]) * c) ))
        
        outfile.write("\t".join(str(x) for x in total))
        outfile.write("\n")

    f.close()
    try:
        f1.close()
    except:
        pass

    outfile.close()
