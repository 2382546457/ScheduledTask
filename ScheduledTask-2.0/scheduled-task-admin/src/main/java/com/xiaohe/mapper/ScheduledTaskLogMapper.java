package com.xiaohe.mapper;

import com.xiaohe.core.model.ScheduledTaskLog;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @author : 小何
 * @Description :
 * @date : 2023-08-31 16:53
 */
public interface ScheduledTaskLogMapper {
    public long save(ScheduledTaskLog jobLog);

    public int updateTriggerInfo(ScheduledTaskLog log);

    public ScheduledTaskLog load(@Param("id") long id);

    public int updateHandleInfo(ScheduledTaskLog log);

    public List<Long> findLostJobIds(@Param("losedTime") Date losedTime);

    public List<Long> findFailJobLogIds(@Param("pagesize") int pagesize);

    public int updateAlarmStatus(@Param("logId")Long failLogId,
                                 @Param("oldAlarmStatus") int oldAlarmStatus,
                                 @Param("newAlarmStatus") int newAlarmStatus);
}
