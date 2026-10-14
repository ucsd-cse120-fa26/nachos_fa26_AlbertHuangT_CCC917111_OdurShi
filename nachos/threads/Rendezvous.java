package nachos.threads;

import nachos.machine.*;

/**
 * <i>Rendezvous</i> 让线程之间可以同步地交换值。
 */
public class Rendezvous {

    
    private Lock lock;
    private Condition waitSwap;
    private Condition waitEmpty;
    private int value = 0;

    //  将要创建多少个线程用于测试
    private static final int count = 100;
    
    // 0: empty; 1: ready to swap; 2: wait until get awaken
    private int state = 0;
    
    /**
     * 分配（创建）一个新的 Rendezvous。
     */
    public Rendezvous () {
        lock = new Lock();
        waitSwap = new Condition(lock);
        waitEmpty = new Condition(lock);
    }

    /**
     * 和另一个线程同步地交换一个值。第一个调用 exchange 的线程 A（带着值 X）
     * 会阻塞，等待另一个线程 B（带着值 Y）。当线程 B 到来时，它会解除 A 的
     * 阻塞，然后两个线程交换值：值 Y 返回给线程 A，值 X 返回给线程 B。
     *
     * 不同的整数 tag 用作不同的、相互平行的同步点（也就是说，在不同 tag 上
     * 同步的线程之间互不影响）。同一个 tag 也可以被反复用于多次交换。
     *
     * @param tag 同步用的 tag。
     * @param value 要交换的整数值。
     */
    public int exchange (int tag, int value) {
        lock.acquire();

        // if there are 2 threads in progress of swaping
        while(this.state == 2){
            waitEmpty.sleep();
        }
        // if there is not any threads waiting
        if (this.state == 0) {
            state = 1;
            this.value = value;
            
            waitSwap.sleep();

            value = this.value;
            this.value = 0;

            state = 0;

            // wake exactly 2 threads so that do not waste resources
            waitEmpty.wake();
            waitEmpty.wake();
            lock.release();
        } else if (this.state == 1){
            state = 2;

            // switch value
            value = this.value ^ value;
            this.value = this.value ^ value;
            value = this.value ^ value;
            
            waitSwap.wake();
            lock.release();
        }
        return value;
    }

    // 定义一个类叫 PingTest。
    // "implements Runnable" 表示：这个类保证自己有一个 run() 方法。
    // KThread 只接受带 run() 的对象，所以必须写这一句。
    private static class PingTest implements Runnable {

        // 字段：新线程要去的那个会合点
        private Rendezvous ping;
        private int number;
        private int[] returnValues;
        // 构造函数：创建 PingTest 时，外面要传进来一个会合点
        PingTest(Rendezvous ping, int number, int[] returnValues) {
            // 把传进来的会合点存进自己的字段，这样 run() 里才能用到它
            this.ping = ping;
            this.number = number;
            this.returnValues = returnValues;
        }

        // 新线程启动之后，会自动执行这个方法。
        // 这个方法不是你直接调用的，是 KThread 在 fork 之后替你调用的。
        public void run() {
            // 新线程去 ping 这个会合点的 0 号位置，带着 91。
            // 如果对方还没到，新线程会停在这一行等。
            // 交换完成后，把拿到的值存进 returnValue。
            this.returnValues[this.number] = ping.exchange(0, number);
            // /////////检查拿到的是不是 78（main 带来的值）。不是的话 Nachos 报错停止。
            // 因为配对任意, 现在由main统一检查
        }
        // run() 执行到这里结束，新线程也就随之结束
    }

	
    // 测试的入口。ThreadedKernel.selfTest() 会调用它，执行它的是 main 线程。
    public static void selfTest() {
        // 局部变量：用来存 main 交换后拿到的值
        int[] returnValues = new int[count];
    
        // 创建一个会合点。main 和新线程都要用这同一个。
        Rendezvous ping = new Rendezvous();
        // 调试输出：只有运行时加了 -d t 才会打印
        Lib.debug('t', "Enter Rendezvous.selfTest");
        
        KThread[] threadArray = new KThread[count];
        for(int i = 0; i < count; i++){
            // 创建任务说明，并把会合点交给它
            PingTest test = new PingTest(ping, i, returnValues);
            // 创建一个新线程，把任务说明交给它
            KThread kthread = new KThread(test);
            threadArray[i] = kthread;
            // 启动新线程。它进入就绪队列，但此刻还没开始运行，
            // main 会继续往下执行。
            kthread.fork();
        }

        for(int i = 0; i < count; i++){
            threadArray[i].join();
        }

        for(int i = 0; i < count; i++){
            Lib.assertTrue((returnValues[i] >= 0) && (returnValues[i] <= (count-1)) && (returnValues[returnValues[i]] == i) && (returnValues[i] != i));
        }
        // main 自己去 ping 的 0 号位置，带着 78。
        // 这时会合点没有人，所以 main 停在这一行等。
        // main 一停下，新线程就得到了运行机会，去执行上面的 run()。
        // 新线程带着 91 到达，两边交换，main 被叫醒，拿到 91。
        // 检查 main 拿到的是不是 91（新线程带来的值）
        // 能执行到这一行，说明上面的检查都通过了
        System.out.println("Rendezvous no problem!");
    }

}
