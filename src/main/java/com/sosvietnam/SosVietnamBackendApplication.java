package com.sosvietnam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SosVietnamBackendApplication {

    public static void main(String[] args) {
        // Giống HIV-TMSS: cố định giờ Việt Nam dù server chạy ở đâu (Render là UTC). Các cột thời gian
        // (created_at, escalate_at...) là timestamp không múi giờ, máy local và Render ghi chung một DB
        // nên phải cùng múi giờ, nếu không ca do Render tạo lệch 7 tiếng và bị leo thang ngay.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(SosVietnamBackendApplication.class, args);
    }
}
