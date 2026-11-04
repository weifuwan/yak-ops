package io.yak.ops.api.dto.treeview;

import java.util.Date;

import lombok.Data;

@Data
public class Instance {

    private Integer id;

    /**
     * node name
     */
    private String name;

    /**
     * node code
     */
    private long code;

    /**
     * node type
     */
    private String type;

    /**
     * node status
     */
    private String state;

    /**
     * node start time
     */
    private Date startTime;

    /**
     * node end time
     */
    private Date endTime;

    /**
     * node running on which host
     */
    private String host;

    /**
     * node duration
     */
    private String duration;

    private long subflowCode;

    public Instance(int id, String name, long code, String type) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
    }

    public Instance(int id, String name, long code, String type, String state, Date startTime, Date endTime,
                    String host, String duration, long subflowCode) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
        this.state = state;
        this.startTime = startTime;
        this.endTime = endTime;
        this.host = host;
        this.duration = duration;
        this.subflowCode = subflowCode;
    }

    public Instance(int id, String name, long code, String type, String state, Date startTime, Date endTime,
                    String host, String duration) {
        this(id, name, code, type, state, startTime, endTime, host, duration, 0);
    }
}
