package org.zstack.test.integration.storage.primary.smp

import org.springframework.http.HttpEntity
import org.zstack.core.db.Q
import org.zstack.header.cluster.ClusterVO
import org.zstack.header.cluster.ClusterVO_
import org.zstack.header.host.HostVO
import org.zstack.header.host.HostVO_
import org.zstack.storage.primary.smp.KvmBackend
import org.zstack.test.integration.storage.SMPEnv
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.ClusterSpec
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.PrimaryStorageSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.ZoneSpec

/**
 * Created by AlanJager on 2017/3/6.
 */
class SMPAttachingCase extends SubCase{
    EnvSpec env

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = SMPEnv.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testAttachingSmpWithoutMountPathOnHost()
        }
    }

    void testAttachingSmpWithoutMountPathOnHost() {
        PrimaryStorageSpec primaryStorageSpec = env.specByName("smp")
        ZoneSpec zoneSpec = env.specByName("zone")
        ClusterSpec clusterSpec = env.specByName("cluster")

        detachPrimaryStorageFromCluster {
            primaryStorageUuid = primaryStorageSpec.inventory.uuid
            clusterUuid = clusterSpec.inventory.uuid
        }

        env.afterSimulator(KvmBackend.CHECK_BITS_PATH) { rsp, HttpEntity<String> e ->
            rsp as KvmBackend.AgentRsp
            rsp.success = false
            return rsp
        }

        String s = Q.New(ClusterVO.class)
                    .select(ClusterVO_.hypervisorType).eq(ClusterVO_.uuid, clusterSpec.inventory.uuid).findValue()

        assert s == "KVM"

        attachPrimaryStorageToCluster {
            primaryStorageUuid = primaryStorageSpec.inventory.uuid
            clusterUuid = clusterSpec.inventory.uuid
        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
