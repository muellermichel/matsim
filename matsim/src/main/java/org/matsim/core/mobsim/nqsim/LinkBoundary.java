package org.matsim.core.mobsim.nqsim;

public class LinkBoundary {

    // Id of the Link inside the owner realm (from realm).
    private final int id;
    // Source real id.
    private final int fromRealm;
    // Destination real id.
    private final int toRealm;
    
    public LinkBoundary(int id, int fromRealm, int toRealm) {
        this.id = id;
        this.fromRealm = fromRealm;
        this.toRealm = toRealm;
    }

    public int id() {
        return this.id;
    }

    public int fromrealm() {
        return this.fromRealm;
    }

    public int torealm() {
        return this.toRealm;
    }
}