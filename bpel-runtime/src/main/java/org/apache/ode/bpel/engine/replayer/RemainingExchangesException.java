package org.apache.ode.bpel.engine.replayer;

import java.util.List;

import org.apache.ode.bpel.pmapi.CommunicationType.Exchange;

/**
 * It's raised when replayed invokes don't drain out all provided communication.
 * For example if there is invoke in process and two requests in provided communication, this 
 * exception occurs.   
 * 
 * @author Rafal Rusin
 *
 */
public class RemainingExchangesException extends Exception {
    private static final long serialVersionUID = 1L;

    public final List<Exchange> remainingExchanges;

    public RemainingExchangesException(List<Exchange> remainingExchanges) {
        this.remainingExchanges = remainingExchanges;
    }

    @Override
    public String getMessage() {
        return "Remaining exchanges: " + remainingExchanges.toString();
    }
}
