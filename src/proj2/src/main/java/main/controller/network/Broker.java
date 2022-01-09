package main.controller.network;

import main.controller.message.MessageSender;
import main.model.PeerInfo;
import main.model.SocketInfo;
import main.model.message.Message;
import main.controller.message.MessageBuilder;
import main.model.message.response.MessageResponse;
import main.model.timelines.Post;
import org.zeromq.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;


public class Broker {
    private static final int N_WORKERS = 3;

    private ZContext context;
    private SocketInfo socketInfo;
    private ZMQ.Socket backend;
    // To signal the broker thread to shutdown, we use a control socket
    // This is better than an interrupt because this thread has a poller, making the process of exiting more safe
    // We could use interrupts here, but it would cause too many try catches (smelly code) and JeroMQ only started
    // supporting socket interruption on receive calls recently
    private ZMQ.Socket control;
    private final Map<String, List<Post>> subMessages; // New posts that are posted by our subs
    private List<Worker> workers;

    private Thread thread;
    // Messages that we are expecting to receive, workers fill these when they receive the request
    private final ConcurrentMap<UUID, CompletableFuture<MessageResponse>> promises;

    public Broker(ZContext context, InetAddress address){
        this.context = context;
        this.backend = context.createSocket(SocketType.ROUTER);
        this.control = context.createSocket(SocketType.PULL);
        this.backend.bind("inproc://workers");
        this.control.bind("inproc://control");

        this.socketInfo = new SocketInfo(context, address, SocketType.REP, SocketType.PUB);

        this.promises = new ConcurrentHashMap<>();
        this.workers = new ArrayList<>();
        this.thread = new Thread(this::run);
        this.subMessages = new ConcurrentHashMap<>();
        for(int id = 0; id < N_WORKERS; id++){
            Worker worker = new Worker(context, id, promises, socketInfo);
            workers.add(worker);
        }
    }

    public SocketInfo getSocketInfo() {
        return socketInfo;
    }

    public Map<String, List<Post>> popSubMessages() {
        Map<String, List<Post>> res;
        synchronized (subMessages) {
            res = new HashMap<>(subMessages);
            subMessages.clear();
        }
        return res;
    }

    public void setSender(MessageSender sender) {
        for (Worker w: workers)
            w.setSender(sender);
    }

    public void setPeerInfo(PeerInfo peerInfo) {
        for (Worker w: workers)
            w.setPeerInfo(peerInfo);
    }

    public Future<MessageResponse> addPromise(UUID id) {
        if (promises.containsKey(id))
            return promises.get(id);

        CompletableFuture<MessageResponse> promise = new CompletableFuture<>();
        promises.put(id, promise);
        return promise;
    }

    public void removePromise(UUID id) {
        if (!promises.containsKey(id))
            return;
        promises.remove(id);
    }

    private void sendToControl(String new_sub) {
        ZMQ.Socket controlSend = context.createSocket(SocketType.PUSH);
        controlSend.connect("inproc://control");
        controlSend.send(new_sub);
        controlSend.close();
    }

    public void subscribe(String username, InetAddress address, String port) {
        System.out.println("SUBBED TO " + port);
        socketInfo.addSubscription(username, address, port);
        this.sendToControl("NEW_SUB");
    }

    public void unsubscribe(String username) {
        socketInfo.removeSubscription(username);
        this.sendToControl("NEW_UNSUB");
    }

    public void publishPost(Post post) {
        try {
            ZMQ.Socket publisher = this.socketInfo.getPublisher();
            publisher.send(MessageBuilder.objectToByteArray(post));
        } catch (IOException e) { // Thrown when we don't receive a post
            e.printStackTrace();
        }
    }

    public void execute() {
        this.thread.start();
    }

    public void stop() {
        if (this.thread.isAlive()) {
            // CHECK controlSend.close() after try
            this.sendToControl("STOP");

            try {
                this.thread.join();
                this.socketInfo.close();
                backend.close();
                control.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for(Worker worker: workers)
            worker.stop();
    }

    public void run() {
        for (Worker worker: workers)
            worker.execute();
        Queue<String> worker_queues = new LinkedList<>();

        while (!Thread.currentThread().isInterrupted()) {
            ZMQ.Poller items = context.createPoller(4);
            items.register(backend, ZMQ.Poller.POLLIN);
            items.register(control, ZMQ.Poller.POLLIN);

            for (ZMQ.Socket socket: socketInfo.getSubscriptions())
                items.register(socket, ZMQ.Poller.POLLIN);

            if (worker_queues.size() > 0) {
                System.out.println("WAITING FOR " + socketInfo.getFrontendPort());
                items.register(socketInfo.getFrontend(), ZMQ.Poller.POLLIN);
            }

            if (items.poll() < 0)
                return;

            if (items.pollin(0)) { // Backend, Worker pinged
                try {
                    worker_queues.add(backend.recvStr());

                    //Remove empty msg between messages
                    String empty = backend.recvStr();
                    assert(empty.length() == 0);

                    String workerResponse = backend.recvStr();
                    assert(workerResponse.equals("READY"));
                } catch (ZMQException e) {
                    e.printStackTrace();
                }
            }

            if (items.pollin(1)) { // Control, shutdown now or add new sub
                String cmd = control.recvStr();
                if (cmd.equals("STOP"))
                    return;
                else if (cmd.equals("NEW_SUB") || cmd.equals("NEW_UNSUB")) {} // Do nothing
            }

            Set<String> subscribedUsers = this.socketInfo.getSubsribedUsers();
            int i=0;
            for (String username : subscribedUsers) {
                if (items.pollin(2 + i)) { // Received post from subscription
                    ZMQ.Socket subscription = items.getSocket(2 + i);
                    try {
                        Post post = MessageBuilder.postFromSocket(subscription);
                        this.subMessages.putIfAbsent(username, new CopyOnWriteArrayList<>());
                        this.subMessages.get(username).add(post);

                        // check if I should redirect this post to other peers
                        if (this.socketInfo.hasRedirect(username)) {
                            ZMQ.Socket redirectSocket = this.socketInfo.getRedirectSocket(username);
                            try { // send posts to the redirect PUB port
                                redirectSocket.send(MessageBuilder.objectToByteArray(post));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                ++i;
            }

            if (items.pollin(2 + this.socketInfo.getSubscriptionSize())) { // Frontend, client request
                try {
                    //Remove empty msg between messages
                    Message request = null;
                    try {
                        request = MessageBuilder.messageFromSocket(socketInfo.getFrontend());
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    String workerAddr = worker_queues.poll();

                    backend.sendMore(workerAddr);
                    backend.sendMore("");
                    try {
                        backend.send(MessageBuilder.objectToByteArray(request));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    socketInfo.getFrontend().send("OK");
                } catch (ZMQException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
