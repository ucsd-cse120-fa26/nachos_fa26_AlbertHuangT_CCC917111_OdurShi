package nachos.threads;
import nachos.machine.*;

/**
 * KThread 是一个可以用来执行 Nachos 内核代码的线程。Nachos
 * 允许多个线程并发运行。
 *
 * 要创建一个新的执行线程，首先声明一个实现了 <tt>Runnable</tt>
 * 接口的类，由这个类来实现 <tt>run</tt> 方法。然后创建这个类的一个实例，
 * 在创建 <tt>KThread</tt> 时把它作为参数传进去，再调用 fork。例如，
 * 一个计算 pi 的线程可以这样写：
 *
 * <p>
 * <blockquote>
 *
 * <pre>
 * class PiRun implements Runnable {
 * 	public void run() {
 *         // 计算 pi
 *         ...
 *     }
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * 下面的代码会创建这个线程，并让它开始运行：
 *
 * <p>
 * <blockquote>
 *
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 *
 * </blockquote>
 */
public class KThread {
	/**
	 * 获取当前线程。
	 *
	 * @return 当前线程。
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * 分配（创建）一个新的 <tt>KThread</tt>。如果这是first KThread,
	 * 会顺带创建一个 idle 线程。
	 */
	public KThread() {
		if (currentThread != null) {
			tcb = new TCB();
		}
		else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * 分配（创建）一个新的 KThread。
	 *
	 * @param target 一个对象，这个线程运行时会调用它的 <tt>run</tt> 方法。
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * 设置这个线程的 target。
	 *
	 * @param target 一个对象，这个线程运行时会调用它的 <tt>run</tt> 方法。
	 * @return 这个线程本身。
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * 设置这个线程的名字。名字只用于调试。
	 *
	 * @param name 要给这个线程起的名字。
	 * @return 这个线程本身。
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * 获取这个线程的名字。名字只用于调试。
	 *
	 * @return 这个线程的名字。
	 */
	public String getName() {
		return name;
	}

	/**
	 * 获取这个线程的完整名字，包括名字和它的数字 ID。只用于调试。
	 *
	 * @return 这个线程的完整名字。
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * 以确定且一致的方式，把这个线程和另一个线程做比较。
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * 让这个线程开始执行。执行的结果是两个线程并发运行：当前线程（它从
	 * <tt>fork</tt> 方法调用中返回），以及另一个线程（它执行自己 target 的
	 * <tt>run</tt> 方法）。
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: "
				+ target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * 结束当前线程，并安排在安全的时候把它销毁。当一个线程的 <tt>run</tt>
	 * 方法返回时，这个方法会被自动调用；但也可以直接调用它。
	 *
	 * 当前线程不能被立刻销毁，因为它的栈和其他执行状态还在使用中。
	 * 所以，这个线程会在下一个线程开始运行、可以安全删除它的时候，
	 * 由那个线程自动销毁。
	 */
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();

		Machine.autoGrader().finishingCurrentThread();

		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;

		currentThread.status = statusFinished;
		if (currentThread.joinWaiter != null) {
			currentThread.joinWaiter.ready();
			currentThread.joinWaiter = null;
		}

		sleep();
	}

	/**
	 * 如果有其他线程准备好运行，就让出 CPU。这种情况下，把当前线程放进
	 * ready queue，这样它之后还会被重新调度。
	 *
	 * <p>
	 * 如果没有其他线程准备好运行，就立刻返回。否则，要等到当前线程再次被
	 * <tt>readyQueue.nextThread()</tt> 选中运行时才返回。
	 *
	 * <p>
	 * 这里会关闭中断，这样当前线程就能原子地把自己加入 ready queue，
	 * 并切换到下一个线程。返回时，把中断恢复成调用前的状态，以防
	 * <tt>yield()</tt> 是在中断已经关闭的情况下被调用的。
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * 让出 CPU，原因是当前线程要么已经结束，要么被阻塞了。这个线程必须是
	 * 当前线程。
	 *
	 * <p>
	 * 如果当前线程是被阻塞了（阻塞在某个同步原语上，即 <tt>Semaphore</tt>、
	 * <tt>Lock</tt> 或 <tt>Condition</tt>），之后会有某个线程把它唤醒，把它放回
	 * ready queue，这样它就能被重新调度。否则（即线程已经结束），
	 * <tt>finish()</tt> 应该已经安排好，由下一个运行的线程来销毁这个线程。
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * 把这个线程设为 ready 状态，并把它加入调度器的 ready queue。
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * 等待这个线程结束。如果这个线程已经结束，就立刻返回。这个方法只能被
	 * 调用一次；第二次调用不保证会返回。这个线程不能是当前线程。
	 *
	 * 【译注：proj1 的 spec 对第二次调用的要求更严格：对同一个线程第二次
	 * 调用 join 必须触发 assert。】
	 */
	public void join() {
		Lib.debug(dbgThread, "Joining to thread: " + toString());

		Lib.assertTrue(this != currentThread);

		boolean intStatus = Machine.interrupt().disable();
		Lib.assertTrue(!joinCalled);
		// 线程已经结束也不能再 join 第二次
		joinCalled = true;
		if (status != statusFinished) {
			joinWaiter = currentThread;
			KThread.sleep();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * 创建 idle 线程。每当没有线程可以运行、而 <tt>runNextThread()</tt>
	 * 又被调用时，就运行 idle 线程。idle 线程绝不能阻塞，而且只有在其他
	 * 所有线程都被阻塞时，才允许它运行。
	 *
	 * <p>
	 * 注意：<tt>ready()</tt> 永远不会把 idle 线程加入 ready set。
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					KThread.yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * 决定下一个要运行的线程，然后通过 <tt>run()</tt> 把 CPU 交给那个线程。
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * 把 CPU 交给这个线程。保存当前线程的状态，调用 <tt>TCB.contextSwitch()</tt>
	 * 切换到新线程，再加载新线程的状态。新线程成为当前线程。
	 *
	 * <p>
	 * 即使新线程和旧线程是同一个，这个方法也必须照常调用 <tt>saveState()</tt>、
	 * <tt>contextSwitch()</tt> 和 <tt>restoreState()</tt>。
	 *
	 * <p>
	 * 之前在运行的那个线程，它的状态必须已经从 running 改成了 blocked 或
	 * ready（取决于这个线程是在 sleep 还是在 yield）。
	 *
	 * @param finishing 如果当前线程已经结束、应该由新线程来销毁它，则为
	 * <tt>true</tt>。
	 * 【译注：这个方法其实没有 finishing 参数，这是旧版本遗留的注释，可以忽略。】
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
				+ " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * 准备让这个线程运行。把 <tt>status</tt> 设为 <tt>statusRunning</tt>，
	 * 并检查 <tt>toBeDestroyed</tt>。
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * 准备让这个线程交出处理器。内核线程在这里不需要做任何事。
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i
						+ " times");
				currentThread.yield();
			}
		}

		private int which;
	}

	/**
	 * 测试这个模块是否正常工作。
	 */
	public static void selfTest() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		KThread ping = new KThread(new PingTest(1)).setName("forked thread");
		ping.fork();
		new PingTest(0).run();
		ping.join();

		joinTest1();
		joinTest2();
		joinTest3();
		joinTest4();
		// joinTest5();  // 会触发 assert，需单独运行
		// joinTest6();  // 会触发 assert，需单独运行
		System.out.println("join tests done");
	}

	// 子线程已经结束，join 直接返回
	private static void joinTest1() {
		final Semaphore finished = new Semaphore(0);
		KThread child = new KThread(() -> {
			Machine.interrupt().disable();
			finished.V();
		}).setName("join-finished");
		child.fork();
		finished.P();
		Lib.assertTrue(child.status == statusFinished);
		child.join();
		Lib.assertTrue(Machine.interrupt().enabled());
	}

	// 子线程还在运行，调用者要阻塞
	private static void joinTest2() {
		final Semaphore release = new Semaphore(0);
		final KThread parent = currentThread();
		final boolean[] done = {false};
		KThread child = new KThread(() -> {
			release.P();
			done[0] = true;
		}).setName("join-running");
		KThread observer = new KThread(() -> {
			Lib.assertTrue(parent.status == statusBlocked);
			release.V();
		}).setName("join-observer");

		boolean intStatus = Machine.interrupt().disable();
		child.fork();
		observer.fork();
		child.join();
		Lib.assertTrue(done[0] && child.status == statusFinished);
		Lib.assertTrue(Machine.interrupt().disabled());
		Machine.interrupt().restore(intStatus);
		observer.join();
	}

	// 几组线程互不干扰，每个父线程依次 join 两个子线程
	private static void joinTest3() {
		KThread[] parents = new KThread[3];
		for (int i = 0; i < parents.length; i++) {
			parents[i] = new KThread(() -> {
				for (int j = 0; j < 2; j++) {
					final boolean[] done = {false};
					KThread child = new KThread(() -> {
						KThread.yield();
						done[0] = true;
					});
					child.fork();
					child.join();
					Lib.assertTrue(done[0]);
				}
			}).setName("join-pair-" + i);
			parents[i].fork();
		}
		for (KThread parent : parents) {
			parent.join();
		}
	}

	// 非父子线程之间 join，以及没人 join 的线程
	private static void joinTest4() {
		final Semaphore release = new Semaphore(0);
		KThread child = new KThread(() -> release.P());
		KThread other = new KThread(() -> child.join());
		child.fork();
		other.fork();
		release.V();
		other.join();
		Lib.assertTrue(child.status == statusFinished);

		final Semaphore finished = new Semaphore(0);
		KThread unjoined = new KThread(() -> {
			Machine.interrupt().disable();
			finished.V();
		});
		unjoined.fork();
		finished.P();
		Lib.assertTrue(unjoined.status == statusFinished);
	}

	// 自己 join 自己，应该 assert
	private static void joinTest5() {
		currentThread().join();
	}

	// 对同一个线程 join 两次，应该 assert
	private static void joinTest6() {
		KThread child = new KThread(() -> {}).setName("join-twice");
		child.fork();
		child.join();
		child.join();
	}

	private static final char dbgThread = 't';

	/**
	 * 供调度器使用的额外状态。
	 *
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;

	private static final int statusReady = 1;

	private static final int statusRunning = 2;

	private static final int statusBlocked = 3;

	private static final int statusFinished = 4;

	/**
	 * 这个线程的状态。一个线程可以处于以下状态之一：new（还没有 fork）、
	 * ready（在 ready queue 上，但没有在运行）、running（正在运行），
	 * 或者 blocked（既不在 ready queue 上，也没有在运行）。
	 */
	private int status = statusNew;

	private String name = "(unnamed thread)";

	private Runnable target;

	// join 用到的状态
	private boolean joinCalled = false;
	private KThread joinWaiter = null;

	private TCB tcb;

	/**
	 * 唯一标识符。用于以确定的方式比较线程。
	 */
	private int id = numCreated++;

	/** KThread 构造函数被调用的次数。 */
	private static int numCreated = 0;

	private static ThreadQueue readyQueue = null;

	private static KThread currentThread = null;

	private static KThread toBeDestroyed = null;

	private static KThread idleThread = null;
}
