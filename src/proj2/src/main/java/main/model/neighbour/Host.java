package main.model.neighbour;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Objects;

// Data class
public class Host implements Serializable, Comparable<Host> {
    private final InetAddress address;
    private String port;
    private final int capacity; // Quantity of messages that we can handle, arbitrary for us
    // needed to add as neighbor
    private final String username;
    private int degree;
    private int maxNbrs;

    public Host(String username, InetAddress address, String port, int capacity, int degree, int maxNbrs) {
        this.address = address;
        this.port = port;
        this.capacity = capacity;
        this.username = username;
        this.degree = degree;
        this.maxNbrs = maxNbrs;
    }

    public Host(Host host) {
        this(host.username, host.address, host.port, host.capacity, host.degree, host.maxNbrs);
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getPort() {
        return port;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getDegree() {
        return degree;
    }

    public String getUsername() { return username; }

    public int getMaxNbrs() { return maxNbrs; }

    public void setDegree(int size) {
        this.degree = size;
    }

    public void setPort(String port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "Username: " + username + " IP: " + address.getHostName() +
                ":" + port + " Cap: " + capacity + " Deg: " + degree;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Host host)) return false;
        return Objects.equals(address, host.address) && Objects.equals(port, host.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }

    @Override
    public int compareTo(Host host) {
        return Integer.compare(degree, host.degree);
    }
}
