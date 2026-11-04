package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@AllArgsConstructor
@TableName("t_ds_fav_task")
public class FavTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskType;
    private int userId;

}
