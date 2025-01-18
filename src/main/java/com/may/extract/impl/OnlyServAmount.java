package com.may.extract.impl;

import cn.hutool.db.Entity;
import com.may.extract.AbstarctExtractStrategy;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只有信息技术服务费
 */
public class OnlyServAmount extends AbstarctExtractStrategy {

    public OnlyServAmount(String str) {
        super(str);
    }
    private static Pattern PATTERN = Pattern.compile("借款服务类型：(\\S+)合同编号：(\\d+)借款人姓名：(\\S+)身份证号：(\\d{17}[0-9|x|X])居住地址：(\\S+)联系电话：(\\S+)产品系列代码：(\\S+)产品代码：(\\S+)借款金额：(\\S+)借款期限：(\\S+)年化借款利率：(\\S+)信息技术服务费：(\\S+)年化客户服务费率：(\\S+)还款方式：(\\S+)还款日：(\\S+)到期还款金额：(\\S+)借款用途：(\\S+)还款账户：账户名：(\\S+)银行名称：(\\S+)银行账号：(\\d+)");

    private Supplier<Entity> operator = () -> {
        Matcher matcher = PATTERN.matcher(getStr());
        Entity entity = null;
        if (matcher.find()){
            entity = Entity.create(getDataBase());

            entity.set("ec_loan_type",matcher.group(1));
            entity.set("ec_num",matcher.group(2));
            entity.set("ec_loan_name",matcher.group(3));
            entity.set("ec_loan_idcard",matcher.group(4));
            entity.set("ec_loan_address",matcher.group(5));
            entity.set("ec_loan_phone",matcher.group(6));
            entity.set("ec_all_code",matcher.group(7));
            entity.set("ec_product_code",matcher.group(8));
            entity.set("ec_loan_amount",matcher.group(9));
            entity.set("ec_loan_limit",matcher.group(10));
            entity.set("ec_loan_rate1",matcher.group(11));
            entity.set("ec_serv_amount",matcher.group(12));
            entity.set("ec_loan_rate2",matcher.group(13));
            entity.set("ec_pay_type" ,matcher.group(14));
            entity.set("ec_pay_time" , matcher.group(15));
            entity.set("ec_pay_amount" , matcher.group(16));
            entity.set("ec_loan_use" , matcher.group(17));
            entity.set("ec_pay_name" , matcher.group(18));
            entity.set("ec_pay_bank" , matcher.group(19));
            entity.set("ec_pay_bank_num" , matcher.group(20));
        }
        return entity;
    };

    @Override
    public Entity doExtract() {
        return operator.get();
    }
}
