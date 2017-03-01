package org.zstack.test.integration.ha.restart

import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.core.db.SimpleQuery
import org.zstack.header.storage.primary.PrimaryStorageClusterRefVO
import org.zstack.header.storage.primary.PrimaryStorageClusterRefVO_
import org.zstack.header.vm.VmInstanceSpec
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.storage.primary.local.LocalStorageHostRefVO
import org.zstack.storage.primary.local.LocalStorageHostRefVO_
import org.zstack.test.integration.ha.Env
import org.zstack.test.integration.ha.haTest
import org.zstack.testlib.ClusterSpec
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.PrimaryStorageSpec
import org.zstack.testlib.SubCase

/**
 * Created by zouye on 2017/3/1.
 */
class HARestartWhenPsDetachedCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(haTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testHARestartWhenPsDetached()
        }
    }

    private void testHARestartWhenPsDetached() {
        ClusterSpec clusterSpec = env.specByName("cluster") as ClusterSpec
        PrimaryStorageSpec primaryStorageSpec = env.specByName("local") as PrimaryStorageSpec
        VmInstanceSpec vmInstanceSpec = env.specByName("kvm") as VmInstanceSpec
        DatabaseFacade dbf = bean(DatabaseFacade.class)

        detachPrimaryStorageFromCluster {
            clusterUuid = clusterSpec.inventory.uuid
            primaryStorageUuid = primaryStorageSpec.inventory.uuid
        }

        List<LocalStorageHostRefVO> vo = Q.New(LocalStorageHostRefVO.class).eq(LocalStorageHostRefVO_.primaryStorageUuid , d.getInventory().getRootVolume().getPrimaryStorageUuid()).list()
        assert vo == null

        SimpleQuery<PrimaryStorageClusterRefVO> q = dbf.createQuery(PrimaryStorageClusterRefVO.class)
        q.add(PrimaryStorageClusterRefVO_.clusterUuid, SimpleQuery.Op.EQ, clusterSpec.inventory.uuid)
        q.add(PrimaryStorageClusterRefVO_.primaryStorageUuid, SimpleQuery.Op.EQ, primaryStorageSpec.inventory.uuid)
        List<PrimaryStorageClusterRefVO> refs = q.list()
        assert refs == null

        VmInstanceVO vmvo = dbFindByUuid(vmInstanceSpec.vmInventory.uuid, VmInstanceVO.class)
        assert vmvo.state == VmInstanceState.Stopped
    }

    @Override
    void clean() {

    }
}
