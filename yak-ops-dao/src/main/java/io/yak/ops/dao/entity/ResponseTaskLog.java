package io.yak.ops.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * log of the logger service response
 */
@Data
@AllArgsConstructor
public class ResponseTaskLog {

    private int lineNum;
    private String message;
}
