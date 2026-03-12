package com.synapxnet.aiopsk8sservice.exception;

public class K8sResourceNotFoundException extends RuntimeException {
    public K8sResourceNotFoundException(String message) {
        super(message);
    }
}
