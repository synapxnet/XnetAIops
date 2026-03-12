package com.synapxnet.aiopshomservice.controller;

import com.synapxnet.aiopshomservice.common.Result;
import com.synapxnet.aiopshomservice.entity.Host;
import com.synapxnet.aiopshomservice.service.HostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hom/hosts")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    @GetMapping
    public Result<List<Host>> list(@RequestParam(value = "clusterId", required = false) Long clusterId) {
        if (clusterId != null) {
            return Result.success(hostService.listByClusterId(clusterId));
        }
        return Result.success(hostService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Host> get(@PathVariable("id") Long id) {
        return Result.success(hostService.getById(id));
    }

    @PostMapping
    public Result<Host> create(@RequestBody Host host) {
        return Result.success(hostService.create(host));
    }

    @PutMapping("/{id}")
    public Result<Host> update(@PathVariable("id") Long id, @RequestBody Host host) {
        host.setId(id);
        return Result.success(hostService.update(host));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        hostService.delete(id);
        return Result.success();
    }

    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, Object> params) {
        String host = (String) params.get("host");
        int port = params.get("port") != null ? ((Number) params.get("port")).intValue() : 22;
        String user = (String) params.getOrDefault("user", "root");
        String password = (String) params.get("password");
        return Result.success(hostService.testConnection(host, port, user, password));
    }
}
