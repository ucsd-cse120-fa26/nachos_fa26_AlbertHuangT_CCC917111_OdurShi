#include "syscall.h"
#include "stdio.h"

int main(void) {
    char *args[1] = { "orphan" };
    if (exec("p3_child.coff", 1, args) < 0) {
        printf("P3 ORPHAN FAIL: exec\n");
        return 1;
    }
    printf("P3 parent exiting\n");
    return 0;
}
