package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;
/**
 * 通过关闭 interrupt 来实现同步的 condition variable（条件变量）。
 *
 * <p>
 * 你必须实现这个类。
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * 分配（创建）一个新的 condition variable。
	 *
	 * @param conditionLock 与这个 condition variable 关联的 lock。
	 * 当前线程在使用 <tt>sleep()</tt>、<tt>wake()</tt> 或 <tt>wakeAll()</tt> 时，
	 * 必须持有这个 lock。
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * 原子地释放关联的 lock，并在这个 condition variable 上 sleep，直到另一个
	 * 线程用 <tt>wake()</tt> 把它唤醒。当前线程必须持有关联的 lock。在
	 * <tt>sleep()</tt> 返回之前，线程会自动重新获取这个 lock。
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		waitQueue.add(new Waiter(KThread.currentThread(), false));
		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * 最多唤醒一个在这个 condition variable 上 sleep 的线程。当前线程必须
	 * 持有关联的 lock。
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		while (!waitQueue.isEmpty()) {
			Waiter waiter = waitQueue.removeFirst();
			if (waiter.timed) {
				// cancel 成功时已经 ready 过了；timer 已到期就跳过，找下一个
				if (ThreadedKernel.alarm.cancel(waiter.thread))
					break;
			} else {
				waiter.thread.ready();
				break;
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * 唤醒所有在这个 condition variable 上 sleep 的线程。当前线程必须持有
	 * 关联的 lock。
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		while (!waitQueue.isEmpty()) {
			wake();
		}
	}

        /**
	 * 原子地释放关联的 lock，并在这个 condition variable 上 sleep，直到
	 * (1) 另一个线程用 <tt>wake()</tt> 把它唤醒，或者 (2) 指定的
	 * <i>timeout</i> 时间已经过去。当前线程必须持有关联的 lock。在
	 * <tt>sleep()</tt> 返回之前，线程会自动重新获取这个 lock。如果 timeout
	 * 为负数或 0，则立即返回。
	 */
        public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		if (timeout <= 0)
			return;

		boolean intStatus = Machine.interrupt().disable();
		Waiter waiter = new Waiter(KThread.currentThread(), true);
		waitQueue.add(waiter);
		conditionLock.release();
		ThreadedKernel.alarm.waitUntil(timeout);
		// 超时醒来时记录还在队列里
		waitQueue.remove(waiter);
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

    private static class Waiter {
        final KThread thread;
        final boolean timed;

        Waiter(KThread thread, boolean timed) {
            this.thread = thread;
            this.timed = timed;
        }
    }

    private Lock conditionLock;
    private LinkedList<Waiter> waitQueue = new LinkedList<Waiter>();
	    // Place Condition2 testing code in the Condition2 class.

    // Example of the "interlock" pattern where two threads strictly
    // alternate their execution with each other using a condition
    // variable.  (Also see the slide showing this pattern at the end
    // of Lecture 6.)

    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition2(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way
            // to do so is to join on ping.  (Note that, when ping is
            // done, pong is sleeping on the condition variable; if we
            // were also to join on pong, we would block forever.)
            // For this to work, join must be implemented.  If you
            // have not implemented join yet, then comment out the
            // call to join and instead uncomment the loop with
            // yields; the loop has the same effect, but is a kludgy
            // way to do it.
            ping.join();
            // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
    }

    // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
        new InterlockTest();
        cvTest5();
        sleepForTest1();
        sleepForTest2();
        sleepForTest3();
        sleepForTest4();
        sleepForTest5();
        System.out.println("sleepFor tests done");
    }

    // 没有 wake 时超时返回；0 和负数直接返回
    private static void sleepForTest1() {
        final Lock lock = new Lock();
        final Condition2 cv = new Condition2(lock);
        lock.acquire();
        cv.wake();
        cv.wakeAll();
        boolean intStatus = Machine.interrupt().disable();
        long t0 = Machine.timer().getTime();
        cv.sleepFor(0);
        cv.sleepFor(-100);
        Lib.assertTrue(Machine.timer().getTime() == t0);
        Lib.assertTrue(lock.isHeldByCurrentThread());
        Lib.assertTrue(Machine.interrupt().disabled());
        Machine.interrupt().restore(intStatus);
        lock.release();

        long[] durations = {500, 1500, 3500};
        KThread[] threads = new KThread[durations.length];
        for (int i = 0; i < durations.length; i++) {
            final long duration = durations[i];
            threads[i] = new KThread(() -> {
                lock.acquire();
                long start = Machine.timer().getTime();
                cv.sleepFor(duration);
                Lib.assertTrue(Machine.timer().getTime() - start >= duration);
                Lib.assertTrue(lock.isHeldByCurrentThread());
                lock.release();
            }).setName("sleepFor-timeout-" + i);
            threads[i].fork();
        }
        for (KThread thread : threads) {
            thread.join();
        }
    }

    // 提前被 wake，旧的 timer 不能影响下一次等待
    private static void sleepForTest2() {
        final Lock lock = new Lock();
        final Condition2 cv = new Condition2(lock);
        final Semaphore started = new Semaphore(0);
        final boolean[] done = {false};
        KThread child = new KThread(() -> {
            lock.acquire();
            boolean intStatus = Machine.interrupt().disable();
            long t0 = Machine.timer().getTime();
            started.V();
            cv.sleepFor(50000);
            Lib.assertTrue(Machine.timer().getTime() - t0 < 50000);
            Lib.assertTrue(lock.isHeldByCurrentThread());
            Lib.assertTrue(Machine.interrupt().disabled());
            done[0] = true;
            Machine.interrupt().restore(intStatus);
            lock.release();

            t0 = Machine.timer().getTime();
            ThreadedKernel.alarm.waitUntil(100000);
            Lib.assertTrue(Machine.timer().getTime() - t0 >= 100000);
        }).setName("sleepFor-wake");
        child.fork();
        started.P();
        lock.acquire();
        cv.wake();
        ThreadedKernel.alarm.waitUntil(2000);
        Lib.assertTrue(!done[0]);
        lock.release();
        child.join();
        Lib.assertTrue(done[0]);
    }

    // sleep 和 sleepFor 混合：wake 唤醒一个，wakeAll 唤醒剩下的
    private static void sleepForTest3() {
        final Lock lock = new Lock();
        final Condition2 cv = new Condition2(lock);
        final Semaphore started = new Semaphore(0);
        final int[] done = {0};
        KThread[] threads = new KThread[4];
        for (int i = 0; i < threads.length; i++) {
            final boolean timed = i % 2 == 0;
            threads[i] = new KThread(() -> {
                lock.acquire();
                boolean intStatus = Machine.interrupt().disable();
                started.V();
                if (timed) {
                    cv.sleepFor(500000);
                } else {
                    cv.sleep();
                }
                Lib.assertTrue(lock.isHeldByCurrentThread());
                done[0]++;
                Machine.interrupt().restore(intStatus);
                lock.release();
            }).setName("sleepFor-mixed-" + i);
            threads[i].fork();
        }
        for (int i = 0; i < threads.length; i++) {
            started.P();
        }
        lock.acquire();
        cv.wake();
        lock.release();
        ThreadedKernel.alarm.waitUntil(3000);
        lock.acquire();
        Lib.assertTrue(done[0] == 1);
        cv.wakeAll();
        lock.release();
        for (KThread thread : threads) {
            thread.join();
        }
        Lib.assertTrue(done[0] == threads.length);
    }

    // 定时等待者已经 ready 但还在队列里，wake 应该唤醒下一个
    private static void sleepForTest4() {
        final Lock lock = new Lock();
        final Condition2 cv = new Condition2(lock);
        final Semaphore started = new Semaphore(0);
        final boolean[] done = {false};
        KThread timed = new KThread(() -> {
            lock.acquire();
            boolean intStatus = Machine.interrupt().disable();
            started.V();
            cv.sleepFor(50000);
            Machine.interrupt().restore(intStatus);
            lock.release();
        });
        KThread normal = new KThread(() -> {
            lock.acquire();
            boolean intStatus = Machine.interrupt().disable();
            started.V();
            cv.sleep();
            done[0] = true;
            Machine.interrupt().restore(intStatus);
            lock.release();
        });
        timed.fork();
        started.P();
        normal.fork();
        started.P();
        lock.acquire();
        boolean intStatus = Machine.interrupt().disable();
        Lib.assertTrue(ThreadedKernel.alarm.cancel(timed));
        cv.wake();
        Machine.interrupt().restore(intStatus);
        lock.release();
        timed.join();
        normal.join();
        Lib.assertTrue(done[0]);
    }

    // 短超时和 wake/wakeAll 交替出现
    private static void sleepForTest5() {
        final Lock lock = new Lock();
        final Condition2 cv = new Condition2(lock);
        final int[] done = {0};
        KThread[] threads = new KThread[4];
        for (int i = 0; i < threads.length; i++) {
            final long timeout = 1 + i * 500;
            threads[i] = new KThread(() -> {
                for (int j = 0; j < 4; j++) {
                    lock.acquire();
                    cv.sleepFor(timeout);
                    Lib.assertTrue(lock.isHeldByCurrentThread());
                    done[0]++;
                    lock.release();
                }
            });
            threads[i].fork();
        }
        KThread signaler = new KThread(() -> {
            for (int i = 0; i < 8; i++) {
                ThreadedKernel.alarm.waitUntil(250);
                lock.acquire();
                if (i % 2 == 0) {
                    cv.wake();
                } else {
                    cv.wakeAll();
                }
                lock.release();
            }
        });
        signaler.fork();
        for (KThread thread : threads) {
            thread.join();
        }
        signaler.join();
        Lib.assertTrue(done[0] == 16);
    }

	    // Place Condition2 test code inside of the Condition2 class.

    // Test programs should have exactly the same behavior with the
    // Condition and Condition2 classes.  You can first try a test with
    // Condition, which is already provided for you, and then try it
    // with Condition2, which you are implementing, and compare their
    // behavior.

    // Do not use this test program as your first Condition2 test.
    // First test it with more basic test programs to verify specific
    // functionality.

    public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }
}
