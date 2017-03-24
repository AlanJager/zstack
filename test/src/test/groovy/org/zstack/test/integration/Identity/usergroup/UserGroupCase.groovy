package org.zstack.test.integration.Identity.usergroup

import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.identity.SessionVO
import org.zstack.header.identity.SessionVO_
import org.zstack.header.identity.UserGroupVO
import org.zstack.header.identity.UserVO
import org.zstack.sdk.AccountInventory
import org.zstack.sdk.SessionInventory
import org.zstack.sdk.UserGroupInventory
import org.zstack.sdk.UserInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

import java.util.concurrent.TimeUnit

/**
 * Created by AlanJager on 2017/3/20.
 */
class UserGroupCase extends SubCase {
    EnvSpec env
    AccountInventory accountInventory
    UserGroupInventory userGroupInventory
    UserInventory userInventory
    SessionInventory sessionInventory

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            accountInventory = createAccount {
                name = "test"
                password = "password"
            }

            testRemoveUserFromGroup()
            testDeleteUserGroup()
        }
        env.delete()
    }

    void testRemoveUserFromGroup() {
        sessionInventory = logInByAccount {
            accountName = "test"
            password = "password"
        }

        userGroupInventory = createUserGroup {
            name = "test"
            sessionId = sessionInventory.uuid
        }

        userInventory = createUser {
            name = "test"
            password = "test"
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        // add user to group
        addUserToGroup {
            groupUuid = userGroupInventory.uuid
            userUuid = userInventory.uuid
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        def userGroupVO = dbFindByUuid(userGroupInventory.uuid, UserGroupVO.class)
        assert userGroupVO.accountUuid == userGroupInventory.accountUuid

        List<String> ids = SQL.New("select ref.id from UserGroupUserRefVO ref where ref.groupUuid = :groupUuid and" +
                " ref.userUuid = :userUuid")
                .param("groupUuid", userGroupInventory.uuid)
                .param("userUuid", userInventory.uuid).list()

        assert ids

        removeUserFromGroup {
            userUuid = userInventory.uuid
            groupUuid = userGroupInventory.uuid
            sessionId = sessionInventory.uuid
        }

        ids = SQL.New("select ref.id from UserGroupUserRefVO ref where ref.groupUuid = :groupUuid and" +
                " ref.userUuid = :userUuid")
                .param("groupUuid", userGroupInventory.uuid)
                .param("userUuid", userInventory.uuid).list()

        assert ids.isEmpty()
    }


    void testDeleteUserGroup() {

        // readd user to group
        addUserToGroup {
            groupUuid = userGroupInventory.uuid
            userUuid = userInventory.uuid
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        deleteUser {
            uuid = userInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        assert !dbIsExists(userInventory.uuid, UserVO.class)
        List<String> ids = SQL.New("select ref.id from UserGroupUserRefVO ref where ref.groupUuid = :groupUuid and" +
                " ref.userUuid = :userUuid")
                .param("groupUuid", userGroupInventory.uuid)
                .param("userUuid",userInventory.uuid).list()
        assert ids.isEmpty()

        List<String> sessions = Q.New(SessionVO.class).select(SessionVO_.uuid)
                .eq(SessionVO_.userUuid, userInventory.uuid).findValue()
        assert sessions == null

        UserInventory userInventory1 = createUser {
            name = "user"
            password = "password"
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        addUserToGroup {
            userUuid = userInventory1.uuid
            groupUuid = userGroupInventory.uuid
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        deleteUserGroup {
            uuid = userGroupInventory.uuid
            sessionId = sessionInventory.uuid
        }
        TimeUnit.SECONDS.sleep(1)

        ids = SQL.New("select ref.id from UserGroupUserRefVO ref where ref.groupUuid = :groupUuid and" +
                " ref.userUuid = :userUuid")
                .param("groupUuid", userGroupInventory.uuid)
                .param("userUuid", userInventory1.uuid).list()
        assert ids.isEmpty()
    }
}
