package com.synapxnet.aiopsk8sservice.exception;

public class ClusterConnectionException extends RuntimeException {
    public ClusterConnectionException(String message) {
        super(message);
    }

    public ClusterConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
