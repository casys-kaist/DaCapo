from sys import argv

filename = argv[1]

model, jobtype, batchsize = filename.split("_")

r = 8
c = 8

outfile = open(f"results/{model}-{jobtype}-{batchsize}.csv", "w")
f1 = open(f"dims/{model}_{jobtype}_{batchsize}.txt")

outfile.write("mantissa_row-num_batch-size_type, Total_cycles, Read_stall, MAC_cycles, Mac_ops_count, Util\n")

for bitwidth in [2, 4, 7]:
    for rows in range(1, r+1):
        batch = batchsize[1:]
        t = "T" if jobtype == "train" else "I"

        # We have the header ready. Now read individual GEMM results and add.

        try:
            f = open(f"dims/{model}_{jobtype}_{batchsize}_unique.txt")
        except:
            raise AssertionError("dimension reading failed. Please read the instructions. ")

        result_dict = {}

        skip = False
        total = [0, 0, 0, 0, 0]

        f.seek(0)

        for l in f:
            m, k, n = l.strip().split()

            args = (int(m), int(k), int(n), int(bitwidth), int(rows), int(c))

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

        if not skip:
            f1.seek(0)

            for l in f1:
                m, k, n = l.strip().split()

                args = (int(m), int(k), int(n), int(bitwidth), int(rows), int(c))
                results = result_dict[args]
                total = [x + y for x, y in zip(total, results)]

            # r -> util
            total.pop(3)
            total.append(str(int(total[3]) / (rows * int(total[2]) * c) ))
            
            outfile.write(f"{bitwidth}_{rows}_{batch}_{t}, ")
            outfile.write(", ".join(str(x) for x in total))
        
        outfile.write("\n")

outfile.close()