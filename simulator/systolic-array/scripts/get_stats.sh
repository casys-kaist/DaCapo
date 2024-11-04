#!/bin/bash

# Usage:
# Run this code in the root directory of the repo.
# ./scripts/get_stats.sh task_name bitwidth c
# r goes from c downto 1 (to make runs faster)


for (( i=8; i>=1; i-- )); do
    while IFS=$'\t' read -r m k n; do
        echo $m $k $n $2 $i $3
        python main.py $m $k $n $2 $i $3
    done < dims/$1_unique.txt
done