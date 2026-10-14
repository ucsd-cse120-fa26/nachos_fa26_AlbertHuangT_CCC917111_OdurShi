package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * 利用硬件 timer 来实现抢占（preemption），并让线程可以 sleep 到某个时间点。
 */
public class Alarm {
	// Add Alarm testing code to the Alarm class
    
    public static void alarmTest1() {
	int durations[] = {1000, 10*1000, 100*1000};
	long t0, t1;

	for (int d : durations) {
	    t0 = Machine.timer().getTime();
	    ThreadedKernel.alarm.waitUntil (d);
	    t1 = Machine.timer().getTime();
	    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
	}
    }
	public static void alarmTest2() {
    long[] durations = {0, -100};          
    for (long d : durations) {             
        long t0 = Machine.timer().getTime();   
        ThreadedKernel.alarm.waitUntil(d);    
        long t1 = Machine.timer().getTime();   
        System.out.println("alarmTest2: waitUntil(" + d + ") waited for "
                           + (t1 - t0) + " ticks");
    }
	}
	public static void alarmTest3() {
    long[] durations = {3000, 1000, 2000};   

    for (int i = 0; i < durations.length; i++) {
        final long d = durations[i];         
        final int id = i;                   

        new KThread(() -> {                
            long t0 = Machine.timer().getTime();
            ThreadedKernel.alarm.waitUntil(d);
            long t1 = Machine.timer().getTime();
            System.out.println("alarmTest3: thread " + id + " asked for " + d
                               + ", woke at " + t1 + ", waited " + (t1 - t0));
        }).setName("alarmTest3-" + i).fork();  
    }

    // 主线程自己等一段比所有子线程都长的时间，保证它们都醒来打印完
    ThreadedKernel.alarm.waitUntil(5000);
    System.out.println("alarmTest3: main thread done");
	}
    // Implement more test methods here ...

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
	alarmTest1();
    alarmTest2();
	alarmTest3();
	// Invoke your other test methods here ...
    }
	/**
	 * 分配（创建）一个新的 Alarm。把机器的 timer 中断处理函数设置为这个
	 * alarm 的回调函数。
	 *
	 * <p>
	 * <b>注意</b>：如果存在多于一个 alarm，Nachos 将无法正常工作。
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * timer 中断处理函数。机器的 timer 会周期性地调用它（大约每 500 个
	 * clock tick 一次）。它会让当前线程 yield；如果有其他应该运行的线程，
	 * 就强制发生一次 context switch。
	 */
	public void timerInterrupt() {
		long now = Machine.timer().getTime();
		while (!waitQueue.isEmpty() && waitQueue.peek().time <= now) {
			ThreadTime threadTime = waitQueue.poll();
			threadTime.thread.ready();
		}
	        /* 不要删除这个 yield 调用。 */
		KThread.currentThread().yield();
	}

	/**
	 * 让当前线程 sleep 至少 <i>x</i> 个 tick，并在 timer 中断处理函数中把它
	 * 唤醒。这个线程必须在满足下面条件的第一次 timer 中断中被唤醒（放入
	 * scheduler 的 ready set）：
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * 如果等待值 <i>x</i> 为负数或 0，则立即返回，不阻塞调用它的线程。
	 *
	 * @param x 至少要等待的 clock tick 数。
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if (x <= 0) {return;}
		long wakeTime = Machine.timer().getTime() + x;
		boolean intStatus = Machine.interrupt().disable();
		waitQueue.add(new ThreadTime(KThread.currentThread(), wakeTime));
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * 取消 <i>thread</i> 设置的任何 timer，相当于立即唤醒这个线程（把它放入
	 * scheduler 的 ready set），并返回 true。如果 <i>thread</i> 没有设置
	 * timer，则返回 false。
	 *
	 * <p>
	 * @param thread 要取消其 timer 的线程。
	 */
        public boolean cancel(KThread thread) {
		return false;
	}

	private class ThreadTime{
		KThread thread;
		long time;
		public ThreadTime(KThread thread, long time){
			this.thread = thread;
			this.time = time;
		}
	}
	private PriorityQueue<ThreadTime> waitQueue = new PriorityQueue<>((a, b) -> Long.compare(a.time, b.time));
	
}
