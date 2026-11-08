/* End-to-end tests for process lifecycle, ownership and hostile arguments. */
#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

static int checks;
static char boundary[3072];
static char longarg[258];
static char *manyargs[205];

static void check(int ok, char *message) {
    if (!ok) {
        printf("P3 FAIL: %s\n", message);
        exit(1);
    }
    checks++;
}

static int spawn(char *mode) {
    char *args[1];
    args[0] = mode;
    return exec("p3_child.coff", 1, args);
}

static void expect(int pid, int value) {
    int status = 999;
    check(pid > 0, "exec succeeds");
    check(join(pid, &status) == 1 && status == value, "normal child status");
    check(join(pid, &status) == -1, "second join rejected");
}

int main(int argc, char **argv) {
    int pid, other, status, fd, i, previous = 0, pointer, result;
    int top = (int)argv + 1024;
    char *args[4] = { "args", "", "hello world", "last" };
    char *edge = (char *)((((int)boundary + 1023) / 1024) * 1024 + 1022);
    char *statusargs[2] = { "status", "-1" };

    check(join(0, 0) == -1 && join(-1, 0) == -1 && join(99999, 0) == -1,
          "only direct children can be joined");
    check(exec(0, 0, 0) == -1, "null executable name");
    check(exec((char *)-1, 0, 0) == -1, "negative executable address");
    check(exec((char *)0x7fffffff, 0, 0) == -1, "high executable address");
    check(exec("p3_child", 0, 0) == -1, "coff suffix required");
    check(exec("p3_child.coff", -1, 0) == -1, "negative argc");
    check(exec("p3_child.coff", 0x7fffffff, 0) == -1, "huge argc");
    check(exec("p3_child.coff", 1, 0) == -1, "null argv");
    check(exec("p3_child.coff", 1, (char **)-1) == -1, "negative argv");
    check(exec("p3_child.coff", 1, (char **)0x7ffffffe) == -1, "overflowing argv");
    check(exec("p3_child.coff", 1, (char **)(top - 2)) == -1, "partial argv word");
    manyargs[0] = (char *)-1;
    check(exec("p3_child.coff", 1, manyargs) == -1, "invalid argument string");
    manyargs[0] = 0;
    check(exec("p3_child.coff", 1, manyargs) == -1, "null argument string");
    memset(longarg, 'a', 257);
    longarg[257] = 0;
    manyargs[0] = longarg;
    check(exec("p3_child.coff", 1, manyargs) == -1, "257-byte argument rejected");
    longarg[256] = 0;
    expect(exec("p3_child.coff", 1, manyargs), 42);
    for (i = 0; i < 205; i++) manyargs[i] = "";
    expect(exec("p3_child.coff", 204, manyargs), 42);
    check(exec("p3_child.coff", 205, manyargs) == -1, "argument page overflow");
    for (i = 0; i < 4; i++) manyargs[i] = longarg;
    longarg[251] = 0; /* Four times (4 + 251 + 1) = exactly 1024 bytes. */
    expect(exec("p3_child.coff", 4, manyargs), 42);
    longarg[251] = 'a';
    longarg[252] = 0;
    check(exec("p3_child.coff", 4, manyargs) == -1, "aggregate argument overflow");

    expect(exec("p3_child.coff", 0, (char **)-1), 37);
    expect(exec("p3_child.coff", 4, args), 42);
    expect(exec("p3_child.coff", 2, statusargs), -1);
    strcpy(edge, "p3_child.coff");
    expect(exec(edge, 0, 0), 37); /* Filename crosses a page boundary. */
    pointer = (int)"halt";
    memcpy(edge, &pointer, 4);
    expect(exec("p3_child.coff", 1, (char **)edge), 42);

    /* The child cannot finish until the parent creates the gate file.
       An implementation that waits before validating status deadlocks here. */
    unlink("p3-gate.tmp");
    pid = spawn("gate");
    check(pid > 0, "start blocked child");
    check(join(pid, (int *)-1) == -1, "negative join status returns immediately");
    check(join(pid, (int *)0x7ffffffe) == -1, "join status address overflow");
    check(join(pid, (int *)main) == -1, "read-only join status");
    check(join(pid, (int *)(top - 2)) == -1, "partially invalid join status");
    fd = creat("p3-gate.tmp");
    check(fd >= 0, "release gate child");
    close(fd);
    check(join(pid, (int *)edge) == 1, "cross-page status write");
    memcpy(&status, edge, 4);
    check(status == 42, "cross-page status bytes");
    check(join(pid, 0) == -1, "child consumed only by successful join");
    unlink("p3-gate.tmp");

    pid = spawn("crash");
    status = 555;
    check(pid > 0 && join(pid, &status) == 0, "exceptional child join");
    check(join(pid, &status) == -1, "exceptional child consumed");
    pid = spawn("readonly");
    check(pid > 0 && join(pid, 0) == 0, "write to code exits abnormally");
    expect(spawn("halt"), 42);
    pid = exec("p3_child.coff", 0, 0);
    check(pid > 0 && join(pid, 0) == 1, "null status accepted");

    pid = spawn("grandchild");
    check(pid > 0 && join(pid, &status) == 1 && status > 0, "grandchild created");
    check(join(status, 0) == -1, "grandchild cannot be joined by grandparent");

    /* Multiple live children have private address spaces and independent status. */
    pid = exec("p3_child.coff", 4, args);
    other = exec("p3_child.coff", 2, statusargs);
    expect(other, -1);
    expect(pid, 42);

    fd = open("p3_child.coff");
    check(fd == 2, "parent descriptor allocated");
    for (i = 0; i < 40; i++) {
        pid = spawn("fds");
        check(pid > previous, "PIDs increase across exited processes");
        previous = pid;
        expect(pid, 42);
    }
    check(close(fd) == 0, "child does not close parent descriptor");
    /* Repetition exceeds both physical memory and the stub's 32-open-file limit
       if either successful or failed process creation leaks resources. */
    fd = creat("p3-invalid.coff");
    check(fd >= 0 && write(fd, "not coff", 8) == 8, "make invalid executable");
    close(fd);
    for (i = 0; i < 80; i++) {
        check(exec("p3-missing.coff", 0, 0) == -1, "missing executable cleanup");
        check(exec("p3-invalid.coff", 0, 0) == -1, "invalid executable cleanup");
        expect(exec("p3_child.coff", 0, 0), 37);
    }
    unlink("p3-invalid.coff");
    for (i = 0; i < 40; i++) {
        pid = spawn("crash");
        check(pid > 0 && join(pid, 0) == 0, "abnormal exit releases pages and coff");
    }
    /* An unknown syscall must fail without crashing the kernel. */
    __asm__ volatile("li $2, 99\n\tsyscall\n\tmove %0, $2"
                     : "=r"(result) : : "$2", "memory");
    check(result == -1, "unknown syscall rejected");
    printf("P3 PASS: %d checks\n", checks);
    return 0;
}
