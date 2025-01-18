package com.may.extract;

import cn.hutool.db.Entity;
import cn.hutool.setting.Setting;

/**
 * 定义抽象的提取策略，具体方法由子类实现
 */
public abstract class AbstarctExtractStrategy {

    /**
     * 公共的构造器
     * @param str
     */
    public AbstarctExtractStrategy(String str){
        this.str = str;
    }

    /**
     * 待解析字符串（去空格的字符串）
     */
    private String str;

    //读取配置文件
    private static Setting props = new Setting("props.setting");
    private static final String ENV = props.getStr("env");

    // 要插入的数据库
    private static final String DATA_BASE = "pod".equals(ENV) ? props.getByGroup("dataBase","pod"):props.getByGroup("dataBase","dev");

    public String getStr(){
        return this.str;
    }

    public static String getDataBase(){return DATA_BASE;}

    /**
     * 解析方法
     * @return 封装后需要存储的实体
     */
    public abstract Entity doExtract();

}
