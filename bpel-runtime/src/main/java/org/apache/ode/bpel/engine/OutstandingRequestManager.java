/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.engine;

import org.apache.ode.bpel.runtime.PartnerLinkInstance;
import org.apache.ode.bpel.runtime.Selector;
import org.apache.ode.utils.ObjectPrinter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.wsdl.OperationType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * Manages receive/pick--reply matching. Keeps track of active pick/receive activities (i.e. those that have been reached in the script) and their association with a message exchange (for those receive/picks that have received a message). The purpose of this class is to 1) enable matching a reply activity to the corresponding receive/pick activity and 2) allow us to fault out message exchanges that have not been replied to when they go out of scope.
 * </p>
 * <p>
 * Note, this class is only used for INBOUND synchronous (request-response) operations. None of this is necessary for asynchronous messages.
 * </p>
 */
public class OutstandingRequestManager implements Serializable {
    private static final long serialVersionUID = -5556374398943757951L;

    private static final Log __log = LogFactory.getLog(OutstandingRequestManager.class);

    public final Map<RequestIdTuple, Entry> _byRid = new HashMap<RequestIdTuple, Entry>();
    // holds outstanding rid that are now waiting to reply
    public final Map<OutstandingRequestIdTuple, Entry> _byOrid = new HashMap<OutstandingRequestIdTuple, Entry>();
    public final Map<String, Entry> _byChannel = new HashMap<String, Entry>();

    //finds conflictingReceive
    int findConflict(Selector selectors[]) {
        if (__log.isTraceEnabled()) {
            __log.trace(ObjectPrinter.stringifyMethodEnter("findConflict", new Object[] { "selectors", selectors }));
        }

        Set<RequestIdTuple> workingSet = new HashSet<RequestIdTuple>(_byRid.keySet());
        for (int i = 0; i < selectors.length; ++i) {
            final RequestIdTuple rid = new RequestIdTuple(selectors[i].plinkInstance, selectors[i].opName);
            if (workingSet.contains(rid)) {
                return i;
            }
            workingSet.add(rid);
        }
        return -1;
    }

    /**
     * Register a receive/pick with the manager. This occurs when the receive/pick is encountered in the processing of the BPEL script.
     * 
     * @param pickResponseChannel
     *            response channel associated with this receive/pick
     * @param selectors
     *            selectors for this receive/pick
     */
    void register(String pickResponseChannel, Selector selectors[]) {
        if (__log.isTraceEnabled()) {
            __log.trace(ObjectPrinter.stringifyMethodEnter("register", new Object[] { "pickResponseChannel", pickResponseChannel, "selectors", selectors }));
        }

        if (_byChannel.containsKey(pickResponseChannel)) {
            String errmsg = "INTERNAL ERROR: Duplicate ENTRY for RESPONSE CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalArgumentException(errmsg);
        }

        Entry entry = new Entry(pickResponseChannel, selectors);
        for (int i = 0; i < selectors.length; ++i) {
            final RequestIdTuple rid = new RequestIdTuple(selectors[i].plinkInstance, selectors[i].opName);
            if (_byRid.containsKey(rid)) {
                String errmsg = "INTERNAL ERROR: Duplicate ENTRY for RID " + rid;
                __log.fatal(errmsg);
                throw new IllegalStateException(errmsg);
            }
            _byRid.put(rid, entry);
        }

        _byChannel.put(pickResponseChannel, entry);
    }

    //convert registered request into outstanding
    String processOutstandingRequest(PartnerLinkInstance partnerLink, String opName, String mexId) {
        if (__log.isTraceEnabled()) {
            __log.trace(ObjectPrinter.stringifyMethodEnter("process", new Object[] { "partnerLinkInstance", partnerLink, "operationName", opName, "messageExchangeId", mexId }));
        }
        final RequestIdTuple rid = new RequestIdTuple(partnerLink, opName);
        final OutstandingRequestIdTuple orid = new OutstandingRequestIdTuple(partnerLink, opName, mexId);
        Entry entry = _byRid.get(rid);
        if (entry == null) {
            String errmsg = "INTERNAL ERROR: Missing ENTRY for RID " + rid;
            __log.fatal(errmsg);
            throw new IllegalStateException(errmsg);
        }
        _byRid.remove(rid);
        if (_byOrid.containsKey(orid)) {
            //conflictingRequest found
            return entry.mexRef;
        }
        // We convert into outstanding request only for in-out operations (pending release operation)
        if (partnerLink.partnerLink.getMyRoleOperation(opName).getStyle().equals(OperationType.REQUEST_RESPONSE)) {
            _byOrid.put(orid, entry);
        }
        return null;
    }

    /**
     * Cancel a previous registration.
     * 
     * @see #register(String, Selector[])
     * @param pickResponseChannel
     */
    void cancel(String pickResponseChannel) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("cancel", new Object[] { "pickResponseChannel", pickResponseChannel }));

        Entry entry = _byChannel.remove(pickResponseChannel);
        if (entry != null) {
            while (_byRid.values().remove(entry));
            while (_byOrid.values().remove(entry));
        }
    }

    /**
     * Associate a message exchange with a registered receive/pick. This happens when a message corresponding to the receive/pick is received by the system.
     * 
     * @param pickResponseChannel
     * @param mexRef
     */
    void associate(String pickResponseChannel, String mexRef) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("associate", new Object[] { "pickResponseChannel", pickResponseChannel, "mexRef", mexRef }));

        Entry entry = _byChannel.get(pickResponseChannel);
        if (entry == null) {
            String errmsg = "INTERNAL ERROR: No ENTRY for RESPONSE CHANNEL " + pickResponseChannel;
            __log.fatal(errmsg);
            throw new IllegalArgumentException(errmsg);
        }

        // For pub-sub cases, an entry may already be associated with a message
        // Hence, the sanity check shown below is no longer valid
        // if (entry.mexRef != null) {
        // String errmsg = "INTERNAL ERROR: Duplicate ASSOCIATION for CHANEL " + pickResponseChannel;
        // __log.fatal(errmsg);
        // throw new IllegalStateException(errmsg);
        // }

        entry.mexRef = mexRef;
    }

    /**
     * Release the registration. This method is called when the reply activity sends a reply corresponding to the registration.
     * 
     * @param plinkInstnace
     *            partner link
     * @param opName
     *            operation
     * @param mexId
     *            message exchange identifier IN THE BPEL SENSE OF THE TERM (i.e. a receive/reply disambiguator).
     * @return message exchange identifier associated with the registration that matches the parameters
     */
    public String release(PartnerLinkInstance plinkInstnace, String opName, String mexId) {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("release", new Object[] { "plinkInstance", plinkInstnace, "opName", opName, "mexId", mexId }));

        final OutstandingRequestIdTuple orid = new OutstandingRequestIdTuple(plinkInstnace, opName, mexId);
        Entry entry = _byOrid.get(orid);
        if (entry == null) {
            entry = _byRid.get(orid);
        }
        if (entry == null) {
            if (__log.isDebugEnabled()) {
                __log.debug("==release: ORID " + orid + " not found in " + _byOrid);
            }
            return null;
        }
        while (_byChannel.values().remove(entry));
        while (_byOrid.values().remove(entry));
        return entry.mexRef;
    }

    /**
     * "Release" all outstanding incoming messages exchanges. Makes the object forget about the previous registrations
     * 
     * @return a list of message exchange identifiers for message exchanges that were begun (receive/pick got a message) but not yet completed (reply not yet sent)
     */
    public String[] releaseAll() {
        if (__log.isTraceEnabled())
            __log.trace(ObjectPrinter.stringifyMethodEnter("releaseAll", null));

        ArrayList<String> mexRefs = new ArrayList<String>();
        while (!_byOrid.isEmpty()) {
            Entry entry = _byOrid.entrySet().iterator().next().getValue();
            mexRefs.add(entry.mexRef);
            _byChannel.values().remove(entry);
            _byOrid.values().remove(entry);
        }
        return mexRefs.toArray(new String[mexRefs.size()]);
    }

    public String toString() {
        return ObjectPrinter.toString(this, new Object[] { "byRid", _byRid, "byOrid", _byOrid, "byChannel", _byChannel });
    }

    private class RequestIdTuple implements Serializable {
        private static final long serialVersionUID = -1059389611839777482L;
        /** On which partner link it was received. */
        PartnerLinkInstance partnerLink;
        /** Name of the operation. */
        String opName;

        /** Constructor. */
        private RequestIdTuple(PartnerLinkInstance partnerLink, String opName) {
            this.partnerLink = partnerLink;
            this.opName = opName;
        }

        public int hashCode() {
            return this.partnerLink.hashCode() ^ this.opName.hashCode();
        }

        public boolean equals(Object obj) {
            RequestIdTuple other = (RequestIdTuple) obj;
            return other.partnerLink.equals(partnerLink) && other.opName.equals(opName);
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] { "partnerLink", partnerLink, "opName", opName});
        }
    }

    private class OutstandingRequestIdTuple implements Serializable {
        private static final long serialVersionUID = -1059389611839777482L;
        /** On which partner link it was received. */
        PartnerLinkInstance partnerLink;
        /** Name of the operation. */
        String opName;
        /** Message exchange identifier. */
        String mexId;

        /** Constructor. */
        private OutstandingRequestIdTuple(PartnerLinkInstance partnerLink, String opName, String mexId) {
            this.partnerLink = partnerLink;
            this.opName = opName;
            this.mexId = mexId == null ? "" : mexId;
        }

        public int hashCode() {
            return this.partnerLink.hashCode() ^ this.opName.hashCode() ^ this.mexId.hashCode();
        }

        public boolean equals(Object obj) {
            OutstandingRequestIdTuple other = (OutstandingRequestIdTuple) obj;
            return other.partnerLink.equals(partnerLink) && other.opName.equals(opName) && other.mexId.equals(mexId);
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] { "partnerLink", partnerLink, "opName", opName, "mexId", mexId });
        }
    }

    public class Entry implements Serializable {
        private static final long serialVersionUID = -583743124656582887L;
        final String pickResponseChannel;
        public Object[] selectors;
        String mexRef;

        private Entry(String pickResponseChannel, Selector[] selectors) {
            this.pickResponseChannel = pickResponseChannel;
            this.selectors = selectors;
        }

        public String toString() {
            return ObjectPrinter.toString(this, new Object[] { "pickResponseChannel", pickResponseChannel, "selectors", selectors, "mexRef", mexRef });
        }
    }
}
