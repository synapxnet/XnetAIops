/* -*- coding: utf-8 -*-
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * Kubernetes 资源单位读取。 / Kubernetes resource quantity conversion.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.service;

import io.fabric8.kubernetes.api.model.Quantity;
import java.math.BigDecimal;

public final class K8sResourceQuantity {
    /** 禁止实例化无状态转换器。 / Prevent instantiation of the stateless converter. */
    private K8sResourceQuantity() {
    }

    /** 按 Fabric8 的 amount 与 format 一起读取资源基准量。 / Read the base quantity using both Fabric8 amount and format. */
    private static BigDecimal baseAmount(Quantity quantity) {
        if (quantity == null) return BigDecimal.ZERO;
        BigDecimal value = quantity.getNumericalAmount();
        if (value.signum() < 0) throw new IllegalArgumentException("Resource quantity must not be negative");
        return value;
    }

    /** 将 n/u/m 或普通 CPU Quantity 转换为核数，不猜单位或截断。 / Convert n/u/m or plain CPU quantities to cores without guessing or clamping. */
    public static double cpuCores(Quantity quantity) {
        double cores = baseAmount(quantity).doubleValue();
        if (!Double.isFinite(cores)) throw new IllegalArgumentException("CPU quantity is not finite");
        return cores;
    }

    /** 将内存与存储的二进制或十进制 Quantity 转换为字节。 / Convert binary or decimal memory/storage quantities to bytes. */
    public static long bytes(Quantity quantity) {
        return baseAmount(quantity).longValueExact();
    }

    /** 保留既有节点字符串字段类型，并明确使用核数。 / Preserve the node string field type while expressing CPU in cores. */
    public static String cpuCoresText(Quantity quantity) {
        return baseAmount(quantity).stripTrailingZeros().toPlainString();
    }
}
