package jghoman;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.thrift.ThriftClientFramedCodecFactory;
import com.twitter.finagle.thrift.ThriftClientRequest;
import com.twitter.util.FutureEventListener;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public static void main(String[] args) {
        Service<ThriftClientRequest, byte[]> client = ClientBuilder.safeBuild(ClientBuilder.get()
                .hosts(new InetSocketAddress(8080))
                .codec(new ThriftClientFramedCodecFactory())
                .hostConnectionLimit(100)); // IMPORTANT: this determines how many rpc's are sent in at once.
                                           // If set to 1, you get no parallelism on for this client.


        Haver.ServiceIface haverClient = new Haver.ServiceToClient(client, new TBinaryProtocol.Factory());

        // Simple call to ask the server to say hi.
        haverClient.hi().addEventListener(new FutureEventListener<String>() {
            @Override
            public void onFailure(Throwable cause) {
                System.out.println("Hi call. Failure: " + cause);
            }

            @Override
            public void onSuccess(String value) {
                System.out.println("Hi call. Success: " + value);
            }
        });

        // Simple call to as the server to add a couple numbers
        haverClient.add(40, 2).addEventListener(new FutureEventListener<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                System.out.println("Add call success. Answer: " + integer);
            }

            @Override
            public void onFailure(Throwable throwable) {
                System.out.println("Add call fail because: " + throwable);
            }
        });

        // Now let's inundate the server with lots of blocking calls and watch as it handles them
        int numCalls = 1000;
        List<BlockingCallResponse> responses = new ArrayList<BlockingCallResponse>(numCalls);

        for (int i = 0; i < numCalls; i++) {
          BlockingCallResponse blockingCallResponse = new BlockingCallResponse(i);
          // Send call to the server, return its result handler
          haverClient.blocking_call().addEventListener(blockingCallResponse);
          responses.add(blockingCallResponse);
          System.out.println("Queued up request #" + i);

          // Just for fun, throw in some non-blocking calls to ensure the server responds quickly.
          haverClient.add(i, i).addEventListener(new FutureEventListener<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                System.out.println("Extra Add call success. Answer: " + integer);
            }

            @Override
            public void onFailure(Throwable throwable) {
                System.out.println("Extra Add call fail because: " + throwable);
            }
        });
        }
        System.out.println("Waiting until everyone is done");
        boolean done = false;

        while (!done) {
            // Check to see how many results we've received, report on the number
            int count = 0;
            for (BlockingCallResponse blockingCallResponse : responses) {
                if (blockingCallResponse.isDone()) count++;

                done = true;
            }
            done = count == numCalls; // We're done when everyone has gotten a result back
            if (!done) {
                try {
                    Thread.sleep(1 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        client.release(); // Close down the clinet
        System.out.println("Everybody is done, let's see what they got: ");
        for (BlockingCallResponse blockingCallResponse : responses) {
            System.out.println("Answer = " + blockingCallResponse.getAnswer());
        }

        System.out.println("Done");
        return;
    }

    public static class BlockingCallResponse implements FutureEventListener<Integer> {
        int num; // this was call number

        public BlockingCallResponse(int num) {
            this.num = num;
        }

        AtomicBoolean b = new AtomicBoolean(false); // Have we got a response yet?
        Integer answer = null;

        public void onFailure(Throwable cause) {
            b.set(true);
            System.out.println("Failure in BlockingCallResponse for #" + num + ": " + cause);
        }

        public void onSuccess(Integer value) {
            answer = value;
            b.set(true);
            System.out.println("Got a response back for #" + num + ": " + value);
        }

        public int getAnswer() {
            return answer;
        }

        public boolean isDone() {
            return b.get();
        }
    }
}