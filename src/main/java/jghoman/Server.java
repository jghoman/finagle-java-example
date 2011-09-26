package jghoman;

import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.thrift.ThriftServerFramedCodec;
import com.twitter.util.ExecutorServiceFuturePool;
import com.twitter.util.Function0;
import com.twitter.util.Future;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
  // Function0 implementations - how to do blocking work on the server:
  // In Scala, Finagle passes closures to to a threadpool to execute long-running calls on another thread.
  // In Java, the closure is simulated by passing an instance of a Function0 (which is a wrapper around
  // how Scala actually implements closures, and should probably be named something more Java friendly).
  // The only method to implement is apply, which is where the long-running calculation lines.
  // This example just burns through CPU for the specified number of seconds, simulating some Big Important Work
  public static class ExampleBlockingCall extends com.twitter.util.Function0<Integer> {
    private int delayForSeconds;

    public ExampleBlockingCall(int delayForSeconds) {
      this.delayForSeconds = delayForSeconds;
    }

    public Integer apply() {
      System.out.println("Blocking call will now do some busy work for " + delayForSeconds + " seconds");
      long delayUntil = System.currentTimeMillis() + (delayForSeconds * 1000);

      long acc = 0;
      while(System.currentTimeMillis() < delayUntil) {
        // Let's bind and gag the CPU
        for(int i = 0; i < 1000; i++) {
            for(int j = 0; j < 1000; j++) {
                acc += delayForSeconds + j + i;
            }
        }
      }

      return acc == System.currentTimeMillis() ? 42 : delayForSeconds; // whatever, doesn't matter
    }
  }

  public static class HaverServer implements Haver.ServiceIface /* Special Interface added by Finagle's thrift compiler */ {
    // In Scala, one can call directly to the FuturePool, but Java gets confused
    // between the object and class, so it's best to instantiate an ExecutorServiceFuturePool directly
    ExecutorService es = Executors.newFixedThreadPool(4); // Number of threads to devote to blocking requests
    ExecutorServiceFuturePool esfp = new ExecutorServiceFuturePool(es); // Pool to process blockng requests so server thread doesn't

    Random r = new Random();

    // Simple call that returns a value
    @Override
    public Future<String> hi() {
      System.out.println("HaverServer:hi request received.");
      // Future.value is an immediate return of the expression, suitable for non-blocking calls
      return Future.value("Hello, Stonehenge! At the beep, the time will be " + System.currentTimeMillis());
    }

    // Very fast, non-blocking computation that the server can respond to immediately
    @Override
    public Future<Integer> add(int a, int b) {
      System.out.println("HaverServer:add(" + a + " ," + b + ") request received");
      return Future.value(a + b);
    }

    // Call that will take some time and should be moved off of the server's main event loop
    @Override
    public Future<Integer> blocking_call() {
      int delay = r.nextInt(10); // blocking calls will take between 0 and 10 seconds
      System.out.println("HaverServer:blocking_call requested. Will block for " + delay + " seconds");
      Function0<Integer> blockingWork = new ExampleBlockingCall(delay);
      // Load the blocking call on the threadpool to be scheduled and eventually executed.  Once complete,
      // the result will be returned to the client
      return esfp.apply(blockingWork);
    }
  }

  public static void main(String[] args) {
    Haver.ServiceIface server = new HaverServer();

    ServerBuilder.safeBuild(
            new Haver.Service(server, new TBinaryProtocol.Factory()),
            ServerBuilder.get()
                    .name("HaverServer")
                    .codec(ThriftServerFramedCodec.get())  // IntelliJ  shows this as a type error, but compiles
                    .bindTo(new InetSocketAddress(8080))
    );
    System.out.println("HaverServer's up!");

  }
}