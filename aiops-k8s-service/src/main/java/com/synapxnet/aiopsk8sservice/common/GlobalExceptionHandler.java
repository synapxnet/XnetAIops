package com.synapxnet.aiopsk8sservice.common;

import com.synapxnet.aiopsk8sservice.exception.ClusterConnectionException;
import com.synapxnet.aiopsk8sservice.exception.K8sResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(ClusterConnectionException.class)
    public Result<?> handleClusterConnection(ClusterConnectionException e) {
        log.error("Cluster connection error: {}", e.getMessage());
        return Result.error(503, "Cluster connection failed: " + e.getMessage());
    }

    @ExceptionHandler(K8sResourceNotFoundException.class)
    public Result<?> handleResourceNotFound(K8sResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return Result.error(404, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Internal error: {}", e.getMessage(), e);
        return Result.error(500, "Internal server error: " + e.getMessage());
    }
}
