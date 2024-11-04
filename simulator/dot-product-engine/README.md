
## Environment setting
### Install SDK
```
curl -s "https://get.sdkman.io" | bash  
```

### Install Scala
```
sdk install scala 2.13.11
```
### Install SBT
```
sdk install java $(sdk list java | grep -o "\b8\.[0-9]*\.[0-9]*\-tem" | head -1)
sdk install sbt
```
## clone the repository

## run the tests
1. tb_mirror_DPE_for_VCD (tb_mirror_DPE_golden_groupsize_16, tb_mirror_DPE_golden_groupsize_1)

    Mirror DPE is DPE + data mirroring, and provide tests for 2bit(groupsize=16) 8bit(groupsize=1) MX dotproduct. It compare HW_generated_result with real_fp_result and SW_golden_result

2. tb_MX_Converter_for_DPE (tb_MX_Converter_for_VCD)

    Convert a set of fps to MX format with groupsize 16

3. fp_Add_Test (FPAdd_no_minus_no, FPAdd_no_plus_no)

   Testcase for floating point adder.
4. FPMAC_TEST (FPMAC_TEST)

   Testcase for floating point MAC unit.
5. tb_INT8_PE (tb_INT8_PE)

    Testcase for INT8 MAC unit
6. BFP_Converter_DPE_TEST (BFP_TEST_DPE)

    Convert a set of fps to BFP format
7. tb_mirror_DPE_array

    Systolic array with BFP based DPE + data mirroring

   
You can run test in this directory.
```
sbt "testOnly *tb_mirror_DPE_golden_groupsize_1"
```

[2023.12.04]

For ISCA 2024, We develop the "DPE - flexible 3 types mx support + output stationary only" and "converter from fp32 to mx type"

These can be found in src/main/scala/PE/AS_FALL_DPE - labeled ISCA_hardware.

[file name]

Top module file names are "OS_only_mirror_DPE.scala" and "MX_Converter_for_DPE.scala"
