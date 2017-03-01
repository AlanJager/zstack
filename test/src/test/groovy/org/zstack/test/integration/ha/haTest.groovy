package org.zstack.test.integration.ha

import org.zstack.testlib.SpringSpec
import org.zstack.testlib.Test

/**
 * Created by zouye on 2017/3/1.
 */
class haTest extends Test{
    static SpringSpec springSpec = makeSpring {
        localStorage()
        virtualRouter()
        vyos()
        kvm()
    }

    @Override
    void setup() {
        useSpring(springSpec)
    }

    @Override
    void environment() {
    }

    @Override
    void test() {
        runSubCases([

        ])
    }
}
