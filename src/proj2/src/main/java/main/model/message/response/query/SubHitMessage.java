package main.model.message.response.query;

import java.net.InetAddress;
import java.util.UUID;

public class SubHitMessage extends QueryResponseImpl {
    public static final String type = "SUB_HIT";
    private final String publishPort;
    private final String port;
    private final InetAddress address;

    public SubHitMessage(UUID id, String port, String publishPort, InetAddress address) {
        super(id);
        this.port = port;
        this.publishPort = publishPort;
        this.address = address;
    }

    public String getPort() {
        return port;
    }

    public String getPublishPort() {
        return publishPort;
    }

    public InetAddress getAddress() {
        return address;
    }

    @Override
    public String getType() {
        return type;
    }
}
