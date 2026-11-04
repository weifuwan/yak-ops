package io.yak.ops.extract.alert.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertSendRequest {

    private int groupId;

    private String title;

    private String content;

    private int warnType;

}
