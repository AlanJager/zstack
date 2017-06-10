for CASES_TO_RUN in `cat /home/case`
do
        echo $CASES_TO_RUN
        for case_name in `echo ${CASES_TO_RUN} | sed 's/,/ /g'`
        do
             case_dir=`dirname $case_name | grep "test-premium"`
             case=`basename $case_name | awk -F '.groovy' '{print $1}'`
             if [ "$case_dir" != "" ];then
                 cd /root/zstack/premium/test-premium
             else
                 cd /root/zstack/test/
             fi
             timeout 1800 mvn test -Dtest=$case -DresultDir=/home/zstack/test/zstack-integration-test-result || echo "ignore"
             echo $case_name
             pwd
        done
done
