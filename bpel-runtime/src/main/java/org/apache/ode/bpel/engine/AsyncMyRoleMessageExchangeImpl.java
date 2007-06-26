package org.apache.ode.bpel.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.iapi.MessageExchange.Status;

/**
 * For invoking the engine using ASYNC style.
 * 
 * @author Maciej Szefler
 * 
 */
public class AsyncMyRoleMessageExchangeImpl extends MyRoleMessageExchangeImpl {
    private static final Log __log = LogFactory.getLog(ReliableMyRoleMessageExchangeImpl.class);

    private static Map<String, ResponseFuture> _waitingFutures = new ConcurrentHashMap<String, ResponseFuture>();

    public AsyncMyRoleMessageExchangeImpl(BpelEngineImpl engine, String mexId) {
        super(engine, mexId);
    }

    public Future<Status> invokeAsync() {
        ResponseFuture future = new ResponseFuture();

        BpelProcess target = _engine.route(_callee, _request);
        if (target == null) {
            if (__log.isWarnEnabled())
                __log.warn(__msgs.msgUnknownEPR("" + _epr));

            _cstatus = CorrelationStatus.UKNOWN_ENDPOINT;
            setFailure(FailureType.UNKNOWN_ENDPOINT, null, null);
            future.done(_status);

            return future;
        }

        scheduleInvoke(target);
      
        if (getOperation().getOutput() != null) {
            _waitingFutures.put(getMessageExchangeId(), future);
        } else {
            future.done(getStatus());
        }

        return future;

    }

    private static class ResponseFuture implements Future<Status> {
        private Status _status;

        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        public Status get() throws InterruptedException, ExecutionException {
            try {
                return get(0, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // If it's thrown it's definitely a bug
                throw new RuntimeException(e);
            }
        }

        public Status get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

            synchronized (this) {
                if (_status != null)
                    return _status;

                while (_status == null) {
                    this.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
                }

                if (_status == null)
                    throw new TimeoutException();

                return _status;
            }
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return _status != null;
        }

        void done(Status status) {
            synchronized (this) {
                _status = status;
                this.notifyAll();
            }
        }
    }

}