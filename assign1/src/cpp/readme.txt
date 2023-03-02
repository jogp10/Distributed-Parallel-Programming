To be able to read performance counters on LINUX, you need to edit the following file:
sudo sh -c 'echo -1 >/proc/sys/kernel/perf_event_paranoid'

compilar com: g++ -O2 matrixproduct.cpp -o fileout -lpapi 
