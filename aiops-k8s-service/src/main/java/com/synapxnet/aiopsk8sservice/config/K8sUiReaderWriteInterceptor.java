/*
 * Copyright (C) 2026 Synapxnet. All rights reserved.
 * This file is Synapxnet Proprietary and Confidential. It is strictly
 * forbidden to copy, distribute, or use without explicit authorization.
 * 独立读取数据库写入拒绝器。 Database mutation denial for the isolated reader.
 * Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.3.0
 * Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
 */
package com.synapxnet.aiopsk8sservice.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public final class K8sUiReaderWriteInterceptor implements Interceptor {
    /** INSERT、UPDATE、DELETE 均经过 Executor.update，禁止调用下一层。 Blocks INSERT, UPDATE and DELETE through Executor.update without invoking downstream code. */
    @Override
    public Object intercept(Invocation invocation) {
        throw new IllegalStateException("K8S_UI_READER_DATABASE_WRITE_DENIED");
    }
}
