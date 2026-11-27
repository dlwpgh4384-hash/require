package com.company.common.notifications.mapper;

import java.util.List;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.company.common.notifications.mapper.NotificationRows.*;

@Mapper
public interface NotificationMapper {
    boolean activeAccount(@Param("actor") String actor);
    boolean eligibleAccount(@Param("actor") String actor);
    String uploadOwner(@Param("id") String id);
    Integer uploadWarnings(@Param("actor") String actor, @Param("id") String id);
    List<Notification> selectNotifications(Window window);
    Notification lockNotification(Selection selection);
    Notification selectNotification(Selection selection);
    Access selectAccess(@Param("actor") String actor, @Param("notification") Notification notification);
    int read(Selection selection);
    int readAll(Window window);
    int deleteExpired(@Param("cutoff") Instant cutoff);
    void lockDelivery(@Param("identity") String identity);
    boolean deliveredToday(Delivery delivery);
    int insert(Delivery delivery);
}
