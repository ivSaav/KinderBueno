package main.model;

import main.gui.Observer;
import main.model.neighbour.Host;
import main.model.neighbour.Neighbour;
import main.model.timelines.Timeline;
import main.model.timelines.TimelineInfo;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Data class that serves like a Model in an MVC
public class PeerInfo {
    private Host me;
    private TimelineInfo timelineInfo;
    private Set<Neighbour> neighbours;
    private Set<Host> hostCache;
    private Observer observer;

    public PeerInfo(String username, InetAddress address, int capacity, TimelineInfo timelineInfo) {
        this.me = new Host(username, address, "-1", capacity, 0);
        this.timelineInfo = timelineInfo;
        this.neighbours = ConcurrentHashMap.newKeySet();
        this.hostCache = new HashSet<>();
    }

    public PeerInfo(InetAddress address, String username, int capacity) {
        this(username, address, capacity, new TimelineInfo(username));
    }

    // Neighbours

    public boolean hasNeighbour(Neighbour neighbour) {
        return neighbours.contains(neighbour);
    }

    public void replaceNeighbour(Neighbour oldNeigh, Neighbour newNeigh) {
        this.removeNeighbour(oldNeigh);
        this.addNeighbour(newNeigh);
    }

    public void updateNeighbour(Neighbour updated) {
        neighbours.remove(updated);
        neighbours.add(updated);
    }

    public void updateHostCache(Set<Host> hostCache) {
        Set<Host> filterOurselvesOut = hostCache.stream().filter(
                host -> !host.equals(this.getHost())
        ).collect(Collectors.toSet());

        this.hostCache.addAll(filterOurselvesOut);
    }

    public void addNeighbour(Neighbour neighbour) {
        if (neighbour.equals(this.me)) // We can't add ourselves as a neighbour
            return;

        System.out.println(this.me.getUsername() + " ADDED " + neighbour.getUsername());
        neighbours.add(neighbour);
        this.me.setDegree(neighbours.size());
        hostCache.add(neighbour); // Everytime we add a neighbour, we also add to the hostcache

        if (this.observer != null)
            this.observer.newEdgeUpdate(this.getUsername(), neighbour.getUsername());

    }

    public void removeNeighbour(Neighbour neighbour) {
        if (!neighbours.contains(neighbour))
            return;

        neighbours.remove(neighbour);
        System.out.println(this.getUsername() + " REMOVED " + neighbour.getUsername());
        this.me.setDegree(neighbours.size());

        if (this.observer != null)
            this.observer.removeEdgeUpdate(this.getUsername(), neighbour.getUsername());
    }

    public Neighbour getWorstNeighbour(int hostCapacity) {
        // get neighbors with less capacity than host
        List<Neighbour> badNgbrs = neighbours.stream()
                .filter(n -> n.getCapacity() <= hostCapacity).toList();
        if (badNgbrs.isEmpty()) return null; // REJECT host if there are no worse neighbours

        // from neighbours with less capacity than host, get the one with max degree
        return badNgbrs.stream().min(Host::compareTo).get();
    }

    public Neighbour getBestNeighbour() { // With highest capacity
        return neighbours.stream().max(Host::compareTo).get();
    }

    public Set<Neighbour> getNeighboursWithTimeline(String username) {
        for (Neighbour n: neighbours)
            System.out.println(n.getUsername());
        return neighbours.stream().filter(n -> n.hasTimeline(username)).collect(Collectors.toSet());
    }

    // HostCache

    public void addHost(Host host) {

        if (hostCache.contains(host) || this.me.equals(host))
            return;

        hostCache.add(host);
    }

    public void removeHost(Host host) {
        if (!hostCache.contains(host))
            return;

        hostCache.remove(host);
    }

    public Host getBestHostNotNeighbour() {
        // filter already neighbors
        Set<Host> notNeighbors = hostCache.stream()
                .filter(f -> !neighbours.contains(f))
                .collect(Collectors.toSet());


        Optional<Host> best_host = notNeighbors.stream().max(Host::compareTo);
        if(best_host.isEmpty()) return null;
        return best_host.get();
    }

    // observers
    public void subscribe(Observer o) {
        this.observer = o;
        this.observer.newNodeUpdate(this.getUsername(), this.getCapacity());
    }

    // getters

    public String getUsername() {
        return this.me.getUsername();
    }

    public InetAddress getAddress() {
        return this.me.getAddress();
    }

    public String getPort() {
        return this.me.getPort();
    }

    public Host getHost() {
        return this.me;
    }

    public int getCapacity() {
        return this.me.getCapacity();
    }

    public Set<Neighbour> getNeighbours() { return neighbours; }

    public Set<Host> getHostCache() {
        return this.hostCache;
    }

    public Integer getDegree() {
        return me.getDegree();
    }

    public TimelineInfo getTimelineInfo() {
        return timelineInfo;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return Objects.equals(me, peerInfo.me);
    }

    public void setPort(String port) {
        this.me.setPort(port);
    }
}