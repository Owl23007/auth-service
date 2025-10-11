package top.contins.authservice.model.common;

import lombok.Data;

// model/SyncEvent.java
@Data
public class SyncEvent {
    private String eventType; // "create" / "update"
    private String userId;
    private String userStatus;
    private long timestamp;
    private String source = "auth_service";

    public SyncEvent(String eventType, String userId, String userStatus) {
        this.eventType = eventType;
        this.userId = userId;
        this.userStatus = userStatus;
        this.timestamp = System.currentTimeMillis();
    }
}