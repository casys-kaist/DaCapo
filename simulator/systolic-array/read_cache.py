from math import floor, ceil


class ReadCache:
    """
    Read cache with double buffering.
    For realistic double buffering, we keep track of the group ID of the reads.
    Once the unique IDs hits limit, we call a stall.
    (Note that we do not model a separate memory and communications between it and this.)
    """

    def __init__(self, size, m_bitwidth, bandwidth, freq, group_size=16, e_bitwidth=8, cycle_only = False):
        """
        Size must be specified in bytes.
        Bandwidth in GB/s, frequency in MHz.
        """
        self.size = size
        self.group_size = group_size

        self.read_count = 0

        self.bandwidth = bandwidth
        self.freq = freq

        self.cycle_only = cycle_only

        if self.cycle_only:
            # If we need cycle counts, we just need to keep track of which group IDs are in.
            self.data = set()
        else:
            self.data = []

        self.current_hits = set()

        # We need to fill size/2 bytes, using bandwidth and freq.
        self.cycles_for_half_full = ceil(
            (self.size / 2) / (self.bandwidth * 1024) * (self.freq)
        )

        size_per_group_bytes = (
            e_bitwidth + (group_size // 2) + (group_size * (m_bitwidth + 1))
        ) / 8
        self.reads_per_one_fill = floor(self.size / size_per_group_bytes)

    def insert(self, data, idx):
        """
        Insert a group into idx.
        This is the equivalent of adding a BFP group to DRAM (though type of data is irrelevant).
        """
        if self.cycle_only:
            self.data.add(idx)
            return

        # If we need actual values, we need to make sure the data is inserted into its proper place.
        while len(self.data) <= idx:
            # Make space for new element.
            self.data.append(None)

        self.data[idx] = data

    def read_group(self, group_id):
        """
        Simulates read from cache. Returns (bool, obj).
        If cache hit, return (True, obj). If cache miss, return (False, obj).
        If group_id == -1 (used for skewing group idx matrix), returns (False, None).
        """
        # Return None when we don't actually read.
        if group_id == -1:
            return False, None

        if self.cycle_only:
            assert group_id in self.data, "this index does not exist"
        else:
            assert group_id < len(self.data), "index out of bounds"

        miss = False

        if len(self.current_hits) == 0:
            miss = True
            self.current_hits.add(group_id)

        elif len(self.current_hits) > self.reads_per_one_fill:
            # We have used up the buffer. Time to fill a new one.
            miss = True
            self.current_hits = {group_id}

        self.read_count += 1

        if self.cycle_only:
            # Return any integer, because we should't return None! 
            # The best integer we have on hand is the group id.
            return miss, group_id
        else:
            return miss, self.data[group_id]

    def no_Nones(self):
        """
        Verify that no elements are None.
        Please do this check after conversion of a matrix is complete.
        """
        for obj in self.data:
            if obj == None:
                return False
        return True


if __name__ == "__main__":
    example_cache = ReadCache(size=160 * 1024, m_bitwidth=4, bandwidth=204, freq=500)
    print(example_cache.reads_per_one_fill)
    print(example_cache.cycles_for_half_full)
