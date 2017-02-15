package org.zstack.testlib

import org.zstack.sdk.NetworkServiceProviderInventory

/**
 * Created by xing5 on 2017/2/15.
 */
class NetworkServiceSpec implements Spec, HasSession {
    String provider
    List<String> types

    SpecID create(String uuid, String sessionId) {
        List<NetworkServiceProviderInventory> providers = queryNetworkServiceProvider {
            conditions = ["type=$provider".toString()]
        } as List<NetworkServiceProviderInventory>

        assert !providers.isEmpty() : "no network service provider with type[$provider]"
        def p = providers[0]

        attachNetworkServiceToL3Network {
            delegate.l3NetworkUuid = (parent as L3NetworkSpec).inventory.uuid
            delegate.sessionId = sessionId
            delegate.networkServices = [(p.uuid) : types]
        }

        return null
    }
}
