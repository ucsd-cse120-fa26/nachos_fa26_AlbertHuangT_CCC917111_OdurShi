/* Part 3 regression helper. Modes are selected by the parent test. */
#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char **argv) {
    int i, fd;
    volatile int delay;
    if (argc == 0) return 37;
    if (!strcmp(argv[0], "args")) {
        return argc == 4 && !strcmp(argv[1], "") &&
            !strcmp(argv[2], "hello world") && !strcmp(argv[3], "last") ? 42 : -42;
    }
    if (!strcmp(argv[0], "status")) {
        /* The supplied atoi does not parse a '-' sign. */
        return argv[1][0] == '-' ? -atoi(argv[1] + 1) : atoi(argv[1]);
    }
    if (!strcmp(argv[0], "crash")) return *(volatile int *)0x7ffffffc;
    if (!strcmp(argv[0], "readonly")) {
        *(volatile int *)main = 0;
        return -42;
    }
    if (!strcmp(argv[0], "halt")) {
        halt();
        return 42;
    }
    if (!strcmp(argv[0], "gate")) {
        do { fd = open("p3-gate.tmp"); } while (fd < 0);
        close(fd);
        return 42;
    }
    if (!strcmp(argv[0], "grandchild")) return exec("p3_child.coff", 0, 0);
    if (!strcmp(argv[0], "fds")) {
        if (close(2) != -1 || join(0, 0) != -1) return -42;
        for (i = 0; i < 14; i++) if (open("p3_child.coff") != i + 2) return -42;
        /* Deliberately leave all descriptors open to exercise exit cleanup. */
        return 42;
    }
    if (!strcmp(argv[0], "orphan")) {
        for (delay = 0; delay < 100000; delay++);
        halt(); /* An orphan must not acquire root privileges. */
        printf("P3 ORPHAN PASS: child survived parent exit\n");
        return 42;
    }
    return 42;
}
