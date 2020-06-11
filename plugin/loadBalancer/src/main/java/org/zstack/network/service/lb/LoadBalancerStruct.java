package org.zstack.network.service.lb;

import org.zstack.header.vm.VmNicInventory;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by frank on 8/8/2015.
 */
public class LoadBalancerStruct implements Serializable {
    private LoadBalancerInventory lb;
    private Map<String, VmNicInventory> vmNics;
    private List<LoadBalancerListenerInventory> listeners;
    private Map<String, List<String>> tags;
    private boolean init;

    public Map<String, VmNicInventory> getVmNics() {
        return vmNics;
    }

    public boolean isInit() {
        return init;
    }

    public void setInit(boolean init) {
        this.init = init;
    }

    public Map<String, List<String>> getTags() {
        return tags;
    }

    public void setTags(Map<String, List<String>> tags) {
        this.tags = tags;
    }

    public List<LoadBalancerListenerInventory> getListeners() {
        return listeners;
    }

    public void setListeners(List<LoadBalancerListenerInventory> listeners) {
        this.listeners = listeners;
    }

    public LoadBalancerInventory getLb() {
        return lb;
    }

    public void setLb(LoadBalancerInventory lb) {
        this.lb = lb;
    }

    public void setVmNics(Map<String, VmNicInventory> vmNics) {
        this.vmNics = vmNics;
    }
}
