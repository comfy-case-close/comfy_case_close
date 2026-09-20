package com.fnbx.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * api-gateway — cong 8080, cua vao duy nhat tu Internet.
 *
 * <h2>Trach nhiem</h2>
 * <ul>
 *   <li>Verify access JWTs; downstream services independently verify the forwarded bearer token</li>
 *   <li>Keep a single ingress for routing and future rate limiting</li>
 *   <li>Dinh tuyen theo tien to duong dan</li>
 *   <li>Strip untrusted identity headers; forward the original bearer token</li>
 * </ul>
 *
 * <h2>KHONG phai trach nhiem</h2>
 * <ul>
 *   <li>Nghiep vu — gateway khong duoc biet phieu ket ca la gi</li>
 *   <li>Gop du lieu tu nhieu service — do la viec cua reporting-service</li>
 *   <li>Chuyen doi du lieu — de nguyen payload di qua</li>
 * </ul>
 *
 * <p>Gateway phinh to thanh noi chua logic la cach nhanh nhat de bien no
 * thanh diem nghen va diem loi don. Giu no mong.
 *
 * <h2>⚠️ Dieu kien bat buoc ve ha tang</h2>
 * Cac service (8081-8089) <b>khong duoc mo ra Internet</b>. Neu chung mo,
 * Keep one ingress for traffic controls. Direct service requests also require
 * a cryptographically verified access token.
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
