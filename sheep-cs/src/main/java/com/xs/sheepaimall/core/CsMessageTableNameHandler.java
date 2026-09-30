package com.xs.sheepaimall.core;

import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.xs.sheepaimall.entity.CsMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** cs_message 按月分表处理 */
@Slf4j
public class CsMessageTableNameHandler implements TableNameHandler {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 当前操作的月份，通过 ThreadLocal 传递（跨月消息需要指定月份） */
    private static final ThreadLocal<String> MONTH_HOLDER = new ThreadLocal<>();

    public static void setMonth(String month) {
        MONTH_HOLDER.set(month);
    }

    public static void setMonth(LocalDateTime time) {
        MONTH_HOLDER.set(time.format(YM));
    }

    public static void clearMonth() {
        MONTH_HOLDER.remove();
    }

    @Override
    public String dynamicTableName(String sql, String tableName) {
        if ("cs_message".equals(tableName)) {
            String month = MONTH_HOLDER.get();
            if (month == null) {
                month = LocalDate.now().format(YM);
            }
            return "cs_message_" + month;
        }
        return tableName;
    }
}
