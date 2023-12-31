package main.controller;

import main.Peer;
import main.controller.message.MessageSender;
import main.controller.network.AuthenticationServer;
import main.gui.GraphWrapper;
import main.model.neighbour.Neighbour;
import main.model.timelines.Post;
import main.model.timelines.Timeline;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class CommandExecutor {
    private static final int MAX_CAPACITY = 31;
    private final MultipleNodeExecutor executor;
    private final Map<String, Peer> peers;
    private int curr_peer_id;
    private final GraphWrapper graph;
    private Peer currPeerListening;
    private AuthenticationServer authenticationServer;

    public CommandExecutor() {
        this.peers = new HashMap<>();
        this.executor = new MultipleNodeExecutor();
        this.curr_peer_id = 1;
        this.graph = new GraphWrapper("Network");
        this.executor.execute();
        this.currPeerListening = null;
        this.authenticationServer = null;
    }

    public int execCmd(String cmd) throws UnknownHostException, InterruptedException {
        String[] opts = cmd.split(" ");

        switch (opts[0].toUpperCase()) {
            case "START" :
                return this.execStart(opts);
            case "POST":
                return this.execPost(cmd, opts);
            case "STOP":
                return this.execStop(opts);
            case "START_MULT":
                return this.execStartMult(opts);
            case "STOP_ALL":
                return this.execStopAll();
            case "DELETE":
                return this.execDelete(opts);
            case "UPDATE":
                return this.execUpdate(cmd, opts);
            case "TIMELINE":
                return this.execTimeline(opts);
            case "SEARCH":
                return this.execSearch(opts);
            case "SUB":
                return this.execSub(opts);
            case "IGNORE":
                return this.execIgnore(opts);
            case "MSG_TIMEOUT":
                return this.execDelay(opts);
            case "LISTEN":
                return this.execListen(opts);
            case "PRINT":
                return this.execPrint(opts);
            case "PRINT_PEERS":
                return this.execPrintPeers();
            case "SLEEP":
                return this.execSleep(opts);
            case "BREAK":
                return this.execBreakpoint();
            case "AUTH":
                return this.execAuth();
            case "LOGIN":
                return this.execLogin(opts);
            case "LOGOUT":
                return this.execLogout(opts);
            case "REGISTER":
                return this.execRegister(opts);
            default:
                return -1;

        }
    }

    private int execRegister(String[] opts) {
        String username = opts[1];
        String password = opts[2];
        this.peers.get(username).register(password,authenticationServer.getAddress(), authenticationServer.getSocketPort());
        return 0;
    }

    private int execLogout(String[] opts) {
        String username = opts[1];
        this.peers.get(username).logout();
        return 0;
    }

    private int execLogin(String[] opts) {
        String username = opts[1];
        String password = opts[2];
        this.peers.get(username).login(password,authenticationServer.getAddress(), authenticationServer.getSocketPort());
        return 0;
    }

    private int execAuth() {
        InetAddress localhost = null;
        try {
            localhost = InetAddress.getByName("localhost");
        } catch (UnknownHostException ignored) {}
        authenticationServer = new AuthenticationServer(localhost);
        authenticationServer.execute();
        return 0;
    }

    private int execStart(String[] opts) throws UnknownHostException {
        if (opts.length < 3) return -1;

        // create and store peer
        String username = opts[1];
        int capacity = Integer.parseInt(opts[2]);

        InetAddress address = InetAddress.getByName("localhost");
        Peer peer = new Peer(username, address, capacity);
        System.out.println("INIT " + username + " " + peer.getPeerInfo().getPort() + " " + peer.getPeerInfo().getPublishPort());

        // connect to the provided peer
        if (opts.length == 4) {
            String paramUsername = opts[3];
            this.startPeer(username, peer, paramUsername);
        }
        else {
            // connect randomly to another peer
            startPeer(username, peer);
        }
        
        return 0;
    }

    public void displayGraph() {
        this.graph.display();
    }

    private void startPeer(String username, Peer peer) {
        peer.subscribe(this.graph);
        this.connectToNetwork(peer);
        peers.put(username, peer);
        executor.addNode(peer);
    }

    private void startPeer(String username, Peer peer, String joinUsername) {
        Peer joinPeer = this.peers.get(joinUsername);

        if (joinPeer == null) {
            System.err.println("Error: No peer with the requested ID (" + joinUsername + ")");
            System.out.println("Connecting " + username + " randomly.");
            this.startPeer(username, peer);
            return;
        }
        peer.subscribe(this.graph);
        peer.join(new Neighbour(joinPeer.getPeerInfo().getHost()));
        peers.put(username, peer);
        executor.addNode(peer);
    }

    private void connectToNetwork(Peer peer) {
        // select random peer to connect to
        if (this.peers.size() > 0) {
            List<String> users = new ArrayList<>(peers.keySet());
            String key = users.get(new Random().nextInt(users.size()));

            // add peer to neighbour list
            Peer neigh = peers.get(key);
            peer.join(new Neighbour(neigh.getPeerInfo().getHost()));
        }
    }

    private int execPost(String cmd, String[] opts) {
        if (opts.length < 3) return -1;

        // get peer
        String username = opts[1];
        Peer peer = peers.get(username);

        // split on ""
        String post_content = parsePostStr(cmd);
        if (post_content == null) return -1;

        // add post to timeline
        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        peer.addPost(post_content);
        return 0;
    }

    private String parsePostStr(String cmd) {
        // split on first ""
        String[] cmd_split = cmd.split("\"", 2);
        if(cmd_split.length < 2) return null;

        String content = cmd_split[1];
        return content.substring(0, content.length()-1); // remove last "
    }

    private int execStop(String[] opts) {
        if (opts.length < 2) return -1;

        // remove peer
        String username = opts[1];
        Peer peer = peers.remove(username);

        // stop peer
        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }
        executor.remNode(peer);
        return 0;
    }

    private int execStartMult(String[] opts) throws UnknownHostException {
        if (opts.length < 2) return -1;

        int num_peers = Integer.parseInt(opts[1]);
        InetAddress address = InetAddress.getByName("localhost");
        Random random = new Random();

        // create peers with random capacities and sequential ids
        for (int i = 1; i <= num_peers; i++) {
            String username = "user" + curr_peer_id;
            int capacity = 1 + random.nextInt(MAX_CAPACITY);

            // start and store peer
            Peer peer = new Peer(username, address, capacity);
            startPeer(username, peer);

            curr_peer_id++;
        }

        return 0;
    }

    private int execStopAll() {
        // clean map
        executor.stop();
        peers.clear();
        System.out.println("STOPPED all peers.");

        return 0;
    }

    private int execDelete(String[] opts) {
        if (opts.length < 3) return -1;

        // get peer
        String username = opts[1];
        int postId = Integer.parseInt(opts[2]);
        Peer peer = this.peers.get(username);

        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        // delete post
        peer.deletePost(postId);
        return 0;
    }

    private int execUpdate(String cmd, String[] opts) throws NumberFormatException {
        if (opts.length < 4) return -1;

        // get peer
        String username = opts[1];
        try {
            int postId = Integer.parseInt(opts[2]);

            // split on ""
            String newContent = parsePostStr(cmd);
            if (newContent == null) return -1;

            // get peer
            Peer peer = peers.get(username);
            if (peer == null) {
                System.err.println("ERROR: Peer not found.");
                return -1;
            }

            // update post
            peer.updatePost(postId, newContent);
        } catch (NumberFormatException e) {
            return -1;
        }
        return 0;
    }

    private int execTimeline(String[] opts) {
        // get peer
        String username = opts[1];
        String timeline = opts[2];
        Peer peer = peers.get(username);

        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Timeline t = peer.requestTimeline(timeline);
        System.out.println("Timeline received:\n" + t);

        return 0;
    }

    private int execSearch(String[] opts) {
        // get peer
        String username = opts[1];
        String search = opts[2];
        Peer peer = peers.get(username);

        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Set<Post> posts = peer.requestSearch(search);
        System.out.println("Search Results\n" + posts);
        return 0;
    }

    private int execSub(String[] opts) {
        String username = opts[1];
        String target_user = opts[2];

        Peer peer = peers.get(username);

        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        peer.requestSub(target_user);
        return 0;
    }

    private int execIgnore(String[] opts) {
        String message = opts[1];

        MessageSender.addIgnoredMsg(message);
        return 0;
    }

    private int execDelay(String[] opts) {
        Integer value = Integer.parseInt(opts[1]);

        MessageSender.addDelay(value);
        return 0;
    }

    private int execListen(String[] opts) {
        String username = opts[1];

        if (currPeerListening != null)
            currPeerListening.stopContentListening();

        currPeerListening = peers.get(username);
        currPeerListening.addContentListening();
        return 0;
    }


    private int execPrint(String[] opts) {
        if (opts.length < 2) return -1;

        // get peer
        String username = opts[1];
        Peer peer = peers.get(username);

        if (peer == null) {
            System.err.println("ERROR: Peer not found.");
            return -1;
        }

        // print timelines of peer
        peer.printTimelines();
        System.out.println("-------------------------------------------------------------");
        return 0;
    }

    private int execPrintPeers() {
        System.out.println("Online Peers: ");
        for (Peer peer : peers.values()) {
            System.out.println("\t" + peer);
        }
        return 0;
    }

    private int execSleep(String[] opts) throws InterruptedException {
        if (opts.length < 2) return -1;
        int time = Integer.parseInt(opts[1]) * 1000;
        Thread.sleep(time);
        return 0;
    }

    private int execBreakpoint() {
        System.out.println("Press enter...");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
}
