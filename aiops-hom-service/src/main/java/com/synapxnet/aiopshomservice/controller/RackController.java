package com.synapxnet.aiopshomservice.controller;

import com.synapxnet.aiopshomservice.common.Result;
import com.synapxnet.aiopshomservice.entity.Rack;
import com.synapxnet.aiopshomservice.service.HostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hom/racks")
public class RackController {

    private final HostService hostService;

    public RackController(HostService hostService) {
        this.hostService = hostService;
    }

    @GetMapping
    public Result<List<Rack>> list(@RequestParam("clusterId") Long clusterId) {
        return Result.success(hostService.listRacks(clusterId));
    }

    @PostMapping
    public Result<Rack> create(@RequestBody Rack rack) {
        return Result.success(hostService.createRack(rack));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        hostService.deleteRack(id);
        return Result.success();
    }
}
