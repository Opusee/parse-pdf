package com.may;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.setting.Setting;
import com.may.extract.AbstarctExtractStrategy;
import com.may.extract.impl.BothExist;
import com.may.extract.impl.Neither;
import com.may.extract.impl.OnlyManagerAmount;
import com.may.extract.impl.OnlyServAmount;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取指定路径下的pdf，用正则匹配获取指定内容，然后写入数据库
 */
public class ReadPDFInsertDBThread {

    static final Log log = LogFactory.get();

    // volatile 保证了内存可见性，但不保证原子性，用 AtomicInteger 原子变量可解决。这是多线程下高效且安全的方式
    static volatile AtomicInteger count = new AtomicInteger(0);// 更新成功的个数

    // 每个第三方的连接池都有对数源的实现，该方法拿到默认配置的连接池
    static DataSource DS = DSFactory.get();

    //读取配置文件
    private static final Setting props = new Setting("props.setting");
    private static final String ENV = props.getStr("env");
    private static final Integer MAX_THREAD = props.getInt("maxThread");

    //合同目录
    static final String FILE_PATH = props.getByGroup("filePath","dev");

    // 135 类型的规则
    static Pattern FOOT_PATTERN_135 = Pattern.compile("盖章）：(\\S+?)(\\d+)日期：");
    static Pattern FOOT_PATTERN = Pattern.compile("（盖章）：(\\S+?)日期：\\S+日期：(\\S+)日");

    public static void main(String[] args) {

        //待插入数据的模型
        List<Entity> dataList = new ArrayList<>();

        if (Objects.equals(ENV,"pod")){
            try {
                dataList = Db.use(DS).query("select ecld,ec_path from tmp_ecdetail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else {
            //获取目录下的pdf
            File[] files = new File(FILE_PATH).listFiles();
            for (File file : files){
                Entity entity = Entity.create();
                entity.set("ec_path",file.getPath());
                entity.set("ecld",null);
                dataList.add(entity);
            }

        }

        int len = dataList.size();
        CountDownLatch latch = new CountDownLatch(len);
        long begin = System.currentTimeMillis();

        //插入失败的合同，写入文本
        StringBuffer insertFailBuffer = new StringBuffer();

        //创建线程池
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREAD);
        for (Entity data : dataList) {

            pool.execute(() -> {
                String filePath = data.getStr("ec_path");
                String ecld = data.getStr("ecld");
                // 获取操作系统，Linux 是左斜杠
                String[] pathArray = System.getProperty("os.name").toLowerCase().startsWith("win") ? filePath.split("[.\\\\]+") : filePath.split("[/.]+");
//                Arrays.asList(pathArray).stream().forEach(System.out::println);
                String ecType = pathArray[pathArray.length-2].split("_")[0];

                log.info("合同：{} 正在解析......", filePath);

                String pdfStr = "";
                //解析指定的pdf
                PdfDocument pdf = new PdfDocument();

                try {
                    pdf.loadFromFile(filePath);
                    //取第一页数据
                    // pdf 页 （com.spire.pdf 免费版最多解析10页）
                    PdfPageBase page = pdf.getPages().get(0);
                    //是否保留页面文本与页脚之间的空格。true:保留，原样输出。
                    String content;
                    synchronized (ReadPDFInsertDBThread.class){
                        content = page.extractText(false);
                    }
                    //替换文本中所有的空格，变成一行字符串
                    pdfStr = content.replaceAll("\\s+", "");

                } catch (Exception e) {
                    log.info("----合同：{} 解析出错---",filePath);
                }finally {
                    pdf.close();
                }

                boolean existManagerAmount = false;
                boolean existServAmount = false;
                if (pdfStr.contains("账户管理费")){
                    existManagerAmount = true;
                }
                if (pdfStr.contains("信息技术服务费")){
                    existServAmount = true;
                }
//                System.out.println(String.format("账户管理费：%s\t信息技术服务费：%s",existManagerAmount,existServAmount));

                AbstarctExtractStrategy extractStrategy = null;
                //只有信息技术服务费
                if (!existManagerAmount && existServAmount){
                    extractStrategy = new OnlyServAmount(pdfStr);
                }

                //只有账户管理费
                if (existManagerAmount && !existServAmount){
                    extractStrategy = new OnlyManagerAmount(pdfStr);
                }

                //两者都有
                if (existManagerAmount && existServAmount){
                    extractStrategy = new BothExist(pdfStr);
                }

                //两者都没有
                if (!existManagerAmount && !existServAmount){
                    extractStrategy = new Neither(pdfStr);
                }

                //策略提取部分
                Entity entity = extractStrategy.doExtract();

                if (Objects.equals("135",ecType)){
                    Matcher footMatcher = FOOT_PATTERN_135.matcher(pdfStr);
                    if (footMatcher.find()){
                        entity.set("ec_sign_unit",footMatcher.group(1));
                        String str = footMatcher.group(2);
                        String ecSignTime = str.substring(0, str.length() / 2).replaceAll("^(.{4})(.{2})(.{2})$","$1-$2-$3");//解析出为202000220，替换成2020-02-20
                        entity.set("ec_sign_time",ecSignTime);
                    }
                }else {
                    Matcher footMatcher = FOOT_PATTERN.matcher(pdfStr);
                    if (footMatcher.find()){
                        entity.set("ec_sign_unit",footMatcher.group(1));
                        entity.set("ec_sign_time",footMatcher.group(2).replaceAll("[_|\\s]+","").replaceAll("[年|月]","-"));
                    }
                }
                entity.set("from_ecld",ecld);
                //指定 druid 数据源
                Session session = Session.create(DS);
                try {
                    session.beginTransaction();
                    session.insert(entity);
                    session.commit();
                    log.info("合同：{} 插入成功------------- 计数：{}/{}", filePath, count.incrementAndGet(), len);// 并发下的 ++i 操作
                } catch (SQLException e) {
                    log.info(e, "合同：{} 插入数据库出错！！！",filePath);
                    insertFailBuffer.append(filePath+"\n");
                    session.quietRollback();
                }

                latch.countDown();//无论是否成功，计数减一
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        pool.shutdown();

        long end = System.currentTimeMillis();

        if (insertFailBuffer.length() > 0){
            try {
//            FileWriter insertFailText = new FileWriter("insertFailText.txt",true);//追加的方式写入
                FileWriter insertFailText = new FileWriter("insertFailText.txt");
                insertFailText.write(insertFailBuffer.toString());
                insertFailText.flush();
                insertFailText.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String disc = new SimpleDateFormat("mm 分 ss 秒").format(end - begin);
        log.info("------合同个数：{}，更新成功个数：{}，更新失败个数：{},耗时：{} ------",len, count.intValue(),len-count.intValue(),disc);

    }
}
