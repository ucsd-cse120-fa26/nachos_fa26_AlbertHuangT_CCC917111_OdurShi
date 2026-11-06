package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;

/**
 * 一个可以支持多个用户进程的内核。
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * 分配（创建）一个新的 user kernel。
	 */
	public UserKernel() {
		super();
	}

	/**
	 * 初始化这个内核。创建一个同步的 console，并设置处理器的异常处理函数。
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		freePagesLock = new Lock();
		initFreePages();
		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * 测试 console 设备。
	 */
	public void selfTest() {
		super.selfTest();

		/* 默认跳过 console 测试，这样运行 Nachos 时就不用
		 * 输入 'q'。要使用这个测试，只要删掉下面的
		 * return 即可。 */
		if (true)
		    return;

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * 返回当前进程。
	 *
	 * @return 当前进程；如果当前没有进程，返回 <tt>null</tt>。
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * 异常处理函数。每当一条用户指令引起处理器异常时，处理器都会调用这个处理
	 * 函数。
	 *
	 * <p>
	 * 调用异常处理函数时，中断是开启的，处理器的 cause 寄存器中存着一个标识
	 * 异常原因的整数（参见 <tt>Processor</tt> 类中的 <tt>exceptionZZZ</tt>
	 * 系列常量）。如果异常涉及一个无效的虚拟地址（例如 page fault、TLB miss、
	 * read-only、bus error 或 address error），处理器的 BadVAddr 寄存器会指明
	 * 引起异常的虚拟地址。
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * 开始运行用户程序：创建一个进程，并在其中运行一个 shell 程序。它必须运行
	 * 的 shell 程序的名字由 <tt>Machine.getShellProgramName()</tt> 返回。
	 *
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
		    System.out.println ("UserProcess.execute failed trying to open and load executable '" +
					shellProgram + "',\ntrying '" +
					shellProgram + ".coff' instead.");
		    shellProgram += ".coff";
		    if (!process.execute(shellProgram, new String[] {})) {
			System.out.println ("Also failed on '" +
					    shellProgram + "', aborting.");
			Lib.assertTrue(false);
		    }

		}

		KThread.currentThread().finish();
	}

	/**
	 * 终止这个内核。永远不会返回。
	 */
	public void terminate() {
		super.terminate();
	}

	/** 全局可访问的同步 console 引用。 */
	public static SynchConsole console;

	// 一些 dummy 变量，让 javac 更"聪明"（顺带把这些类也编译进来）
	private static Coff dummy1 = null;

	// privete static linked array to track free pages
	private static LinkedList<Integer> freePages;
	// Lock to protect freePages
	private static Lock freePagesLock;

	// init free pages
	public static void initFreePages(){
	    freePages = new LinkedList<Integer>();
        for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
            freePages.add(i);
        }
	}
	
	// distribute freePages
	public static int[] allocatePages(int need){
	    freePagesLock.acquire();
		if(need > freePages.size()){
		    freePagesLock.release();
		    return null;
		}
		int[] allocatedPages = new int[need];
		for(int i = 0; i < need; i++){
		    allocatedPages[i] = freePages.removeFirst();
		}
		freePagesLock.release();
		return allocatedPages;
	}
	
	// free usedPages
	public static void releasePages(TranslationEntry[] pageTable){
	    freePagesLock.acquire();
	    if(pageTable == null){
			freePagesLock.release();
			return;
		}

		for(int i = 0; i < pageTable.length; i++){
		    freePages.add(pageTable[i].ppn);
		}
		freePagesLock.release();
		return;
	}
}
