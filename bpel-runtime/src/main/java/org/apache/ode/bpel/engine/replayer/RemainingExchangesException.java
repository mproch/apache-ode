package org.apache.ode.bpel.engine.replayer;

import java.util.List;

import org.apache.ode.bpel.pmapi.CommunicationType.Exchange;

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
