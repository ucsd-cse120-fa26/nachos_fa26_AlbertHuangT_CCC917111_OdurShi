/* Run with -m 20 (no child fits), or -m 32/64 (exhaust then reuse pages). */
#include "syscall.h"
#include "stdio.h"

int main(void) {
    int round, count, pid, i, status, capacity = -1;
    int children[16];
    char *args[1] = { "gate" };
    for (round = 0; round < 40; round++) {
        unlink("p3-gate.tmp");
        count = 0;
        while ((pid = exec("p3_child.coff", 1, args)) > 0 && count < 16)
            children[count++] = pid;
        if (pid != -1 || (capacity >= 0 && count != capacity)) {
            printf("P3 LOWMEM FAIL: capacity changed\n");
            return 1;
        }
        capacity = count;
        for (i = 0; i < 40; i++) {
            if (exec("p3_child.coff", 1, args) != -1) {
                printf("P3 LOWMEM FAIL: expected exec failure\n");
                return 1;
            }
        }
        pid = creat("p3-gate.tmp");
        if (pid < 0) {
            printf("P3 LOWMEM FAIL: leaked files\n");
            return 1;
        }
        close(pid);
        for (i = 0; i < count; i++) {
            if (join(children[i], &status) != 1 || status != 42) {
                printf("P3 LOWMEM FAIL: join\n");
                return 1;
            }
        }
        unlink("p3-gate.tmp");
    }
    printf("P3 LOWMEM PASS: 40 rounds, %d children per round\n", capacity);
    return 0;
}
