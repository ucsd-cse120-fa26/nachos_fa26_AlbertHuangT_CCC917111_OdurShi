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
		waitQueue.add(KThread.currentThread());
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
		if (!waitQueue.isEmpty()) {
			KThread thread = waitQueue.removeFirst();
			thread.ready();
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
	}

        private Lock conditionLock;
		private LinkedList<KThread> waitQueue = new LinkedList<KThread>();
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
