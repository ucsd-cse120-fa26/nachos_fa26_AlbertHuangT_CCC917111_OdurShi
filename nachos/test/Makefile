# 用来构建运行在 Nachos 之上的用户程序的 GNU Makefile
#
# 需要注意的事项：
#
#    在使用这个 makefile 之前，必须先设置 ARCHDIR 环境变量。如果你用的是
#	教学用机器，这一步应该会自动完成。但如果你用的不是教学用机器，
#	你需要把 ARCHDIR 指向交叉编译器所在的目录，例如
#		setenv ARCHDIR ../mips-x86.win32-xgcc

# 你需要指向正确的可执行文件
GCCDIR = $(ARCHDIR)/mips-

ASFLAGS = -mips1
CPPFLAGS =
CFLAGS = -O2 -B$(GCCDIR) -G 0 -Wa,-mips1 -nostdlib -ffreestanding -std=gnu99
LDFLAGS = -s -T script -N -warn-common -warn-constructors -warn-multiple-gp

CC = $(GCCDIR)gcc
AS = $(GCCDIR)as
LD = $(GCCDIR)ld
CPP = $(GCCDIR)cpp
AR = $(GCCDIR)ar
RANLIB = $(GCCDIR)ranlib

STDLIB_H = stdio.h stdlib.h ag.h
STDLIB_C = stdio.c stdlib.c
STDLIB_O = start.o stdio.o stdlib.o

LIB = assert atoi printf readline stdio strncmp strcat strcmp strcpy strlen memcpy memset rand
NLIB = libnachos.a

TARGETS = halt sh matmult sort echo cat cp mv rm write_basic create_basic read_basic fd_limits io_pages

.SECONDARY: $(patsubst %.c,%.o,$(wildcard *.c))

all: $(patsubst %,%.coff,$(TARGETS))

ag: grade-file.coff grade-exec.coff grade-mini.coff grade-dumb.coff

clean:
	rm -f strt.s *.o *.coff $(NLIB)

agclean: clean
	rm -f f1-* f2-*

$(NLIB): $(patsubst %,$(NLIB)(%.o),$(LIB)) start.o
	$(RANLIB) $(NLIB)

start.o: start.s syscall.h
	$(CPP) $(CPPFLAGS) start.s > strt.s
	$(AS) $(ASFLAGS) -o start.o strt.s
	rm strt.s

%.o: %.c *.h
	$(CC) $(CFLAGS) -c $<

%.coff: %.o $(NLIB)
	$(LD) $(LDFLAGS) -o $@ $< start.o -lnachos
