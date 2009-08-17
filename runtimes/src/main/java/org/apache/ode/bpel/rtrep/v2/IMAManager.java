package org.apache.ode.bpel.rtrep.v2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.common.CorrelationKey;
import org.apache.ode.utils.ObjectPrinter;

/**
 * Manages the state for IMA's and provides the ability to detect
 * conflicitingReceive and conflicitingRequest standard faults.
 */
public class IMAManager implements Serializable {
    private static final long serialVersionUID = -6550688645318311278L;
    
    private static final Log __log = LogFactory.getLog(IMAManager.class);

    // Represents an IMA that is waiting for a request
    private final Map<RequestIdTuple, Entry> _byRequest = new HashMap<RequestIdTuple, Entry>();
    // Represents an IMA that has received a request and now is waiting to reply
    private final Map<ReplyIdTuple, Entry> _byReply = new HashMap<ReplyIdTuple, Entry>();
    private final Map<String, Entry> _byChannel = new HashMap<String, Entry>();

    int findConflict(Selector selectors[]) {
        if (__log.isTraceEnabled()) {
            __log.trace(ObjectPrinter.stringifyMethodEnter("findConflict", new Object[] { "selectors", selectors}) );
        }

        Set<RequestIdTuple> workingSet = new HashSet<RequestIdTuple>(_byRequest.keySet());
        for (int i = 0; i < selectors.length; i++) {
            final RequestIdTuple rid = new RequestIdTuple(selectors[i].plinkInstance,selectors[i].opName, selectors[i].correlationKey);
            if (workingSet.contains(rid)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Register a receive/pick with the manager. This occurs when the receive/pick is encountered in the processing of
     * the BPEL script.
     * @param pickResponseChannel response channel associated with this receive/pick
     * @param selectors selectors for this receive/pick
     */
    void register(String pickResponseChannel, Selector selectors[]) {
        if (__log.isTraceEnabled()) {
            __log.trace(ObjectPrinter.stringifyMethodEnter("register", new Object[] {
                    "pickResponseChannel", pickResponseChannel,
                    "selectors", selectors
            }) );
        }

        if (_byChannel.containsKey(pickResponseChannel)) {
            String errmsg = "INTERNAL ERROR: Duplicate ENTRY for RESPONSE CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalArgumentException(errmsg);
        }

        Entry entry = new Entry(pickResponseChannel, selectors);
        for (Selector selector : selectors) {
            final RequestIdTuple rid = new RequestIdTuple(selector.plinkInstance, selector.opName, selector.correlationKey);
            if (_byRequest.containsKey(rid)) {
                String errmsg = "INTERNAL ERROR: Duplicate ENTRY for REQUEST " + rid;
                __log.fatal(errmsg);
                throw new IllegalStateException(errmsg);
            }
            _byRequest.put(rid, entry);
        }

        _byChannel.put(pickResponseChannel, entry);
    }

    /**
     * Cancel a previous registration.
     * 
     * @see #register(String, Selector[])
     * @param pickResponseChannel
     */
    void cancel(String pickResponseChannel) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("cancel", new Object[] {
                    "pickResponseChannel", pickResponseChannel
            }) );

        Entry entry = _byChannel.remove(pickResponseChannel);
        if (entry != null) {
            while(_byRequest.values().remove(entry));
            while(_byReply.values().remove(entry));
        }
    }

    Selector findAssociate(String pickResponseChannel, String mexRef, int selectorIndex) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("findAssociate", new Object[] {
                    "pickResponseChannel", pickResponseChannel,
                    "mexRef", mexRef
            }) );
        
        Entry entry = _byChannel.get(pickResponseChannel);
        if (entry == null) {
            String errmsg = "INTERNAL ERROR: No ENTRY for RESPONSE CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalArgumentException(errmsg);
        }
        Selector selected = entry.selectors[selectorIndex];
        entry = _byReply.get(new ReplyIdTuple(selected.plinkInstance, selected.opName, selected.messageExchangeId));
        return entry == null ? null : entry.selectors[selectorIndex];
    }
    
    /**
     * Associate a message exchange with a registered receive/pick. This happens when a message corresponding to the
     * receive/pick is received by the system.
     * 
     * @param pickResponseChannel
     * @param mexRef
     */
    void associate(String pickResponseChannel, String mexRef, int selectorIndex) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("associate", new Object[] {
                    "pickResponseChannel", pickResponseChannel,
                    "mexRef", mexRef
            }) );

        Entry entry = _byChannel.get(pickResponseChannel);
        if (entry == null) {
            String errmsg = "INTERNAL ERROR: No ENTRY for RESPONSE CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalArgumentException(errmsg);
        }

        if (entry.mexRef != null) {
            String errmsg = "INTERNAL ERROR: Duplicate ASSOCIATION for CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalStateException(errmsg);
        }

        entry.mexRef = mexRef;
        // We have been associated with a message exchange and therefore need to
        // move into the waiting for reply state
        for (int i=0; i <entry.selectors.length; i++) {
            final RequestIdTuple request = new RequestIdTuple(entry.selectors[i].plinkInstance, entry.selectors[i].opName, entry.selectors[i].correlationKey);
            entry = _byRequest.remove(request);
            if (entry == null) {
                String errmsg = "INTERNAL ERROR: No ENTRY for REQUEST " + request;
                __log.fatal(errmsg);
                throw new IllegalArgumentException(errmsg);
            }
            // Only the selector we are receiving on should be moved into waiting for reply state
            if (i == selectorIndex) {
                _byReply.put(new ReplyIdTuple(entry.selectors[i].plinkInstance, entry.selectors[i].opName, entry.selectors[i].messageExchangeId), entry);
            }
        }
    }

    /**
     * Release the registration. This method is called when the reply activity sends a reply corresponding to the
     * registration.
     * 
     * @param plinkInstnace partner link
     * @param opName operation
     * @param mexId message exchange identifier IN THE BPEL SENSE OF THE TERM (i.e. a receive/reply disambiguator).
     * @return message exchange identifier associated with the registration that matches the parameters
     */
    public String release(PartnerLinkInstance plinkInstnace, String opName, String mexId) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("release", new Object[] {
                    "plinkInstance", plinkInstnace,
                    "opName", opName,
                    "mexId", mexId
            }) );

        final ReplyIdTuple rid = new ReplyIdTuple(plinkInstnace,opName, mexId);
        Entry entry = _byReply.get(rid);
        if (entry == null) {
            if (__log.isDebugEnabled()) {
                __log.debug("==release: RID " + rid + " not found in " + _byReply);
            }
            return null;
        }
        while(_byChannel.values().remove(entry));
        while(_byReply.values().remove(entry));
        return entry.mexRef;
    }

    /**
     * "Release" all outstanding incoming messages exchanges. Makes the object forget about
     * the previous registrations
     * 
     * @return a list of message exchange identifiers for message exchanges that were begun (receive/pick got a message)
     *            but not yet completed (reply not yet sent)
     */
    public String[] releaseAll() {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("releaseAll", null) );

        ArrayList<String> mexRefs = new ArrayList<String>();
        for (Entry entry : _byChannel.values()) {
            if (entry.mexRef!=null)
                mexRefs.add(entry.mexRef);
        }
        _byChannel.values().clear();
        _byRequest.values().clear();
        _byReply.values().clear();
        return mexRefs.toArray(new String[mexRefs.size()]);
    }

    public String toString() {
        return ObjectPrinter.toString(this, new Object[] {
                "_byRequest", _byRequest,
                "byReply", _byReply,
                "byChannel", _byChannel
        });
    }
    
    
    /**
     * Tuple identifying an outstanding request (i.e. a receive,pick, or onMessage).
     */
    private class RequestIdTuple  implements Serializable {
        private static final long serialVersionUID = -5371415442414739887L;
        /** On which partner link it was received. */
        PartnerLinkInstance partnerLink;
        /** Name of the operation. */
        String opName;
        /** Correlation key identifier. */
        CorrelationKey correlationKey;

        /** Constructor. */
        private RequestIdTuple(PartnerLinkInstance partnerLink, String opName, CorrelationKey correlationKey) {
            this.partnerLink = partnerLink;
            this.opName = opName;
            this.correlationKey = correlationKey;
        }

        public int hashCode() {
            return this.partnerLink.hashCode() ^ this.opName.hashCode() ^ this.correlationKey.hashCode();
        }

        public boolean equals(Object obj) {
            RequestIdTuple other = (RequestIdTuple) obj;
            return other.partnerLink.equals(partnerLink) &&
                    other.opName.equals(opName) &&
                    other.correlationKey.equals(correlationKey);
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] {
                    "partnerLink", partnerLink,
                    "opName", opName,
                    "correlationKey", correlationKey
            });
        }
    }
    
    /**
     * Tuple identifying an outstanding reply (i.e. a reply).
     */
    private class ReplyIdTuple  implements Serializable {
        private static final long serialVersionUID = -2993419819851933718L;
        /** On which partner link it was received. */
        PartnerLinkInstance partnerLink;
        /** Name of the operation. */
        String opName;
        /** Message exchange identifier. */
        String mexId;

        /** Constructor. */
        private ReplyIdTuple(PartnerLinkInstance partnerLink, String opName, String mexId) {
            this.partnerLink = partnerLink;
            this.opName = opName;
            this.mexId = mexId == null ? "" : mexId;
        }

        public int hashCode() {
            return this.partnerLink.hashCode() ^ this.opName.hashCode() ^ this.mexId.hashCode();
        }

        public boolean equals(Object obj) {
            ReplyIdTuple other = (ReplyIdTuple) obj;
            return other.partnerLink.equals(partnerLink) &&
                    other.opName.equals(opName) &&
                    other.mexId.equals(mexId);
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] {
                    "partnerLink", partnerLink,
                    "opName", opName,
                    "mexId", mexId
            });
        }
    }
    
    private class Entry implements Serializable {
        private static final long serialVersionUID = 4504047787387625899L;
        
        final String pickResponseChannel;
        final Selector[] selectors;
        String mexRef;

        private Entry(String pickResponseChannel, Selector[] selectors) {
            this.pickResponseChannel = pickResponseChannel;
            this.selectors = selectors;
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] {
                    "pickResponseChannel", pickResponseChannel,
                    "selectors", selectors,
                    "mexRef", mexRef
            });
        }
    }
}