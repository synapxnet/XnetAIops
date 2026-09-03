package com.synapxnet.aiopsusrservice.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端组织选择器使用的最小树节点。
 */
public class OrganizationTreeNode {
    private final String label;
    private final String value;
    private final List<OrganizationTreeNode> children = new ArrayList<>();
    private boolean dataAccess;

    /**
     * 创建一个组织树节点。
     *
     * @param label 用户可见的组织名称
     * @param value 稳定的组织唯一编码
     */
    public OrganizationTreeNode(String label, String value) {
        this(label, value, false);
    }

    /**
     * 创建带业务数据访问标记的组织树节点。
     *
     * @param label 用户可见的组织名称
     * @param value 稳定的组织唯一编码
     * @param dataAccess 当前范围是否允许读取业务数据
     */
    public OrganizationTreeNode(String label, String value, boolean dataAccess) {
        this.label = label;
        this.value = value;
        this.dataAccess = dataAccess;
    }

    /**
     * 返回用户可见的组织名称。
     *
     * @return 组织名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 返回稳定的组织唯一编码。
     *
     * @return 组织唯一编码
     */
    public String getValue() {
        return value;
    }

    /**
     * 返回当前节点下已经授权的子组织。
     *
     * @return 可见子组织列表
     */
    public List<OrganizationTreeNode> getChildren() {
        return children;
    }

    /**
     * 返回当前组织范围是否具有业务数据访问权。
     *
     * @return 可以读取业务数据时返回 true
     */
    public boolean isDataAccess() {
        return dataAccess;
    }

    /**
     * 合并子节点的业务数据访问能力。
     *
     * @param enabled 要合并的数据访问标记
     */
    public void mergeDataAccess(boolean enabled) {
        dataAccess = dataAccess || enabled;
    }
}
