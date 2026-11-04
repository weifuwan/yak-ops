package io.yak.ops.dao.entity;

import io.yak.ops.spi.enums.DbType;

import java.util.Date;
import java.util.Objects;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_datasource")
public class DataSource {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private int userId;

    @TableField(exist = false)
    private String userName;

    /**
     * data source name
     */
    private String name;

    private String note;

    /**
     * data source type
     */
    private DbType type;

    private String connectionParams;

    private Date createTime;

    private Date updateTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataSource that = (DataSource) o;

        if (!Objects.equals(id, that.id)) {
            return false;
        }
        return name.equals(that.name);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + name.hashCode();
        return result;
    }
}
