package main.controller.message;

import main.model.PeerInfo;
import main.model.message.Message;
import main.model.message.request.MessageRequest;
import main.model.message.request.PingMessage;
import main.model.message.request.QueryMessage;
import main.model.message.response.MessageResponse;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MessageSender {
    // Each Peer has a MessageSender, and it sends all messages through it
    private String username;
    private final ZContext context;
    private int maxRetries;
    private int receiveTimeout;

    // Used for testng
    private static List<String> ignoredMessages = new ArrayList<>();
    public static void removeIgnoredMsg(String msgType) {
        ignoredMessages.remove(msgType);
    }
    public static void addIgnoredMsg(String msgType) {
        ignoredMessages.add(msgType);
    }

    public MessageSender(String username, int maxRetries, int receiveTimeout, ZContext context) {
        this.username = username;
        this.maxRetries = maxRetries;
        this.receiveTimeout = receiveTimeout;
        this.context = context;
    }

    public MessageSender(PeerInfo peerInfo, int maxRetries, int receiveTimeout, ZContext context){
        this(peerInfo.getUsername(), maxRetries, receiveTimeout, context);
    }

    public void send(Message message, ZMQ.Socket socket) {
        byte[] bytes = new byte[0];
        try {
            bytes = MessageBuilder.messageToByteArray(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket.send(bytes);
    }

    private String sendRequest(MessageRequest message, String port) {
        ZMQ.Socket socket = context.createSocket(SocketType.REQ);
        socket.setReceiveTimeOut(receiveTimeout);
        socket.connect("tcp://localhost:" + port); // TODO convert to address

        this.send(message, socket);
        System.out.println(username + " SENT[" + message.getType() + "]: " + port);

        String res = socket.recvStr();

        socket.close();
        context.destroySocket(socket);
        return res;
    }


    public boolean sendRequestNTimes(MessageRequest message, String port) {
        int i = 0;
        boolean done = false;
        while (i < this.maxRetries && !done) {
            String response = this.sendRequest(message, port);
            if (response != null && response.equals("OK")) {
                done = true;
            }
            ++i;
        }

        if (!done) {
            System.out.println("Failed getting response to [" + message.getType() + "]" + port);
            return false;
        }
        return true;
    }
}
