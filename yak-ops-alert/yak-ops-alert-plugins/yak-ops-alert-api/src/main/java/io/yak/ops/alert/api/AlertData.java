package io.yak.ops.alert.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class AlertData {

    /**
     * alert id
     */
    private int id;

    /**
     * alert tile
     */
    private String title;

    /**
     * alert content
     */
    private String content;

    /**
     * alert log
     */
    private String log;

    /**
     * AlertType#code
     */
    private int alertType;

}
