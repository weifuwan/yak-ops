package io.yak.ops.tools.datasource.mysql.v8;

import io.yak.ops.dao.mapper.UserPreferenceMapper;
import io.yak.ops.tools.datasource.YakOpsManager;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.YakOpsMysqlProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@YakOpsMysqlProfile
@YakOpsDatabaseContainer(imageName = "mysql:8.0")
class UserPreferenceIT {

    @Autowired
    private YakOpsManager manager;

    @Autowired
    private UserPreferenceMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void persistsFavoritesAndAtomicallyIncrementsUsageWithoutMixingUsers() throws Exception {
        manager.initializeOrValidate();

        Assertions.assertTrue(mapper.upsertFavorite(1, "PRODUCT_MENU", "operations", true, 1) > 0);
        Assertions.assertTrue(mapper.upsertFavorite(2, "PRODUCT_MENU", "operations", false, 0) > 0);
        Assertions.assertTrue(mapper.recordUsage(1, "DATASOURCE_CREATE_TYPE", "MYSQL") > 0);
        Assertions.assertTrue(mapper.recordUsage(1, "DATASOURCE_CREATE_TYPE", "MYSQL") > 0);
        Assertions.assertTrue(mapper.recordUsage(2, "DATASOURCE_CREATE_TYPE", "MYSQL") > 0);

        Assertions.assertEquals(2L, countFor(1, "DATASOURCE_CREATE_TYPE", "MYSQL"));
        Assertions.assertEquals(1L, countFor(2, "DATASOURCE_CREATE_TYPE", "MYSQL"));
        Assertions.assertEquals(1L, countFor(1, "PRODUCT_MENU", "operations"));
        Assertions.assertEquals(1L, countFor(2, "PRODUCT_MENU", "operations"));

        // Repeated schema initialization must preserve preferences and every other table.
        manager.initializeOrValidate();
        Assertions.assertEquals(2L, countFor(1, "DATASOURCE_CREATE_TYPE", "MYSQL"));
    }

    private long countFor(int userId, String scene, String itemKey) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT use_count FROM t_yak_ops_user_preference WHERE user_id = " + userId
                                + " AND scene = '" + scene + "' AND item_key = '" + itemKey + "'")) {
            Assertions.assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
