package nachos.threads;

import nachos.machine.*;

/**
 * 一个多线程的操作系统内核。
 */
public class ThreadedKernel extends Kernel {
	/**
	 * 分配（创建）一个新的多线程内核。
	 */
	public ThreadedKernel() {
		super();
	}

	/**
	 * 初始化这个内核。创建 scheduler、第一个线程和一个 alarm，并开启中断。
	 * 如有需要，还会创建文件系统。
	 */
	public void initialize(String[] args) {
		// 设置 scheduler
		String schedulerName = Config.getString("ThreadedKernel.scheduler");
		scheduler = (Scheduler) Lib.constructObject(schedulerName);

		// 设置 fileSystem
		String fileSystemName = Config.getString("ThreadedKernel.fileSystem");
		if (fileSystemName != null)
			fileSystem = (FileSystem) Lib.constructObject(fileSystemName);
		else if (Machine.stubFileSystem() != null)
			fileSystem = Machine.stubFileSystem();
		else
			fileSystem = null;

		// 启动线程系统
		new KThread(null);

		alarm = new Alarm();

		Machine.interrupt().enable();
	}

	/**
	 * 测试这个内核。测试 <tt>KThread</tt>、<tt>Semaphore</tt>、
	 * <tt>SynchList</tt> 和 <tt>ElevatorBank</tt> 这几个类。注意 autograder
	 * 永远不会调用这个方法，所以在这里添加额外的测试是安全的。
	 */
	public void selfTest() {
		KThread.selfTest();
		Semaphore.selfTest();
		SynchList.selfTest();
		Condition2.selfTest();
		if (Machine.bank() != null) {
			ElevatorBank.selfTest();
		}
		Alarm.selfTest();
	}

	/**
	 * threaded kernel 不运行用户程序，所以这个方法什么都不做。
	 */
	public void run() {
	}

	/**
	 * 终止这个内核。永远不会返回。
	 */
	public void terminate() {
		Machine.halt();
	}

	/** 全局可访问的 scheduler 引用。 */
	public static Scheduler scheduler = null;

	/** 全局可访问的 alarm 引用。 */
	public static Alarm alarm = null;

	/** 全局可访问的文件系统引用。 */
	public static FileSystem fileSystem = null;

	// 一些 dummy 变量，让 javac 更"聪明"（顺带把这些类也编译进来）
	private static RoundRobinScheduler dummy1 = null;

	private static PriorityScheduler dummy2 = null;

	private static LotteryScheduler dummy3 = null;

	private static Condition2 dummy4 = null;

        //private static Communicator dummy5 = null;

	private static Rider dummy6 = null;

	private static ElevatorController dummy7 = null;

        private static GameMatch dummy8 = null;

        private static Future dummy9 = null;
}
