package org.zstack.header.vm;

import com.google.common.collect.Maps;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.Map;

/**
 * @ Author : yh.w
 * @ Date   : Created in 10:29 2021/2/3
 */
@RestResponse(allTo = "vmsCaps")
public class APIGetVmsCapabilitiesReply extends APIReply {

    private Map<String, VmCapabilities> vmsCaps;

    public Map<String, VmCapabilities> getVmsCaps() {
        return vmsCaps;
    }

    public void setVmsCaps(Map<String, VmCapabilities> vmsCaps) {
        this.vmsCaps = vmsCaps;
    }

    public static APIGetVmsCapabilitiesReply __example__() {
        APIGetVmsCapabilitiesReply evt = new APIGetVmsCapabilitiesReply();
        VmCapabilities vmCapabilities = new VmCapabilities();
        vmCapabilities.setSupportLiveMigration(true);
        vmCapabilities.setSupportMemorySnapshot(false);
        Map<String, VmCapabilities> vmsCpas = Maps.newHashMap();
        vmsCpas.put(uuid(), vmCapabilities);
        evt.setVmsCaps(vmsCpas);
        return evt;
    }
}
