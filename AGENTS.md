# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Ground rules: this is graded coursework

This repo is a CSE 120 assignment. The student must write every line of the solution themselves. Claude acts as a tutor: it helps the student understand the code and guides them through the projects, but it does not do the work for them.

**Never modify files.** Do not create, edit, rename, or delete any file in this repo: Java sources, Makefiles, `nachos.conf`, test programs, or anything else. Do not run commands that change files either, such as `sed -i`, redirects into repo files, `git checkout`/`restore`/`stash`/`commit`/`push`, or formatters. The only exception is this `CLAUDE.md`, and only when the student explicitly asks for a change to it. If a change to the code seems needed, describe it in words and let the student make it.

**Exception: comment-only edits.** When the student explicitly asks, Claude may translate existing comments (for example, into Chinese) in repo files. Code must stay byte-identical: identifiers, string literals, logic, and code lines are not touched. Verify this by diffing the files with comments stripped and by rebuilding. Before overwriting a file, read its current contents so the student's own uncommitted edits are kept. The student has said this also applies to files marked `// PART OF THE MACHINE SIMULATION. DO NOT CHANGE.` (machine/, ag/, security/), because grading uses its own copies of them and their code stays identical; keep that marker line itself in English. Comments must never contain solution code or step-by-step pseudocode.

**Allowed:** reading files, `grep`/`find`, `git log`/`diff`/`blame`, and running `make` or `java nachos.machine.Machine` to observe behavior. Those commands write only build outputs (`.class`, `.o`, `.coff`), not source.

**How to help:**
- Explain how the existing code works: trace call paths, explain what a class or method is for, and point to `file:line`.
- Explain the OS concepts behind each task, such as synchronization, condition variables, priority donation, page tables, syscalls, and demand paging.
- Help the student read the spec: what is being asked, the edge cases, and what the autograder is likely to check.
- Talk through the student's design at the level of what state is needed, which invariants must hold, and where races can occur. Prefer guiding questions and hints over answers.
- Review code the student wrote: point to the line, explain what goes wrong and why, and let the student write the fix.
- Explain error messages, stack traces, and debug output (`-d` flags), and suggest test scenarios worth trying.

**Do not:**
- Write code that implements any part of an assignment, whether in a file or in chat. This includes complete methods, "fixed" versions of the student's code, and pseudocode detailed enough to transcribe line by line.
- Write the student's test programs or `selfTest()` cases for them. Describe what to test instead.

## What this is

Nachos 5.0j (Java), the instructional OS used in UCSD CSE 120 (Fall 2026). Students implement kernel features across projects in `nachos/threads`, `nachos/userprog`, and `nachos/vm`. Project specs live on the course site: https://cseweb.ucsd.edu/classes/fa26/cse120-001/projects/index.html

**Do not change the directory structure.** The top-level `nachos/` directory must stay where it is and keep that exact name. The build uses `-sourcepath ../..` and resolves packages as `nachos.*` from the parent of `nachos/`, so a renamed copy will not compile.

**Do not edit** files marked `// PART OF THE MACHINE SIMULATION. DO NOT CHANGE.`: everything in `machine/`, `ag/AutoGrader.java`, and `security/`. Grading uses its own copies of these files.

## Build and run

Builds are per project. Run them from `nachos/projN/`, where N is 0–3. Each `projN/Makefile` sets `DIRS` (the packages it compiles) and includes `nachos/Makefile`.

```bash
cd nachos/proj1 && make            # compiles .class files into nachos/proj1/nachos/
cd nachos/proj1 && make clean      # removes this project's .class files
```

Run Nachos from inside the project directory. It reads `./nachos.conf` and uses `../test` as the user-program file system:

```bash
cd nachos/proj1 && java nachos.machine.Machine
```

- `nachos/bin/nachos` (the `nachos` command) wraps this call with `stty` raw-mode setup. That setup fails without a TTY. In non-interactive shells, call `java nachos.machine.Machine` directly.
- Flags: `-d <flags>` turns on debug output (`t` thread, `a` process, `c` COFF, `i` interrupt, `p` processor, `m`/`M` disassembly, `v` VM in vm/). `-x <prog.coff>` sets the user program to run (overrides `Kernel.shellProgram`). `-s <seed>` sets the RNG seed. `-m <pages>` sets the number of physical pages. `-[] <conf>` uses a different config file. `-- <class>` / `-# <args>` select an autograder and pass it arguments.
- There is no test framework. "Tests" are code you call from a kernel's `selfTest()`, for example `KThread.selfTest()` called by `ThreadedKernel.selfTest()`, plus the MIPS C programs in `nachos/test/`. To run one test, call it from `selfTest()`, or pass `-x <prog>.coff` for proj2/3.

### MIPS user programs (proj2/3)

`nachos/test/*.c` is cross-compiled to `.coff` files with the course MIPS toolchain. `ARCHDIR` must point to that toolchain, and `ARCHDIR` must also be on `PATH`, or `mips-gcc` cannot find `cc1`. The course module (`$PUBLIC` = `/home/linux/ieng6/CSE120_FA26_001/public`) sets both. In a non-login shell, set them by hand:

```bash
export ARCHDIR=/home/linux/ieng6/CSE120_FA26_001/public/mips-x86.linux-xgcc PATH=/home/linux/ieng6/CSE120_FA26_001/public/mips-x86.linux-xgcc:$PATH && cd nachos/test && make
```

To build a new test program, add it to `TARGETS` in `nachos/test/Makefile`. `.gitignore` covers `.class` files but not `.o`, `.coff`, or `libnachos.a`, so don't commit those build outputs. Files that user programs create land in `nachos/test/`.

## Build-system gotchas

- `make` compiles only the classes listed in the per-package variables in `nachos/Makefile` (`threads =`, `userprog =`, `vm =`, …). Each `.class` depends only on its own `.java`. Consequences:
  - Add each new class to its package list, or make sure a listed class references it. Some kernels hold `// dummy variables to make javac smarter` fields for this purpose. `Communicator`, `Boat`, and `SquadMatch` are commented out of the list.
  - Changing a class's interface does not rebuild the classes that depend on it. If you see stale-class weirdness, run `make clean && make`.
- The JDK on ieng6 is Java 21. The Java SecurityManager is not enabled (`Machine.main` comments out `securityManager.enable()`).

## Architecture

**Startup.** `Machine.main` parses flags and loads `nachos.conf` (`Config`). It then creates the simulated devices that the conf enables: interrupt controller, timer, MIPS `Processor`, console, and `StubFileSystem` over `../test`. Next it starts `AutoGrader`, which instantiates the kernel class named by `Kernel.kernel` and calls `initialize()` → `selfTest()` → `run()` → `terminate()`.

**Kernel hierarchy.** Each project's conf selects one kernel in this chain:
- `threads.ThreadedKernel` (proj0/1): builds the scheduler named by `ThreadedKernel.scheduler`, the first `KThread` plus the idle thread, and the `Alarm`. `run()` does nothing.
- `userprog.UserKernel` (proj2): adds `SynchConsole` and installs the processor exception handler. `run()` creates a process with `UserProcess.newUserProcess()` and executes the shell program.
- `vm.VMKernel` (proj3): extends UserKernel for demand paging. The conf sets `Kernel.processClassName = nachos.vm.VMProcess`. `UserProcess.newUserProcess()` hard-codes `UserProcess` and `VMProcess` rather than using reflection, to avoid running out of file descriptors.

**Threads and concurrency.** Each `KThread` is backed by a real Java thread (`TCB`), but only one runs at a time. Context switches happen in `KThread.run()`/`tcb.contextSwitch()`, through `yield()`, `sleep()`, and `finish()`. Kernel code gets atomicity by disabling interrupts: `boolean s = Machine.interrupt().disable(); ... Machine.interrupt().restore(s);`. `KThread.sleep()` and `ready()` assert that interrupts are disabled. Locks and semaphores block threads through `ThreadQueue`s from the active `Scheduler`. `waitForAccess` and `nextThread` are where `PriorityScheduler`/`LotteryScheduler` hook in.

**Simulated time.** Time is counted in ticks (`Machine.timer().getTime()`). Kernel ticks advance only when interrupts go from disabled to enabled, and user ticks advance per MIPS instruction. A pure-Java loop in kernel code therefore never advances time and never gets preempted. The timer fires roughly every 500 ticks into `Alarm.timerInterrupt()`, which must keep its `KThread.currentThread().yield()` call.

**User processes.** `UserProcess.load()` parses the COFF executable and lays out the address space: code/data pages, then `stackPages` (8) stack pages, then one page for argv. In the stub, virtual addresses equal physical addresses and every process gets the whole of memory. Replacing that identity mapping with real per-process page tables is the proj2 work. All user-memory access must go through `readVirtualMemory`/`writeVirtualMemory`. These must return a partial byte count on a bad address rather than kill the process. A `UThread` (a `KThread` subclass) runs `Machine.processor().run()`. On a context switch it saves and restores user registers and calls `process.saveState()`/`restoreState()`, which installs the page table. Syscalls arrive as `Processor.exceptionSyscall` → `UserKernel.exceptionHandler()` → `UserProcess.handleException()` → `handleSyscall(v0, a0..a3)`. The return value is written to `v0`. Syscall numbers and the C-side contracts are in `nachos/test/syscall.h`.

**Grading hooks you must preserve:**
- `Machine.autoGrader().finishingCurrentProcess(status)` must remain the first statement of `UserProcess.handleExit`.
- The `yield()` call in `Alarm.timerInterrupt` must remain.
- When grading, `ElevatorBank.allowElevatorGUI = false` and `NachosSecurityManager.fullySecure = true` are set.

**Per-project config differences** (`projN/nachos.conf`):
- proj0/1: no processor or console.
- proj2: `Processor.usingTLB = false`, 64 physical pages, shell `halt.coff`.
- proj3: still `usingTLB = false` (demand paging through the page table, not a TLB), only 16 physical pages, shell `sh.coff`.

There is no `proj4` directory, and no project's `DIRS` includes `network/` (NetKernel).

## Debugging helpers

- `Lib.debug(flagChar, msg)` prints only when that flag is passed with `-d`.
- `Lib.assertTrue(cond[, msg])` and `Lib.assertNotReached()` halt Nachos with a stack trace.
- An unimplemented syscall hits `assertNotReached("Unknown system call!")`.
