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

    /**
     * 创建一个组织树节点。
     *
     * @param label 用户可见的组织名称
     * @param value 稳定的组织唯一编码
     */
    public OrganizationTreeNode(String label, String value) {
        this.label = label;
        this.value = value;
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
}
