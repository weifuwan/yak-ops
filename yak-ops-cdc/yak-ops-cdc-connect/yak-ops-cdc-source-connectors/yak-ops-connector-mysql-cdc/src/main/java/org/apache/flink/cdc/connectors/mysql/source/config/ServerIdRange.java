package org.apache.flink.cdc.connectors.mysql.source.config;

import javax.annotation.Nullable;

import java.io.Serializable;

import static org.apache.flink.util.Preconditions.checkArgument;

/**
 * This class defines a range of server id. The boundaries of the range are inclusive.
 *
 * @see MySqlSourceOptions#SERVER_ID
 */
public class ServerIdRange implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Start of the range (inclusive). */
    private final int startServerId;

    /** End of the range (inclusive). */
    private final int endServerId;

    public ServerIdRange(int startServerId, int endServerId) {
        this.startServerId = startServerId;
        this.endServerId = endServerId;
    }

    public int getStartServerId() {
        return startServerId;
    }

    public int getEndServerId() {
        return endServerId;
    }

    public int getServerId(int subTaskId) {
        checkArgument(subTaskId >= 0, "Subtask ID %s shouldn't be a negative number.", subTaskId);
        if (subTaskId > getNumberOfServerIds()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Subtask ID %s is out of server id range %s, "
                                    + "please adjust the server id range to "
                                    + "make the number of server id larger than "
                                    + "the source parallelism.",
                            subTaskId, this.toString()));
        }
        return startServerId + subTaskId;
    }

    public int getNumberOfServerIds() {
        return endServerId - startServerId + 1;
    }

    @Override
    public String toString() {
        if (startServerId == endServerId) {
            return String.valueOf(startServerId);
        } else {
            return startServerId + "-" + endServerId;
        }
    }

    /**
     * Returns a {@link ServerIdRange} from a server id range string which likes '5400-5408' or a
     * single server id likes '5400'.
     */
    public static @Nullable ServerIdRange from(@Nullable String range) {
        if (range == null) {
            return null;
        }
        if (range.contains("-")) {
            String[] idArray = range.split("-");
            if (idArray.length != 2) {
                throw new IllegalArgumentException(
                        String.format(
                                "The server id range should be syntax like '5400-5500', but got: %s",
                                range));
            }
            return new ServerIdRange(
                    parseServerId(idArray[0].trim()), parseServerId(idArray[1].trim()));
        } else {
            int serverId = parseServerId(range);
            return new ServerIdRange(serverId, serverId);
        }
    }

    private static int parseServerId(String serverIdValue) {
        try {
            return Integer.parseInt(serverIdValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    String.format("The server id %s is not a valid numeric.", serverIdValue), e);
        }
    }
}
