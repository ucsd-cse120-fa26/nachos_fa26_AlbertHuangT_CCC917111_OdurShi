package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 封装一个用户进程中不属于其用户线程的那部分状态。这包括它的地址翻译状态、
 * 文件表，以及正在执行的程序的相关信息。
 *
 * <p>
 * 其他类会继承这个类，以支持更多功能（例如更多的 syscall）。
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {

	private OpenFile[] fileTable = new OpenFile[16];
	private static int nextPid = 0;
	private static int liveProcesses = 0;
	private final int pid;
	// Only the parent accesses this map; exited children remain until joined.
	private final HashMap<Integer, UserProcess> children = new HashMap<Integer, UserProcess>();
	private int exitStatus;
	private boolean abnormalExit;

	/**
	 * 分配（创建）一个新进程。
	 */
	public UserProcess() {
		boolean intStatus = Machine.interrupt().disable();
		pid = nextPid++;
		Machine.interrupt().restore(intStatus);

		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
	}



	/**
	 * 分配并返回一个正确类型的新进程。类名由 <tt>nachos.conf</tt> 中的 key
	 * <tt>Kernel.processClassName</tt> 指定。
	 *
	 * @return 一个正确类型的新进程。
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// 如果使用 Lib.constructObject，很快就会耗尽文件描述符，
		// 并在 createClassLoader 中抛出异常。这里用一个变通办法：
		// 把创建相应类型新进程的代码写死。

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * 用指定的参数执行指定的程序。尝试加载这个程序，然后 fork 一个线程来
	 * 运行它。
	 *
	 * @param name 包含可执行文件的文件名。
	 * @param args 要传给可执行文件的参数。
	 * @return 如果程序成功执行，返回 <tt>true</tt>。
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		boolean intStatus = Machine.interrupt().disable();
		liveProcesses++;
		Machine.interrupt().restore(intStatus);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * 保存这个进程的状态，为 context switch 做准备。由
	 * <tt>UThread.saveState()</tt> 调用。
	 */
	public void saveState() {
	}

	/**
	 * 在 context switch 之后恢复这个进程的状态。由
	 * <tt>UThread.restoreState()</tt> 调用。
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * 从这个进程的虚拟内存中读取一个以 null 结尾的字符串。从指定地址最多读取
	 * <tt>maxLength + 1</tt> 个字节，查找 null 结束符，然后把它转换成一个
	 * <tt>java.lang.String</tt>（不包括 null 结束符）。如果没有找到 null
	 * 结束符，返回 <tt>null</tt>。
	 *
	 * @param vaddr 以 null 结尾的字符串的起始虚拟地址。
	 * @param maxLength 字符串的最大字符数，不包括 null 结束符。
	 * @return 读到的字符串；如果没有找到 null 结束符，返回 <tt>null</tt>。
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * 把数据从这个进程的虚拟内存传输到整个指定数组中。等同于
	 * <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>。
	 *
	 * @param vaddr 要读取的虚拟内存的第一个字节。
	 * @param data 用来存放数据的数组。
	 * @return 成功传输的字节数。
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * 把数据从这个进程的虚拟内存传输到指定数组中。这个方法负责处理地址翻译的
	 * 细节。出错时，这个方法<i>不能</i>销毁当前进程，而应该返回成功复制的字节数
	 * （如果一个字节都无法复制，就返回零）。
	 *
	 * @param vaddr 要读取的虚拟内存的第一个字节。
	 * @param data 用来存放数据的数组。
	 * @param offset 数组中要写入的第一个字节。
	 * @param length 要从虚拟内存传输到数组的字节数。
	 * @return 成功传输的字节数。
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int currentVirtualAddress = vaddr;
		int dataIndex = offset;
		int copied = 0;

		while(copied < length){
		    int virtualPageNumber = Processor.pageFromAddress(currentVirtualAddress);
			int pageOffset = Processor.offsetFromAddress(currentVirtualAddress);

			if((virtualPageNumber < 0) || (virtualPageNumber >= pageTable.length) || !pageTable[virtualPageNumber].valid){
			    break;
			}

			int amount = Math.min(length - copied, pageSize - pageOffset);
			int currentPhysicalAddress = currentVirtualAddress + (pageTable[virtualPageNumber].ppn - virtualPageNumber) * pageSize;
			System.arraycopy(memory, currentPhysicalAddress, data, dataIndex, amount);
            copied += amount;
            currentVirtualAddress += amount;
            dataIndex += amount;
		}

		return copied;
	}

	/**
	 * 把指定数组中的全部数据传输到这个进程的虚拟内存中。等同于
	 * <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>。
	 *
	 * @param vaddr 要写入的虚拟内存的第一个字节。
	 * @param data 包含要传输的数据的数组。
	 * @return 成功传输的字节数。
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * 把数据从指定数组传输到这个进程的虚拟内存中。这个方法负责处理地址翻译的
	 * 细节。出错时，这个方法<i>不能</i>销毁当前进程，而应该返回成功复制的字节数
	 * （如果一个字节都无法复制，就返回零）。
	 *
	 * @param vaddr 要写入的虚拟内存的第一个字节。
	 * @param data 包含要传输的数据的数组。
	 * @param offset 数组中要传输的第一个字节。
	 * @param length 要从数组传输到虚拟内存的字节数。
	 * @return 成功传输的字节数。
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int currentVirtualAddress = vaddr;
		int dataIndex = offset;
		int wrote = 0;

		while(wrote < length){
		    int virtualPageNumber = Processor.pageFromAddress(currentVirtualAddress);
			int pageOffset = Processor.offsetFromAddress(currentVirtualAddress);

			if((virtualPageNumber < 0) || (virtualPageNumber >= pageTable.length) || !pageTable[virtualPageNumber].valid || pageTable[virtualPageNumber].readOnly){
			    break;
			}

			int amount = Math.min(length - wrote, pageSize - pageOffset);
			int currentPhysicalAddress = currentVirtualAddress + (pageTable[virtualPageNumber].ppn - virtualPageNumber) * pageSize;
			System.arraycopy(data, dataIndex, memory, currentPhysicalAddress, amount);
            wrote += amount;
            currentVirtualAddress += amount;
            dataIndex += amount;
		}

		return wrote;
	}

	/**
	 * 把指定名字的可执行文件加载到这个进程中，并准备把指定的参数传给它。打开
	 * 可执行文件，读取它的 header 信息，然后把各个 section 和参数复制到这个
	 * 进程的虚拟内存中。
	 *
	 * @param name 包含可执行文件的文件名。
	 * @param args 要传给可执行文件的参数。
	 * @return 如果可执行文件被成功加载，返回 <tt>true</tt>。
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// 确保各个 section 是连续的，并且从第 0 页开始
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getLength() < 0){
				coff.close();
				return false;
			}
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// 确保 argv 数组能放进一页
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// argv[] 指针占 4 字节；然后是字符串本身，再加 1 字节的 null
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// 程序计数器一开始指向程序的入口点
		initialPC = coff.getEntryPoint();

		// 接下来是栈；栈指针一开始指向栈顶
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// 最后再为参数预留 1 页
		numPages++;

		Lib.debug(dbgProcess, "UserProcess.load: " + numPages + " pages in address space (" + Machine.processor().getNumPhysPages() + " physical pages)");

		/*
		 * Nachos 用户进程地址空间的布局。
		 * 上面的代码计算了这个可执行文件的地址空间
		 * 总共需要多少页。
		 *
		 * +--------------------+
		 * | 来自可执行文件     |   大小 = COFF 文件中的页数
		 * | （COFF 文件）的    |
		 * | 代码和数据页       |
		 * +--------------------+
		 * | 栈页 (stack pages) |   大小 = stackPages
		 * +--------------------+
		 * | 参数页 (arg page)  |   大小 = 1
		 * +--------------------+
		 *
		 * 第 0 页在最上面，最下面的最后一页是参数页，
		 * 也就是第 numPages-1 页。
		 */

		if (!loadSections()) {
			Lib.debug(dbgProcess, "\tloadSections failed");
			return false;
		}

		// 把参数存到最后一页
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * 为这个进程分配内存，并把 COFF 的各个 section 加载到内存中。如果这个方法
	 * 成功返回，这个进程就一定会运行（这是进程初始化过程中最后一个可能失败的
	 * 步骤）。
	 *
	 * @return 如果各个 section 被成功加载，返回 <tt>true</tt>。
	 */
	protected boolean loadSections() {
	    int[] allocatedPages = UserKernel.allocatePages(numPages);
		if (allocatedPages == null) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
            pageTable[i] = new TranslationEntry(i, allocatedPages[i], true, false, false, false);
        }
		// 加载各个 section
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// 暂时先假定虚拟地址 = 物理地址
				section.loadPage(i, allocatedPages[vpn]);

				pageTable[vpn].readOnly = section.isReadOnly();
			}
		}

		return true;
	}

	/**
	 * 释放 <tt>loadSections()</tt> 分配的所有资源。
	 */
	protected void unloadSections() {
	    UserKernel.releasePages(pageTable);
		pageTable = null;
	}

	/**
	 * 初始化处理器的寄存器，为运行加载到这个进程中的程序做准备。把 PC 寄存器
	 * 设为指向起始函数，把栈指针寄存器设为指向栈顶，把 A0 和 A1 寄存器分别设为
	 * argc 和 argv，并把其他所有寄存器初始化为 0。
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// 默认情况下，所有寄存器都是 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// 相应地初始化 PC 和 SP
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// 把前两个参数寄存器初始化为 argc 和 argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	private int handleCreate(int nameAddress) {
		String name = readVirtualMemoryString(nameAddress, 256);
		if (name == null)
			return -1;
		
		for (int fd = 0; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				OpenFile file = ThreadedKernel.fileSystem.open(name, true);
				if (file == null)
				    return -1;

				fileTable[fd] = file;
				return fd;
			}
		}
		return -1;
	}

	private int handleWrite(int fd, int bufferAddress, int count) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null) {
			return -1;
		}
		if (count < 0) {
			return -1;
		}

		if (count == 0) {
			return 0;
		}

		byte[] buffer = new byte[Math.min(count, pageSize)];
		int total = 0;
		while (total < count) {
			int amount = Math.min(buffer.length, count - total);
			int copied = readVirtualMemory(bufferAddress + total, buffer, 0, amount);
			if (copied != amount) {
				return -1;
			}
			int written = fileTable[fd].write(buffer, 0, amount);
			if (written != amount) {
				return -1;
			}
			total += written;
		}

		return total;
	}

	private int handleRead(int fd, int bufferAddress, int count) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null) {
			return -1;
		}
		if (count < 0) {
			return -1;
		}

		if (count == 0) {
			return 0;
		}

		byte[] buffer = new byte[Math.min(count, pageSize)];
		int total = 0;
		while (total < count) {
			int amount = Math.min(buffer.length, count - total);
			int bytesRead = fileTable[fd].read(buffer, 0, amount);
			if (bytesRead < 0)
				return -1;
			if (bytesRead == 0)
				break;
			
			int copied = writeVirtualMemory(bufferAddress + total, buffer, 0, bytesRead);
			if (copied != bytesRead) 
				return -1;	

			total += bytesRead;

			if (bytesRead < amount) 
				break;
			
		}

		return total;
	}

	private int handleClose(int fd) {
		if (fd < 0 || fd >= fileTable.length || fileTable[fd] == null) {
			return -1;
		}
		fileTable[fd].close();
		fileTable[fd] = null;
		return 0;
	}

	private int handleOpen(int nameAddress) {
		String name = readVirtualMemoryString(nameAddress, 256);
		if (name == null) {
			return -1;
		}
		for (int fd = 0; fd < fileTable.length; fd++) {
			if (fileTable[fd] == null) {
				OpenFile file = ThreadedKernel.fileSystem.open(name, false);
				if (file == null) {
					return -1;
				}
				fileTable[fd] = file;
				return fd;
			}
		}
		return -1;	
	}

	private int handleUnlink(int nameAddress) {
		String name = readVirtualMemoryString(nameAddress, 256);
		if (name == null) {
			return -1;
		}
		boolean removed = ThreadedKernel.fileSystem.remove(name);

		if (removed)
		    return 0;
		return -1;
	}

	/**
	 * 处理 halt() 系统调用。
	 */
	private int handleHalt() {
		if (pid != 0)
			return -1;

		Lib.debug(dbgProcess, "UserProcess.handleHalt");

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * 处理 exit() 系统调用。
	 */
	private int handleExit(int status) {
	        // 不要删除这个对 autoGrader 的调用……
		Machine.autoGrader().finishingCurrentProcess(status);
		// ……并且要让它保持为 handleExit 的第一条语句，
		// 这样我们才能给你的实现评分。

		exitStatus = status;
		closeFiles();
		unloadSections();
		if (coff != null) {
			coff.close();
			coff = null;
		}
		children.clear();

		boolean intStatus = Machine.interrupt().disable();
		boolean lastProcess = --liveProcesses == 0;
		Machine.interrupt().restore(intStatus);
		if (lastProcess)
			Kernel.kernel.terminate();
		KThread.finish();

		return 0;
	}

	/** Close descriptors on exit, or when a newly created child cannot load. */
	private void closeFiles() {
		for (int fd = 0; fd < fileTable.length; fd++) {
			if (fileTable[fd] != null) {
				fileTable[fd].close();
				fileTable[fd] = null;
			}
		}
	}

	private int handleExec(int file, int argc, int argv) {
		// Even empty arguments require four pointer bytes and a terminator.
		if (file == 0 || argc < 0 || argc > pageSize / 5)
			return -1;
		String name = readVirtualMemoryString(file, 256);
		if (name == null || !name.endsWith(".coff"))
			return -1;
		if (argc > 0 && argv == 0)
			return -1;

		ArrayList<String> args = new ArrayList<String>();
		byte[] pointer = new byte[4];
		int argumentBytes = 0;
		for (int i = 0; i < argc; i++) {
			long entryAddress = (long) argv + 4L * i;
			if (entryAddress < 0 || entryAddress > Integer.MAX_VALUE - 3
					|| readVirtualMemory((int) entryAddress, pointer) != 4)
				return -1;
			int address = Lib.bytesToInt(pointer, 0);
			if (address == 0)
				return -1;
			String arg = readVirtualMemoryString(address, 256);
			if (arg == null)
				return -1;
			argumentBytes += 4 + arg.getBytes().length + 1;
			if (argumentBytes > pageSize)
				return -1;
			args.add(arg);
		}

		UserProcess child = UserProcess.newUserProcess();
		// Register before fork: the child may exit before execute returns.
		children.put(child.pid, child);
		if (!child.execute(name, args.toArray(new String[args.size()]))) {
			children.remove(child.pid);
			// load already closes the executable on failure; no thread was forked.
			child.closeFiles();
			return -1;
		}
		return child.pid;
	}

	/** Validate the whole status word without modifying user memory. */
	private boolean writableStatus(int address) {
		long lastAddress = (long) address + 3;
		if (address < 0 || lastAddress > Integer.MAX_VALUE || pageTable == null)
			return false;
		int firstPage = Processor.pageFromAddress(address);
		int lastPage = Processor.pageFromAddress((int) lastAddress);
		if (lastPage >= pageTable.length)
			return false;
		for (int vpn = firstPage; vpn <= lastPage; vpn++) {
			TranslationEntry entry = pageTable[vpn];
			if (entry == null || !entry.valid || entry.readOnly
					|| entry.ppn < 0 || entry.ppn >= Machine.processor().getNumPhysPages())
				return false;
		}
		return true;
	}

	private int handleJoin(int childPid, int status) {
		UserProcess child = children.get(childPid);
		if (child == null || (status != 0 && !writableStatus(status)))
			return -1;

		child.thread.join();
		children.remove(childPid);
		if (child.abnormalExit)
			return 0;
		if (status != 0 && writeVirtualMemory(status, Lib.bytesFromInt(child.exitStatus)) != 4)
			return -1;
		return 1;
	}

	private static final int syscallHalt = 0,
			/* 进程相关的 syscall */
			syscallExit = 1, syscallExec = 2, syscallJoin = 3,
			/* 文件相关的 syscall */
			syscallCreate = 4, syscallOpen = 5, syscallRead = 6,
			syscallWrite = 7, syscallClose = 8, syscallUnlink = 9,
			/* 信号相关的 syscall（可选） */
			syscallSignal = 13, syscallSigreturn = 14,
			syscallAlarm = 15;

	/**
	 * 处理一个 syscall 异常。由 <tt>handleException()</tt> 调用。参数
	 * <i>syscall</i> 指明用户执行的是哪个 syscall：
	 *
	 * <table>
	 * <tr>
	 * <td>syscall 编号</td>
	 * <td>syscall 原型</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 *
	 * @param syscall syscall 编号。
	 * @param a0 syscall 的第一个参数。
	 * @param a1 syscall 的第二个参数。
	 * @param a2 syscall 的第三个参数。
	 * @param a3 syscall 的第四个参数。
	 * @return 要返回给用户的值。
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallCreate:
			return handleCreate(a0);
		case syscallClose:
			return handleClose(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			return -1;
		}
	}

	/**
	 * 处理一个用户异常。由 <tt>UserKernel.exceptionHandler()</tt> 调用。参数
	 * <i>cause</i> 指明发生的是哪种异常；参见 <tt>Processor.exceptionZZZ</tt>
	 * 系列常量。
	 *
	 * @param cause 发生的用户异常。
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			abnormalExit = true;
			handleExit(-1);
		}
	}

	/** 这个进程正在运行的程序。 */
	protected Coff coff;

	/** 这个进程的页表。 */
	protected TranslationEntry[] pageTable;

	/** 程序占用的连续页数。 */
	protected int numPages;

	/** 程序栈的页数。 */
	protected final int stackPages = 8;

	/** 执行用户级程序的线程。 */
        protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
}
